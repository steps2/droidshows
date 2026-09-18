package nl.asymmetrics.droidshows.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import nl.asymmetrics.droidshows.DroidShows;
import nl.asymmetrics.droidshows.R;
import nl.asymmetrics.droidshows.provider.JsonFetcher;
import nl.asymmetrics.droidshows.provider.TVMaze;
import nl.asymmetrics.droidshows.thetvdb.model.Serie;
import nl.asymmetrics.droidshows.thetvdb.model.TVShowItem;
import nl.asymmetrics.droidshows.utils.SQLiteStore;
import nl.asymmetrics.droidshows.utils.SwipeDetect;
import nl.asymmetrics.droidshows.utils.Utils;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.app.AppCompatActivity;
import android.app.SearchManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import androidx.appcompat.content.res.AppCompatResources;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.AdapterContextMenuInfo;


public class AddSerie extends AppCompatActivity
{
	private static List<Serie> search_series = null;
	private TVMaze tvMaze;
	private SeriesSearchAdapter seriessearch_adapter;
	/* Non-intrusive progress: a thin bar at the top of the list; the screen stays usable. */
	private void showProgress(int titleRes, int msgRes, boolean cancelable) {
		runOnUiThread(new Runnable() { public void run() {
			View bar = findViewById(R.id.add_progress);
			if (bar instanceof com.google.android.material.progressindicator.LinearProgressIndicator) {
				((com.google.android.material.progressindicator.LinearProgressIndicator) bar).setIndeterminate(true);
				bar.setVisibility(View.VISIBLE);
			}
		}});
	}

