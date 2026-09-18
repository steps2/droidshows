package nl.asymmetrics.droidshows.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.util.LruCache;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.tabs.TabLayout;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import nl.asymmetrics.droidshows.DroidShows;
import nl.asymmetrics.droidshows.R;
import nl.asymmetrics.droidshows.ThemeHelper;
import nl.asymmetrics.droidshows.provider.JsonFetcher;
import nl.asymmetrics.droidshows.provider.TMDB;
import nl.asymmetrics.droidshows.provider.TVMaze;
import nl.asymmetrics.droidshows.thetvdb.model.Serie;
import nl.asymmetrics.droidshows.utils.SQLiteStore;

/** Discover: an endless list of shows (popular / top-rated / on-the-air mix)
 *  and trending movies. TV rows appear instantly from TMDB; their TVMaze ids
 *  resolve in the background so the check marks catch up. Tap the check to
 *  add an item to the library; long-press for the Add menu. */
public class DiscoverActivity extends AppCompatActivity {

	private ListView listView;
	private TextView emptyView;
	private LinearProgressIndicator topProgress;
	private DiscoverAdapter adapter;
	/** Appended from loader threads while the UI thread reads: keep them thread-safe. */
	private final List<Serie> shows = Collections.synchronizedList(new ArrayList<Serie>());
	private final List<Serie> movies = Collections.synchronizedList(new ArrayList<Serie>());
	private final Set<String> inLibrary = Collections.synchronizedSet(new HashSet<String>());
	/** ids with an add already running; a second tap is ignored until it finishes */
	private final Set<String> addingNow = Collections.synchronizedSet(new HashSet<String>());
	/** bumps on every first-page load: a stale/rotated generation must not touch the new lists */
	private final AtomicInteger loadGen = new AtomicInteger();
	/** bumps on every library-id scan: a stale scan must not overwrite a newer one */
	private final AtomicInteger libraryGen = new AtomicInteger();
	/** one small pool for all poster loads instead of a raw thread per row bind */
	private static final ExecutorService POSTER_POOL = Executors.newFixedThreadPool(4);
	/** in-memory poster cache so rebinds don't blank and re-decode from disk */
	private static final LruCache<String, Bitmap> POSTER_CACHE = new LruCache<String, Bitmap>(48);
	/** tmdbId -> tvmazeId, filled in by the background resolver */
	private final Map<String, String> tvResolved =
		Collections.synchronizedMap(new HashMap<String, String>());
	private final Set<String> seenTvTmdb = new HashSet<String>();
	private final List<Serie> resolveQueue = new ArrayList<Serie>();
	private boolean resolverRunning = false;
	private int tab = 0;
	private int moviePage = 0;
	private int tvPage = 0;
	private boolean movieMore = true;
	private boolean tvMore = true;
	private boolean loadingMore = false;
	private String movieProblem = null;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		ThemeHelper.applyTheme(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.discover);

