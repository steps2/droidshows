package nl.asymmetrics.droidshows.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import nl.asymmetrics.droidshows.DroidShows;
import nl.asymmetrics.droidshows.R;
import nl.asymmetrics.droidshows.ThemeHelper;
import nl.asymmetrics.droidshows.provider.JsonFetcher;
import nl.asymmetrics.droidshows.provider.TMDB;
import nl.asymmetrics.droidshows.provider.TVMaze;
import nl.asymmetrics.droidshows.thetvdb.model.Serie;
import nl.asymmetrics.droidshows.utils.SQLiteStore;

/** Discover: shows airing today (TVMaze) and the week's trending movies (TMDB).
 *  Tap the check to add an item to the library; long-press for the Add menu. */
public class DiscoverActivity extends AppCompatActivity {

	private ListView listView;
	private LinearProgressIndicator topProgress;
	private DiscoverAdapter adapter;
	private final List<Serie> shows = new ArrayList<Serie>();
	private final List<Serie> movies = new ArrayList<Serie>();
	private final Set<String> inLibrary = new HashSet<String>();
	private int tab = 0;

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

		topProgress = (LinearProgressIndicator) findViewById(R.id.top_progress);
		listView = (ListView) findViewById(android.R.id.list);
		listView.setEmptyView(findViewById(android.R.id.empty));
		adapter = new DiscoverAdapter();
		listView.setAdapter(adapter);
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

	private void showTop(final boolean show) {
		runOnUiThread(new Runnable() { public void run() {
			if (topProgress == null) return;
			topProgress.setIndeterminate(true);
			topProgress.setVisibility(show ? View.VISIBLE : View.GONE);
		}});
	}

	/** (Re)load the current tab's feed on a background thread. */
	private void loadTab() {
		showTop(true);
		final int want = tab;
		new Thread(new Runnable() {
			public void run() {
				refreshLibraryIds();
				final List<Serie> data;
				String problem = null;
				if (want == 0) {
					data = loadMixedShows();
				} else {
					String key = getSharedPreferences("DroidShowsPref", 0)
						.getString(DroidShows.TMDB_API_KEY_NAME, "");
					if (key == null || key.isEmpty()) {
						data = null;
						problem = "nokey";
					} else {
						data = new TMDB(key).getTrendingMovies();
					}
				}
				final List<Serie> result = data;
				final String err = problem;
				runOnUiThread(new Runnable() { public void run() {
					if (want != tab) return;
					List<Serie> target = want == 0 ? shows : movies;
					target.clear();
					if (result != null) target.addAll(result);
					refreshList();
					showTop(false);
					if ("nokey".equals(err))
						Toast.makeText(DiscoverActivity.this, R.string.discover_no_key, Toast.LENGTH_LONG).show();
					else if (result == null)
						Toast.makeText(DiscoverActivity.this, R.string.messages_thetvdb_con_error, Toast.LENGTH_LONG).show();
				}});
			}
		}).start();
	}

	/** TV Discover feed: a mix of popular, top-rated and currently-airing shows
	 *  (via TMDB), each mapped to TVMaze so it can be added like any show.
	 *  Without a TMDB key it falls back to the TVMaze airing-today schedule. */
	private List<Serie> loadMixedShows() {
		String key = getSharedPreferences("DroidShowsPref", 0)
			.getString(DroidShows.TMDB_API_KEY_NAME, "");
		TVMaze tvMaze = new TVMaze();
		if (key == null || key.isEmpty()) {
			try { return tvMaze.getScheduleShows(); }
			catch (JsonFetcher.RateLimitException e) { return null; }
		}
		TMDB tmdb = new TMDB(key);
		List<Serie> popular = tmdb.getTVList("popular");
		List<Serie> topRated = tmdb.getTVList("top_rated");
		List<Serie> onAir = tmdb.getTVList("on_the_air");
		if (popular == null && topRated == null && onAir == null) return null;
		// round-robin interleave so the three sources are genuinely mixed
		List<Serie> candidates = new ArrayList<Serie>();
		Set<String> seenTmdb = new HashSet<String>();
		for (int i = 0; i < 8; i++) {
			addCandidate(candidates, seenTmdb, popular, i);
			addCandidate(candidates, seenTmdb, topRated, i);
			addCandidate(candidates, seenTmdb, onAir, i);
		}
		List<Serie> mixed = new ArrayList<Serie>();
		Set<String> seenTvmaze = new HashSet<String>();
		for (Serie c : candidates) {
			if (mixed.size() >= 20) break;
			try {
				List<Serie> hits = tvMaze.searchShows(c.getSerieName());
				if (hits == null || hits.isEmpty()) continue;
				Serie show = hits.get(0);
				if (show.getTvmazeId() == null || !seenTvmaze.add(show.getTvmazeId())) continue;
				mixed.add(show);
			} catch (JsonFetcher.RateLimitException e) {
				break; // rate-limited: show what we have so far
			}
		}
		return mixed;
	}