	private void dismissProgress() {
		runOnUiThread(new Runnable() { public void run() {
			View bar = findViewById(R.id.add_progress);
			if (bar != null) bar.setVisibility(View.GONE);
		}});
	}
	/* Option Menus */
	private static final int ADD_SERIE_MENU_ITEM = Menu.FIRST;
	/* Context Menus */
	private static final int ADD_CONTEXT = Menu.FIRST;
	private ListView listView;
	private Utils utils = new Utils();
	static String searchQuery = "";
	private SQLiteStore db;
	private volatile List<String> series;
	private AsyncAddSerie addSerieTask = null;
	private Serie sToAdd;
	private androidx.appcompat.widget.SearchView searchView;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		// Apply the saved theme (plus Material You dynamic colors) before the window is created.
		nl.asymmetrics.droidshows.ThemeHelper.applyTheme(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.add_serie);
		searchView = (androidx.appcompat.widget.SearchView) findViewById(R.id.add_serie_searchview);
		searchView.setIconifiedByDefault(false);
		searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
			public boolean onQueryTextSubmit(String query) {
				searchQuery = query;
				TextView title = (TextView) findViewById(R.id.add_serie_title);
				title.setText(getString(R.string.dialog_search) + " " + searchQuery);
				searchView.clearFocus();
				doSearch();
				return true;
			}
			public boolean onQueryTextChange(String newText) {
				return false;
			}
		});
		listView = (ListView) findViewById(android.R.id.list);
		View emptyView = findViewById(android.R.id.empty);
		if (emptyView != null) listView.setEmptyView(emptyView);
		listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
				AddSerie.this.onListItemClick(v, position, id);
			}
		});
		db = SQLiteStore.getInstance(this);
		// Loading the owned-show ids hits the DB: do it on a worker thread so
		// the screen opens instantly. getView tolerates an empty list.
		series = new ArrayList<String>();
		new Thread(new Runnable() {
			public void run() {
				final List<String> ids = db.getSeries(2, false, null, 0);	// 2 = archive and current shows, false = don't filter networks, null = ignore networks filter, 0 = TV shows only
				runOnUiThread(new Runnable() {
					public void run() {
						if (isFinishing())
							return;
						series = ids;
						seriessearch_adapter.notifyDataSetChanged();
					}
				});
			}
		}).start();
		List<Serie> search_series = new ArrayList<Serie>();
		this.seriessearch_adapter = new SeriesSearchAdapter(this, R.layout.row_search_series, search_series);
		listView.setAdapter(seriessearch_adapter);
		Intent intent = getIntent();
		getSearchResults(intent);
	}

	/* Options Menu */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, ADD_SERIE_MENU_ITEM, 0, getString(R.string.menu_add_serie)).setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_menu_add));
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case ADD_SERIE_MENU_ITEM :
				searchView.requestFocus();
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	/* context menu */
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, ADD_CONTEXT, 0, getString(R.string.menu_context_add_serie));
	}

	public boolean onContextItemSelected(MenuItem item) {
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		final ListView serieList = listView;
		switch (item.getItemId()) {
			case ADD_CONTEXT :
				final Serie tmpSerie = (Serie) serieList.getAdapter().getItem(info.position);
				addSerie(tmpSerie);
				return true;
			default :
				return super.onContextItemSelected(item);
		}
	}
	private Runnable loadSearchSeries = new Runnable() {
		public void run() {
			seriessearch_adapter.clear();
			if (search_series != null && search_series.size() > 0) {
				for (int i = 0; i < search_series.size(); i++)
					seriessearch_adapter.add(search_series.get(i));
			}
			seriessearch_adapter.notifyDataSetChanged();
			dismissProgress();
		}
	};

	private void searchSeries(String searchQuery) {
		try {
			search_series = new ArrayList<Serie>();
			search_series = searchWithRetry(searchQuery);
			if (search_series == null) {
				dismissProgress();
				new android.os.Handler(android.os.Looper.getMainLooper()).post(new Runnable() {
					public void run() {
						Toast.makeText(getApplicationContext(), R.string.messages_thetvdb_con_error, Toast.LENGTH_LONG).show();
					}
				});
			} else {
				runOnUiThread(loadSearchSeries);
			}
		} catch (Exception e) {
			Log.e(SQLiteStore.TAG, "searchSeries failed", e);
			runOnUiThread(new Runnable() {
				public void run() {
					dismissProgress();
					Toast.makeText(getApplicationContext(), R.string.messages_thetvdb_con_error, Toast.LENGTH_LONG).show();
				}
			});
		}
	}

	/* Search TVMaze, retrying once after 10s when the API answers HTTP 429. */
	private List<Serie> searchWithRetry(String query) {
		try {
			return tvMaze.searchShows(query);
		} catch (JsonFetcher.RateLimitException e) {
			Log.d(SQLiteStore.TAG, "TVMaze rate limited, retrying search in 10s");
			try { Thread.sleep(10000); } catch (InterruptedException ie) {}
			try {
				return tvMaze.searchShows(query);
			} catch (JsonFetcher.RateLimitException e2) {
				Log.e(SQLiteStore.TAG, "TVMaze still rate limited for search");
				return null;
			}
		}
	}

	private void Search() {
		showProgress(R.string.messages_title_search_series, R.string.messages_search_series, true);
		new Thread(new Runnable() {
			public void run() {
				tvMaze = new TVMaze();
				searchSeries(searchQuery);
			}
		}).start();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		dismissProgress();
		super.onSaveInstanceState(outState);
	}
	
	private void addSerie(Serie s) {
		if (addSerieTask == null || addSerieTask.getStatus() != AsyncTask.Status.RUNNING) {
			addSerieTask = new AsyncAddSerie();
			// Don't use the default serial executor: one stalled poster
			// download used to block every AsyncTask queued behind it.
			addSerieTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, s);
		} else {
			Log.d(SQLiteStore.TAG, "Still busy, not adding "+ s.getSerieName());
			Toast.makeText(getApplicationContext(), R.string.messages_error_dbupdate, Toast.LENGTH_SHORT).show();
		}
	}

	private class AsyncAddSerie extends AsyncTask<Serie, Void, Boolean> {
		String msg = null;
		
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			showProgress(R.string.messages_title_adding_serie, R.string.messages_adding_serie, false);
		}

		protected Boolean doInBackground(Serie... params) {
			Serie s = params[0];
			Boolean success = false;
			
			boolean alreadyExists = false;
			List<String> knownIds = series;	// local copy: the field can be replaced on the UI thread
			for (String serieId : knownIds)
				if (serieId.equals(s.getId()) || s.getId().equals(db.getTvmazeId(serieId))) {
					alreadyExists = true;
					break;
				}
			if (alreadyExists) return false;
			
			if (tvMaze == null)
				tvMaze = new TVMaze();
			sToAdd = getShowWithRetry(tvMaze, s.getId());
			if (sToAdd == null) {
				msg = getString(R.string.messages_thetvdb_con_error);
			} else {
				addPosterThumb();
				try {
					Log.d(SQLiteStore.TAG, "Adding "+ sToAdd.getSerieName() +": saving TV show to database");
					sToAdd.setPassiveStatus((DroidShows.showArchive == 1 ? 1 : 0));
					sToAdd.saveToDB(db);
					Log.d(SQLiteStore.TAG, "Adding "+ sToAdd.getSerieName() +": creating the TV show item");
					int nseasons = db.getSeasonCount(sToAdd.getId());
					SQLiteStore.NextEpisode nextEpisode = db.getNextEpisode(sToAdd.getId());
					int unwatchedAired = db.getEpsUnwatchedAired(sToAdd.getId());
					int unwatched = db.getEpsUnwatched(sToAdd.getId());
					String nextEpisodeStr = db.getNextEpisodeString(nextEpisode, DroidShows.showNextAiring && 0 < unwatchedAired && unwatchedAired < unwatched);
					Drawable d = Drawable.createFromPath(sToAdd.getPosterThumb());
					TVShowItem tvsi = new TVShowItem(sToAdd.getId(), sToAdd.getLanguage(), sToAdd.getPosterThumb(), d, sToAdd.getSerieName(), nseasons,
						nextEpisodeStr, nextEpisode.firstAiredDate, unwatchedAired, unwatched, sToAdd.getPassiveStatus() == 1,
						(sToAdd.getStatus() == null ? "null" : sToAdd.getStatus()), "");
					DroidShows.series.add(tvsi);	// synchronizedList: safe from any thread
					// The owned-ids list is read by getView on the UI thread:
					// mutate it there, not here.
					final String addedId = sToAdd.getId();
					runOnUiThread(new Runnable() {
						public void run() { series.add(addedId); }
					});
					runOnUiThread(DroidShows.updateListView);
					success = true;
				} catch (Exception e) {
					Log.e(SQLiteStore.TAG, "Error adding "+ sToAdd.getSerieName());
				}
				if (success) {
					msg = String.format(getString(R.string.messages_series_success), sToAdd.getSerieName())
						+ (DroidShows.showArchive == 1 ? " ("+ getString(R.string.messages_context_archived) +")": "");
				}
			}
			sToAdd = null;
			return success;
		}
		
		private void addPosterThumb() {
			Log.d(SQLiteStore.TAG, "Adding "+ sToAdd.getSerieName() +": getting the poster");
			// get the poster and save it in cache
			String poster = sToAdd.getPoster();
			if (poster == null || poster.isEmpty()) {
				// new URL(null) throws an unchecked NullPointerException that
				// used to escape doInBackground, leaving the progress bar stuck
				Log.e(SQLiteStore.TAG, sToAdd.getSerieName() +" doesn't have a poster URL");
				return;
			}
			URL posterURL = null;
			String posterThumbPath = null;
			try {
				posterURL = new URL(poster);
				posterThumbPath = Utils.posterFile(getApplicationContext(), posterURL).getAbsolutePath();
			} catch (MalformedURLException e) {
				Log.e(SQLiteStore.TAG, sToAdd.getSerieName() +" doesn't have a poster URL");
				e.printStackTrace();
				return;
			}
			File posterThumbFile = new File(posterThumbPath);
			try {
				// Bounded connect/read timeouts: a stalled image host must not
				// hang this download (and the task queue) forever.
				Utils.downloadPosterThumb(getApplicationContext(), posterURL, posterThumbFile);
			} catch (IOException e) {
				Log.e(SQLiteStore.TAG, "Could not download poster: "+ posterURL);
				e.printStackTrace();
				return;
			}
			Bitmap posterThumb = BitmapFactory.decodeFile(posterThumbPath);
			if (posterThumb == null) {
				Log.e(SQLiteStore.TAG, "Corrupt or unknown poster file type: "+ posterThumbPath);
				return;
			}
			int width = getWindowManager().getDefaultDisplay().getWidth();
			int height = getWindowManager().getDefaultDisplay().getHeight();
			int newHeight = (int) ((height > width ? height : width) * 0.265);
			int newWidth = (int) (1.0 * posterThumb.getWidth() / posterThumb.getHeight() * newHeight);
			Bitmap resizedBitmap = Bitmap.createScaledBitmap(posterThumb, newWidth, newHeight, true);
			OutputStream fOut = null;
			try {
				fOut = new FileOutputStream(posterThumbFile, false);
				resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 90, fOut);
				fOut.flush();
				fOut.close();
				sToAdd.setPosterInCache("true");
				sToAdd.setPosterThumb(posterThumbPath);
			} catch (IOException e) {
				e.printStackTrace();
			}
			posterThumb.recycle();
			resizedBitmap.recycle();
			System.gc();
			posterThumb = null;
			resizedBitmap = null;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			super.onPostExecute(result);
			seriessearch_adapter.notifyDataSetChanged();
			if (msg != null) Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
			dismissProgress();
		}

		@Override
		protected void onCancelled(Boolean result) {
			this.onPostExecute(result);
			super.onCancelled();
		}
	}
	
	// Guillaume: searches from within this activity were discarded
	@Override
	protected void onNewIntent(Intent intent) {
		getSearchResults(intent);
	}

	private void getSearchResults(Intent intent) {
		if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
			// Movies tab searches go to AddMovie instead
			if (DroidShows.mediaType == 1) {
				String query = intent.getStringExtra(SearchManager.QUERY);
				Intent i = new Intent(this, AddMovie.class);
				i.setAction(Intent.ACTION_SEARCH);
				if (query != null)
					i.putExtra(SearchManager.QUERY, query);
				startActivity(i);
				finish();
				return;
			}
			searchQuery = intent.getStringExtra(SearchManager.QUERY);
			if (searchQuery == null || searchQuery.length() == 0) {
				searchView.requestFocus();
				return;
			}
			searchView.setQuery(searchQuery, false);
			TextView title = (TextView) findViewById(R.id.add_serie_title);
			title.setText(getString(R.string.dialog_search) + " " + searchQuery);
			doSearch();
		}
		listView.setOnTouchListener(new SwipeDetect());
		registerForContextMenu(listView);
	}
	
	private void doSearch() {
		if (utils.isNetworkAvailable(AddSerie.this))
			Search();
		else
			Toast.makeText(getApplicationContext(), R.string.messages_no_internet, Toast.LENGTH_LONG).show();
	}

	/* Fetch a full TVMaze show, retrying once after 10s on HTTP 429. */
	private Serie getShowWithRetry(TVMaze tvMaze, String tvmazeId) {
		try {
			return tvMaze.getShow(tvmazeId);
		} catch (JsonFetcher.RateLimitException e) {
			Log.d(SQLiteStore.TAG, "TVMaze rate limited, retrying in 10s");
			try { Thread.sleep(10000); } catch (InterruptedException ie) {}
			try {
				return tvMaze.getShow(tvmazeId);
			} catch (JsonFetcher.RateLimitException e2) {
				Log.e(SQLiteStore.TAG, "TVMaze still rate limited for show "+ tvmazeId);
				return null;
			}
		}
	}
	
	private void onListItemClick(View v, int position, long id) {
		// Read the row from the adapter, not the static search list: a newer
		// search can replace that list while this tap is being handled.
		final Serie sToAdd = seriessearch_adapter.getItem(position);
		if (sToAdd == null)
			return;
		AlertDialog sOverview = new MaterialAlertDialogBuilder(this)
		.setIcon(R.drawable.icon)
		.setTitle(sToAdd.getSerieName())
		.setMessage(sToAdd.getOverview())
		.setPositiveButton(getString(R.string.menu_context_add_serie), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
				addSerie(sToAdd);
			}
		})
		.setNegativeButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		})
		.show();
		
		for (String serieId : series)
			if (serieId.equals(sToAdd.getId())) {
				sOverview.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
				break;
			}
	}
	
	private class SeriesSearchAdapter extends ArrayAdapter<Serie>
	{
		private List<Serie> items;

		public SeriesSearchAdapter(Context context, int textViewResourceId, List<Serie> series) {
			super(context, textViewResourceId, series);
			this.items = series;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.row_search_series, parent, false);
			}
			final Serie o = items.get(position);
			if (o != null) {
				TextView sn = (TextView) v.findViewById(R.id.seriename);
				CheckedTextView ctv = (CheckedTextView) v.findViewById(R.id.addserieBtn);
				if (sn != null) {
					String lang = (o.getLanguage() == null ? "" : " ("+ o.getLanguage() +")");
					sn.setText(o.getSerieName() + lang);
				}
				if (ctv != null) {
					boolean alreadyExists = false;
					for (String serieId : series) {
						if (serieId.equals(o.getId())) {
							alreadyExists = true;
							break;
						}
					}
					if (alreadyExists) {
						ctv.setCheckMarkDrawable(getResources().getDrawable(R.drawable.star));
						// ctv.setVisibility(View.GONE);
						ctv.setOnClickListener(new OnClickListener() {
							public void onClick(View v) {
								// does nothing
								return;
							}
						});
					} else {
						// ctv.setVisibility(View.VISIBLE);
						ctv.setCheckMarkDrawable(getResources().getDrawable(R.drawable.add));
						ctv.setOnClickListener(new OnClickListener() {
							public void onClick(View v) {
								addSerie(o);
							}
						});
					}
				}
			}
			return v;
		}
	}
}