		MaterialToolbar toolbar = (MaterialToolbar) findViewById(R.id.toolbar);
		toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			public void onClick(View v) { finish(); }
		});

		TabLayout tabs = (TabLayout) findViewById(R.id.discover_tabs);
		tabs.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
			public void onTabSelected(TabLayout.Tab t) {
				tab = t.getPosition();
				refreshList();
				loadTab();
			}
			public void onTabUnselected(TabLayout.Tab t) {}
			public void onTabReselected(TabLayout.Tab t) {}
		});

		topProgress = (LinearProgressIndicator) findViewById(R.id.discover_progress);
		listView = (ListView) findViewById(android.R.id.list);
		emptyView = (TextView) findViewById(android.R.id.empty);
		listView.setEmptyView(emptyView);
		adapter = new DiscoverAdapter();
		listView.setAdapter(adapter);
		listView.setOnScrollListener(new AbsListView.OnScrollListener() {
			public void onScrollStateChanged(AbsListView view, int scrollState) {}
			public void onScroll(AbsListView view, int firstVisible, int visibleCount, int totalCount) {
				if (loadingMore || totalCount == 0) return;
				boolean more = tab == 0 ? tvMore : movieMore;
				if (more && firstVisible + visibleCount >= totalCount - 4) loadMore();
			}
		});
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				showDetails(adapter.getItem(position));
			}
		});
		listView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
			public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
				final Serie item = adapter.getItem(position);
				new MaterialAlertDialogBuilder(DiscoverActivity.this)
					.setItems(new CharSequence[]{ getString(R.string.discover_add) },
						new android.content.DialogInterface.OnClickListener() {
							public void onClick(android.content.DialogInterface dialog, int which) {
								addItem(item);
							}
						})
					.show();
				return true;
			}
		});

		refreshList();
		loadTab();
	}

	private void refreshList() {
		adapter.setItems(tab == 0 ? shows : movies);
	}

	/** Tap a row: details dialog with the synopsis and an Add button.
	 *  (Press-and-hold keeps the Add menu too.) */
	private void showDetails(final Serie item) {
		if (item == null) return;
		View v = LayoutInflater.from(this).inflate(R.layout.dialog_discover_details, null);
		ImageView poster = (ImageView) v.findViewById(R.id.details_poster);
		((TextView) v.findViewById(R.id.details_name)).setText(item.getSerieName());
		((TextView) v.findViewById(R.id.details_meta)).setText(metaLine(item));
		String syn = item.getOverview();
		if (syn == null || syn.trim().isEmpty()) syn = getString(R.string.discover_no_synopsis);
		((TextView) v.findViewById(R.id.details_synopsis)).setText(syn.trim());
		loadPosterInto(poster, item);
		new MaterialAlertDialogBuilder(this)
			.setView(v)
			.setPositiveButton(R.string.discover_add,
				new android.content.DialogInterface.OnClickListener() {
					public void onClick(android.content.DialogInterface dialog, int which) {
						addItem(item);
					}
				})
			.setNegativeButton(android.R.string.cancel, null)
			.show();
	}

	private static String metaLine(Serie o) {
		StringBuilder sb = new StringBuilder();
		if (o.getMediaType() == 1) {
			sb.append(yearOf(o.getFirstAired()));
		} else {
			if (o.getNetwork() != null && !o.getNetwork().isEmpty()) sb.append(o.getNetwork());
			String y = yearOf(o.getFirstAired());
			if (!y.isEmpty()) {
				if (sb.length() > 0) sb.append(" · ");
				sb.append(y);
			}
		}
		return sb.toString();
	}

	private static String yearOf(String date) {
		return (date != null && date.length() >= 4) ? date.substring(0, 4) : "";
	}

	/** false once the activity is finishing/destroyed (rotation): bg posts must drop then */
	private boolean isAlive() { return !(isFinishing() || isDestroyed()); }

	private void showTop(final boolean show) {
		runOnUiThread(new Runnable() { public void run() {
			if (topProgress == null) return;
			topProgress.setIndeterminate(true);
			topProgress.setVisibility(show ? View.VISIBLE : View.GONE);
		}});
	}

	/** First load of the current tab (skipped when the tab already has pages). */
	private void loadTab() {
		if ((tab == 0 && tvPage > 0) || (tab == 1 && moviePage > 0)) {
			refreshList();
			return;
		}
		emptyView.setText(R.string.discover_loading);
		showTop(true);
		final int want = tab;
		final int gen = loadGen.incrementAndGet();
		new Thread(new Runnable() {
			public void run() {
				refreshLibraryIds();
				final boolean more;
				if (want == 0) {
					more = loadTvPage(1);
				} else {
					more = loadMoviePage(1);
				}
				final String problem = movieProblem;
				runOnUiThread(new Runnable() { public void run() {
					if (gen != loadGen.get() || !isAlive()) return;	// stale or rotated: drop
					if (want != tab) return;
					if (want == 0) tvMore = more; else movieMore = more;
					emptyView.setText(R.string.discover_empty);
					refreshList();
					showTop(false);
					if ("nokey".equals(problem))
						Toast.makeText(DiscoverActivity.this, R.string.discover_no_key, Toast.LENGTH_LONG).show();
					else if ((want == 0 ? shows : movies).isEmpty())
						Toast.makeText(DiscoverActivity.this, R.string.messages_thetvdb_con_error, Toast.LENGTH_LONG).show();
				}});
			}
		}).start();
	}

	/** Endless scroll: append the next page of the current tab. */
	private void loadMore() {
		if (loadingMore) return;
		loadingMore = true;
		showTop(true);
		final int want = tab;
		new Thread(new Runnable() {
			public void run() {
				final boolean more = (want == 0) ? loadTvPage(tvPage + 1) : loadMoviePage(moviePage + 1);
				runOnUiThread(new Runnable() { public void run() {
					loadingMore = false;
					if (!isAlive() || want != tab) return;	// stale or rotated: drop
					if (want == 0) tvMore = more; else movieMore = more;
					refreshList();
					showTop(false);
				}});
			}
		}).start();
	}

	/** TV page: TMDB mix (popular / top-rated / on-the-air) shown instantly;
	 *  TVMaze ids resolve in the background. Returns true if more pages exist. */
	private boolean loadTvPage(int page) {
		String key = getSharedPreferences("DroidShowsPref", 0)
			.getString(DroidShows.TMDB_API_KEY_NAME, "");
		if (key == null || key.isEmpty()) {
			if (page == 1) {
				try {
					List<Serie> sched = new TVMaze().getScheduleShows();
					if (sched != null && !sched.isEmpty()) { shows.addAll(sched); tvPage = 1; }
				} catch (JsonFetcher.RateLimitException e) { /* leave empty */ }
			}
			return false;
		}
		TMDB tmdb = new TMDB(key);
		List<Serie> popular = tmdb.getTVList("popular", page);
		int p1 = tmdb.totalPages;
		List<Serie> topRated = tmdb.getTVList("top_rated", page);
		int p2 = tmdb.totalPages;
		List<Serie> onAir = tmdb.getTVList("on_the_air", page);
		int p3 = tmdb.totalPages;
		List<Serie> fresh = new ArrayList<Serie>();
		for (int i = 0; i < 10; i++) {
			addCandidate(fresh, popular, i);
			addCandidate(fresh, topRated, i);
			addCandidate(fresh, onAir, i);
		}
		if (!fresh.isEmpty()) {
			shows.addAll(fresh);
			tvPage = page;
			enqueueResolve(fresh);
		}
		return page < p1 || page < p2 || page < p3;
	}

	private void addCandidate(List<Serie> out, List<Serie> src, int i) {
		if (src == null || i >= src.size()) return;
		Serie s = src.get(i);
		if (s.getId() == null || !seenTvTmdb.add(s.getId())) return;
		out.add(s);
	}

	/** Movie page: TMDB trending. Returns true if more pages exist. */
	private boolean loadMoviePage(int page) {
		movieProblem = null;
		String key = getSharedPreferences("DroidShowsPref", 0)
			.getString(DroidShows.TMDB_API_KEY_NAME, "");
		if (key == null || key.isEmpty()) {
			movieProblem = "nokey";
			return false;
		}
		TMDB tmdb = new TMDB(key);
		List<Serie> data = tmdb.getTrendingMovies(page);
		if (data == null) return page > 1 && movieMore;
		if (!data.isEmpty()) {
			movies.addAll(data);
			moviePage = page;
		}
		return page < tmdb.totalPages;
	}

	/** Background TVMaze id resolution so check marks catch up after the
	 *  fast TMDB rows are already on screen. */
	private void enqueueResolve(List<Serie> rows) {
		synchronized (resolveQueue) {
			for (Serie s : rows) {
				if (tvResolved.containsKey(s.getId()) || resolveQueue.contains(s)) continue;
				resolveQueue.add(s);
			}
			if (!resolverRunning) {
				resolverRunning = true;
				new Thread(resolveRunnable).start();
			}
		}
	}

	private final Runnable resolveRunnable = new Runnable() {
		public void run() {
			TVMaze tvMaze = new TVMaze();
			for (;;) {
				Serie s;
				synchronized (resolveQueue) {
					if (resolveQueue.isEmpty()) {
						resolverRunning = false;
						return;
					}
					s = resolveQueue.remove(0);
				}
				try {
					List<Serie> hits = tvMaze.searchShows(s.getSerieName());
					if (hits != null && !hits.isEmpty() && hits.get(0).getTvmazeId() != null) {
						tvResolved.put(s.getId(), hits.get(0).getTvmazeId());
						runOnUiThread(new Runnable() { public void run() {
							if (isAlive()) adapter.notifyDataSetChanged();
						}});
					}
				} catch (JsonFetcher.RateLimitException e) {
					synchronized (resolveQueue) { resolveQueue.add(0, s); }
					try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
				} catch (Exception ignored) {}
			}
		}
	};

	private void refreshLibraryIds() {
		final int gen = libraryGen.incrementAndGet();
		Set<String> fresh = new HashSet<String>();
		try {
			android.database.Cursor c = DroidShows.db.Query("SELECT id, tvmazeId, mediaType FROM series");
			if (c != null) {
				while (c.moveToNext()) {
					String id = c.getString(0);
					String tvmazeId = c.getString(1);
					boolean movie = c.getInt(2) == 1;
					if (movie) fresh.add("m:" + id);
					else {
						fresh.add("t:" + id);
						if (tvmazeId != null && !tvmazeId.isEmpty()) fresh.add("t:" + tvmazeId);
					}
				}
				c.close();
			}
		} catch (Exception e) {
			Log.e(SQLiteStore.TAG, "Discover: could not read library ids", e);
		}
		// only the newest scan wins: an older one interleaved by a fast tab-switch is dropped
		synchronized (inLibrary) {
			if (libraryGen.get() == gen) {
				inLibrary.clear();
				inLibrary.addAll(fresh);
			}
		}
	}

	private String libKeyOf(Serie s) {
		if (s.getMediaType() == 1) return "m:" + s.getId();
		String resolved = tvResolved.get(s.getId());
		if (resolved != null) return "t:" + resolved;
		String tid = s.getTvmazeId();
		if (tid != null && !tid.isEmpty()) return "t:" + tid;
		return "t:pending:" + s.getId();
	}

	/** Add the item to the library (full details + poster), like AddSerie/AddMovie do. */
	private void addItem(final Serie item) {
		final String key = libKeyOf(item);
		if (inLibrary.contains(key)) {
			Toast.makeText(this, R.string.discover_already, Toast.LENGTH_SHORT).show();
			return;
		}
		synchronized (addingNow) {
			if (!addingNow.add(key)) return;	// add already in flight: ignore the double-tap
		}
		showTop(true);
		new Thread(new Runnable() {
			public void run() {
				try {
				final boolean movie = item.getMediaType() == 1;
				String msg;
				boolean ok = false;
				try {
					Serie full;
					if (movie) {
						String key = getSharedPreferences("DroidShowsPref", 0)
							.getString(DroidShows.TMDB_API_KEY_NAME, "");
						if (key == null || key.isEmpty()) {
							msg = getString(R.string.discover_no_key);
							postAddResult(msg, false, item);
							return;
						}
						full = new TMDB(key).getMovie(item.getId());
						if (full == null) {
							msg = getString(R.string.messages_tmdb_con_error);
						} else {
							cachePoster(full);
							full.setPassiveStatus(DroidShows.showArchive == 1 ? 1 : 0);
							full.saveToDB(DroidShows.db);
							msg = String.format(getString(R.string.messages_movie_success), full.getSerieName());
							ok = true;
						}
					} else {
						String tvmazeId = tvResolved.get(item.getId());
						if ((tvmazeId == null || tvmazeId.isEmpty())
								&& item.getTvmazeId() != null && !item.getTvmazeId().isEmpty())
							tvmazeId = item.getTvmazeId();
						if (tvmazeId == null || tvmazeId.isEmpty())
							tvmazeId = resolveOnDemand(item);
						if (tvmazeId == null || tvmazeId.isEmpty()) {
							msg = getString(R.string.messages_thetvdb_con_error);
						} else {
							full = getShowWithBackoff(tvmazeId);
							if (full == null) {
								msg = getString(R.string.messages_thetvdb_con_error);
							} else {
								cachePoster(full);
								full.setPassiveStatus(DroidShows.showArchive == 1 ? 1 : 0);
								full.saveToDB(DroidShows.db);
								msg = String.format(getString(R.string.messages_series_success), full.getSerieName());
								ok = true;
							}
						}
					}
				} catch (Exception e) {
					Log.e(SQLiteStore.TAG, "Discover: add failed", e);
					msg = getString(R.string.messages_thetvdb_con_error);
				}
				postAddResult(msg, ok, item);
				} finally {
					addingNow.remove(key);
				}
			}
		}).start();
	}

	/** Resolve a show's TVMaze id right now (user tapped faster than the background resolver). */
	private String resolveOnDemand(Serie item) {
		try {
			List<Serie> hits = new TVMaze().searchShows(item.getSerieName());
			if (hits != null && !hits.isEmpty() && hits.get(0).getTvmazeId() != null) {
				tvResolved.put(item.getId(), hits.get(0).getTvmazeId());
				return hits.get(0).getTvmazeId();
			}
		} catch (JsonFetcher.RateLimitException e) {
			try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
			try {
				List<Serie> hits = new TVMaze().searchShows(item.getSerieName());
				if (hits != null && !hits.isEmpty()) return hits.get(0).getTvmazeId();
			} catch (Exception ignored) {}
		} catch (Exception ignored) {}
		return null;
	}

	private Serie getShowWithBackoff(String tvmazeId) {
		try {
			return new TVMaze().getShow(tvmazeId);
		} catch (JsonFetcher.RateLimitException e) {
			try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
			try { return new TVMaze().getShow(tvmazeId); }
			catch (Exception ignored) { return null; }
		} catch (Exception ignored) {
			return null;
		}
	}

	private void postAddResult(final String msg, final boolean ok, final Serie item) {
		if (ok) inLibrary.add(libKeyOf(item));
		runOnUiThread(new Runnable() { public void run() {
			if (!isAlive()) return;
			// don't hide the app-wide bar while a load-more page fetch is still running
			if (!loadingMore) showTop(false);
			adapter.notifyDataSetChanged();
			Toast.makeText(DiscoverActivity.this, msg, Toast.LENGTH_LONG).show();
		}});
	}

	private void cachePoster(Serie s) {
		String poster = s.getPoster();
		if (poster == null || poster.isEmpty()) return;
		try {
			URL url = new URL(poster);
			File f = nl.asymmetrics.droidshows.utils.Utils.discoverPosterFile(this, url);
			if (!f.exists()) {
				f.getParentFile().mkdirs();
				nl.asymmetrics.droidshows.utils.Utils.downloadPosterThumb(this, url, f);
			}
			s.setPosterInCache("true");
			s.setPosterThumb(f.getAbsolutePath());
		} catch (Exception e) {
			Log.e(SQLiteStore.TAG, "Discover: poster download failed", e);
		}
	}

	private void loadPosterInto(final ImageView iv, final Serie s) {
		final String url = s.getPoster();
		iv.setTag(url);
		if (url == null || url.isEmpty()) {
			iv.setImageDrawable(null);
			return;
		}
		Bitmap cached = POSTER_CACHE.get(url);
		if (cached != null) {
			iv.setImageBitmap(cached);
			return;
		}
		iv.setImageDrawable(null);
		POSTER_POOL.execute(new Runnable() {
			public void run() {
				Bitmap bmp = null;
				try {
					URL u = new URL(url);
					File f = nl.asymmetrics.droidshows.utils.Utils.discoverPosterFile(DiscoverActivity.this, u);
					if (!f.exists()) {
						f.getParentFile().mkdirs();
						nl.asymmetrics.droidshows.utils.Utils.downloadPosterThumb(DiscoverActivity.this, u, f);
					}
					bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
				} catch (Exception ignored) {}
				if (bmp != null) POSTER_CACHE.put(url, bmp);
				final Bitmap b = bmp;
				runOnUiThread(new Runnable() { public void run() {
					// only apply when the view still wants this url (it may have been recycled)
					if (b != null && url.equals(iv.getTag())) iv.setImageBitmap(b);
				}});
			}
		});
	}

	private class DiscoverAdapter extends BaseAdapter {
		/** private snapshot, touched on the UI thread only: binds never race bg appends */
		private List<Serie> items = new ArrayList<Serie>();

		void setItems(List<Serie> items) {
			synchronized (items) {
				this.items = new ArrayList<Serie>(items);
			}
			notifyDataSetChanged();
		}

		public int getCount() { return items.size(); }
		public Serie getItem(int position) { return items.get(position); }
		public long getItemId(int position) { return position; }

		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				LayoutInflater vi = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.row_discover, parent, false);
			}
			final Serie o = items.get(position);
			TextView name = (TextView) v.findViewById(R.id.discover_name);
			TextView meta = (TextView) v.findViewById(R.id.discover_meta);
			ImageView poster = (ImageView) v.findViewById(R.id.discover_poster);
			CheckBox check = (CheckBox) v.findViewById(R.id.discover_check);
			if (o != null) {
				name.setText(o.getSerieName());
				meta.setText(metaLine(o));
				loadPosterInto(poster, o);
				check.setOnCheckedChangeListener(null);
				check.setChecked(inLibrary.contains(libKeyOf(o)));
				check.setOnClickListener(new View.OnClickListener() {
					public void onClick(View btn) {
						CheckBox cb = (CheckBox) btn;
						if (inLibrary.contains(libKeyOf(o))) {
							cb.setChecked(true);
							Toast.makeText(DiscoverActivity.this, R.string.discover_already, Toast.LENGTH_SHORT).show();
						} else {
							cb.setChecked(false);
							addItem(o);
						}
					}
				});
			}
			return v;
		}
	}
}