	private void addCandidate(List<Serie> out, Set<String> seen, List<Serie> src, int i) {
		if (src == null || i >= src.size()) return;
		Serie s = src.get(i);
		if (s.getId() == null || !seen.add(s.getId())) return;
		out.add(s);
	}

	private void refreshLibraryIds() {		inLibrary.clear();
		try {
			android.database.Cursor c = DroidShows.db.Query("SELECT id, tvmazeId, mediaType FROM series");
			if (c != null) {
				while (c.moveToNext()) {
					String id = c.getString(0);
					String tvmazeId = c.getString(1);
					boolean movie = c.getInt(2) == 1;
					if (movie) inLibrary.add("m:" + id);
					else {
						inLibrary.add("t:" + id);
						if (tvmazeId != null && !tvmazeId.isEmpty()) inLibrary.add("t:" + tvmazeId);
					}
				}
				c.close();
			}
		} catch (Exception e) {
			Log.e(SQLiteStore.TAG, "Discover: could not read library ids", e);
		}
	}

	private static String keyOf(Serie s) {
		return (s.getMediaType() == 1 ? "m:" : "t:") + s.getId();
	}

	/** Add the item to the library (full details + poster), like AddSerie/AddMovie do. */
	private void addItem(final Serie item) {
		if (inLibrary.contains(keyOf(item))) {
			Toast.makeText(this, R.string.discover_already, Toast.LENGTH_SHORT).show();
			return;
		}
		showTop(true);
		new Thread(new Runnable() {
			public void run() {
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
						full = null;
						try {
							full = new TVMaze().getShow(item.getTvmazeId());
						} catch (JsonFetcher.RateLimitException e) {
							try { Thread.sleep(2000); } catch (InterruptedException ignored) {}
							full = new TVMaze().getShow(item.getTvmazeId());
						}
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
				} catch (Exception e) {
					Log.e(SQLiteStore.TAG, "Discover: add failed", e);
					msg = getString(R.string.messages_thetvdb_con_error);
				}
				postAddResult(msg, ok, item);
			}
		}).start();
	}

	private void postAddResult(final String msg, final boolean ok, final Serie item) {
		if (ok) inLibrary.add(keyOf(item));
		runOnUiThread(new Runnable() { public void run() {
			showTop(false);
			adapter.notifyDataSetChanged();
			Toast.makeText(DiscoverActivity.this, msg, Toast.LENGTH_LONG).show();
		}});
	}

	private void cachePoster(Serie s) {
		String poster = s.getPoster();
		if (poster == null || poster.isEmpty()) return;
		try {
			URL url = new URL(poster);
			File f = new File(getFilesDir().getAbsolutePath() + "/thumbs" + url.getFile());
			if (!f.exists()) {
				f.getParentFile().mkdirs();
				org.apache.commons.io.FileUtils.copyURLToFile(url, f);
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
		iv.setImageDrawable(null);
		if (url == null || url.isEmpty()) return;
		new Thread(new Runnable() {
			public void run() {
				Bitmap bmp = null;
				try {
					URL u = new URL(url);
					File f = new File(getFilesDir().getAbsolutePath() + "/thumbs" + u.getFile());
					if (!f.exists()) {
						f.getParentFile().mkdirs();
						org.apache.commons.io.FileUtils.copyURLToFile(u, f);
					}
					bmp = BitmapFactory.decodeFile(f.getAbsolutePath());
				} catch (Exception ignored) {}
				final Bitmap b = bmp;
				runOnUiThread(new Runnable() { public void run() {
					if (url.equals(iv.getTag()) && b != null) iv.setImageBitmap(b);
				}});
			}
		}).start();
	}

	private class DiscoverAdapter extends BaseAdapter {
		private List<Serie> items = new ArrayList<Serie>();

		void setItems(List<Serie> items) {
			this.items = items;
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
				final boolean inLib = inLibrary.contains(keyOf(o));
				check.setOnCheckedChangeListener(null);
				check.setChecked(inLib);
				check.setOnClickListener(new View.OnClickListener() {
					public void onClick(View btn) {
						CheckBox cb = (CheckBox) btn;
						if (inLibrary.contains(keyOf(o))) {
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

		private String metaLine(Serie o) {
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

		private String yearOf(String date) {
			return (date != null && date.length() >= 4) ? date.substring(0, 4) : "";
		}
	}
}
