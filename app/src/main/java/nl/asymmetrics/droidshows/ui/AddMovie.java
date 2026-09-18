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
import nl.asymmetrics.droidshows.provider.TMDB;
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
import android.os.Looper;
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

import org.apache.commons.io.FileUtils;

public class AddMovie extends AppCompatActivity
{
	private static List<Serie> search_movies = null;
	private TMDB tmdb;
	private MovieSearchAdapter moviesearch_adapter;
	/* DIALOGS */
	private androidx.appcompat.app.AlertDialog m_ProgressDialog = null;

	private void showProgress(int titleRes, int msgRes, boolean cancelable) {
		dismissProgress();
		View v = View.inflate(this, R.layout.progress_dialog, null);
		((TextView) v.findViewById(R.id.progress_msg)).setText(msgRes);
		com.google.android.material.progressindicator.LinearProgressIndicator bar =
			(com.google.android.material.progressindicator.LinearProgressIndicator) v.findViewById(R.id.progress_bar);
		bar.setIndeterminate(true);
		m_ProgressDialog = new MaterialAlertDialogBuilder(this)
			.setTitle(titleRes).setView(v).setCancelable(cancelable).create();
		m_ProgressDialog.show();
	}

	private void dismissProgress() {
		final androidx.appcompat.app.AlertDialog dlg = m_ProgressDialog;
		m_ProgressDialog = null;
		if (dlg == null) return;
		if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) dlg.dismiss();
		else runOnUiThread(new Runnable() { public void run() { dlg.dismiss(); } });
	}
	/* Option Menus */
	private static final int ADD_MOVIE_MENU_ITEM = Menu.FIRST;
	/* Context Menus */
	private static final int ADD_CONTEXT = Menu.FIRST;
	private ListView listView;
	private Utils utils = new Utils();
	static String searchQuery = "";
	private SQLiteStore db;
	private List<String> movies;
	private String apiKey = "";
	private AsyncAddMovie addMovieTask = null;
	private Serie mToAdd;
	private androidx.appcompat.widget.SearchView searchView;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// Apply the saved theme (plus Material You dynamic colors) before the window is created.
		nl.asymmetrics.droidshows.ThemeHelper.applyTheme(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.add_movie);
		searchView = (androidx.appcompat.widget.SearchView) findViewById(R.id.add_movie_searchview);
		searchView.setIconifiedByDefault(false);
		searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
			public boolean onQueryTextSubmit(String query) {
				searchQuery = query;
				TextView title = (TextView) findViewById(R.id.add_movie_title);
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
				AddMovie.this.onListItemClick(v, position, id);
			}
		});
		db = SQLiteStore.getInstance(this);
		movies = db.getSeries(2, false, null, 1);	// 2 = archive and current, false = don't filter networks, null = ignore networks filter, 1 = movies only
		List<Serie> search_movies = new ArrayList<Serie>();
		this.moviesearch_adapter = new MovieSearchAdapter(this, R.layout.row_search_movies, search_movies);
		listView.setAdapter(moviesearch_adapter);
		((TextView) findViewById(android.R.id.empty)).setText(R.string.layout_search_no_movies);
		apiKey = getSharedPreferences("DroidShowsPref", 0).getString(DroidShows.TMDB_API_KEY_NAME, "");
		if (apiKey == null || apiKey.length() == 0) {
			((TextView) findViewById(R.id.add_movie_title)).setText(R.string.tmdb_key_required);
			Toast.makeText(getApplicationContext(), R.string.tmdb_key_missing, Toast.LENGTH_LONG).show();
			return;
		}
		Intent intent = getIntent();
		getSearchResults(intent);
	}

	/* Options Menu */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, ADD_MOVIE_MENU_ITEM, 0, getString(R.string.menu_add_movie)).setIcon(AppCompatResources.getDrawable(this, R.drawable.ic_menu_add));
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case ADD_MOVIE_MENU_ITEM :
				searchView.requestFocus();
				break;
		}
		return super.onOptionsItemSelected(item);
	}

	/* context menu */
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, ADD_CONTEXT, 0, getString(R.string.menu_add_movie));
	}

	public boolean onContextItemSelected(MenuItem item) {
		final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		final ListView movieList = listView;
		switch (item.getItemId()) {
			case ADD_CONTEXT :
				final Serie tmpMovie = (Serie) movieList.getAdapter().getItem(info.position);
				addMovie(tmpMovie);
				return true;
			default :
				return super.onContextItemSelected(item);
		}
	}
	private Runnable loadSearchMovies = new Runnable() {
		public void run() {
			moviesearch_adapter.clear();
			if (search_movies != null && search_movies.size() > 0) {
				for (int i = 0; i < search_movies.size(); i++)
					moviesearch_adapter.add(search_movies.get(i));
			}
			moviesearch_adapter.notifyDataSetChanged();
			dismissProgress();
		}
	};

	private void searchMovies(String searchQuery) {
		try {
			search_movies = new ArrayList<Serie>();
			search_movies = tmdb.searchMovies(searchQuery);
			if (search_movies == null) {
				dismissProgress();
				Looper.prepare();
					Toast.makeText(getApplicationContext(), R.string.messages_tmdb_con_error, Toast.LENGTH_LONG).show();
				Looper.loop();
			} else {
				runOnUiThread(loadSearchMovies);
			}
		} catch (Exception e) {
			Log.e(SQLiteStore.TAG, "searchMovies failed", e);
			runOnUiThread(new Runnable() {
				public void run() {
					dismissProgress();
					Toast.makeText(getApplicationContext(), R.string.messages_tmdb_con_error, Toast.LENGTH_LONG).show();
				}
			});
		}
	}

	private void Search() {
		showProgress(R.string.messages_title_search_movies, R.string.messages_search_movies, true);
		new Thread(new Runnable() {
			public void run() {
				tmdb = new TMDB(apiKey);
				searchMovies(searchQuery);
			}
		}).start();
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		if (m_ProgressDialog != null) dismissProgress();
		super.onSaveInstanceState(outState);
	}

	private void addMovie(Serie s) {
		if (addMovieTask == null || addMovieTask.getStatus() != AsyncTask.Status.RUNNING) {
			addMovieTask = new AsyncAddMovie();
			addMovieTask.execute(s);
		} else {
			Log.d(SQLiteStore.TAG, "Still busy, not adding "+ s.getSerieName());
			Toast.makeText(getApplicationContext(), R.string.messages_error_dbupdate, Toast.LENGTH_SHORT).show();
		}
	}

	private class AsyncAddMovie extends AsyncTask<Serie, Void, Boolean> {
		String msg = null;

		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			showProgress(R.string.messages_title_adding_movie, R.string.messages_adding_movie, false);
		}

		protected Boolean doInBackground(Serie... params) {
			Serie s = params[0];
			Boolean success = false;

			boolean alreadyExists = false;
			for (String movieId : movies)
				if (movieId.equals(s.getId())) {
					alreadyExists = true;
					break;
				}
			if (alreadyExists) return false;

			mToAdd = tmdb.getMovie(s.getId());
			if (mToAdd == null) {
				msg = getString(R.string.messages_tmdb_con_error);
			} else {
				addPosterThumb();
				try {
					Log.d(SQLiteStore.TAG, "Adding "+ mToAdd.getSerieName() +": saving movie to database");
					mToAdd.setPassiveStatus((DroidShows.showArchive == 1 ? 1 : 0));
					mToAdd.saveToDB(db);
					Log.d(SQLiteStore.TAG, "Adding "+ mToAdd.getSerieName() +": creating the movie item");
					int nseasons = db.getSeasonCount(mToAdd.getId());
					SQLiteStore.NextEpisode nextEpisode = db.getNextEpisode(mToAdd.getId());
					int unwatchedAired = db.getEpsUnwatchedAired(mToAdd.getId());
					int unwatched = db.getEpsUnwatched(mToAdd.getId());
					String nextEpisodeStr = db.getNextEpisodeString(nextEpisode, DroidShows.showNextAiring && 0 < unwatchedAired && unwatchedAired < unwatched);
					Drawable d = Drawable.createFromPath(mToAdd.getPosterThumb());
					TVShowItem tvsi = new TVShowItem(mToAdd.getId(), mToAdd.getLanguage(), mToAdd.getPosterThumb(), d, mToAdd.getSerieName(), nseasons,
						nextEpisodeStr, nextEpisode.firstAiredDate, unwatchedAired, unwatched, mToAdd.getPassiveStatus() == 1,
						(mToAdd.getStatus() == null ? "null" : mToAdd.getStatus()), "");
					tvsi.setMediaType(1);
					DroidShows.series.add(tvsi);
					movies.add(mToAdd.getId());
					runOnUiThread(DroidShows.updateListView);
					success = true;
				} catch (Exception e) {
					Log.e(SQLiteStore.TAG, "Error adding "+ mToAdd.getSerieName());
				}
				if (success) {
					msg = String.format(getString(R.string.messages_movie_success), mToAdd.getSerieName())
						+ (DroidShows.showArchive == 1 ? " ("+ getString(R.string.messages_context_archived) +")": "");
				}
			}
			mToAdd = null;
			return success;
		}

		private void addPosterThumb() {
			Log.d(SQLiteStore.TAG, "Adding "+ mToAdd.getSerieName() +": getting the poster");
			// get the poster and save it in cache
			String poster = mToAdd.getPoster();
			URL posterURL = null;
			String posterThumbPath = null;
			try {
				posterURL = new URL(poster);
				posterThumbPath = getApplicationContext().getFilesDir().getAbsolutePath() +"/thumbs"+ posterURL.getFile().toString();
			} catch (MalformedURLException e) {
				Log.e(SQLiteStore.TAG, mToAdd.getSerieName() +" doesn't have a poster URL");
				e.printStackTrace();
				return;
			}
			File posterThumbFile = new File(posterThumbPath);
			try {
				FileUtils.copyURLToFile(posterURL, posterThumbFile);
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
				mToAdd.setPosterInCache("true");
				mToAdd.setPosterThumb(posterThumbPath);
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
			moviesearch_adapter.notifyDataSetChanged();
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
			searchQuery = intent.getStringExtra(SearchManager.QUERY);
			if (searchQuery == null || searchQuery.length() == 0) {
				searchView.requestFocus();
				return;
			}
			searchView.setQuery(searchQuery, false);
			TextView title = (TextView) findViewById(R.id.add_movie_title);
			title.setText(getString(R.string.dialog_search) + " " + searchQuery);
			doSearch();
		}
		listView.setOnTouchListener(new SwipeDetect());
		registerForContextMenu(listView);
	}

	private void doSearch() {
		if (apiKey == null || apiKey.length() == 0) {
			Toast.makeText(getApplicationContext(), R.string.tmdb_key_missing, Toast.LENGTH_LONG).show();
			return;
		}
		if (utils.isNetworkAvailable(AddMovie.this))
			Search();
		else
			Toast.makeText(getApplicationContext(), R.string.messages_no_internet, Toast.LENGTH_LONG).show();
	}

	private void onListItemClick(View v, int position, long id) {
		final Serie mToAdd = AddMovie.search_movies.get(position);
		AlertDialog sOverview = new MaterialAlertDialogBuilder(this)
		.setIcon(R.drawable.icon)
		.setTitle(mToAdd.getSerieName())
		.setMessage(mToAdd.getOverview())
		.setPositiveButton(getString(R.string.menu_add_movie), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int id) {
				dialog.dismiss();
				addMovie(mToAdd);
			}
		})
		.setNegativeButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		})
		.show();

		for (String movieId : movies)
			if (movieId.equals(mToAdd.getId())) {
				sOverview.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
				break;
			}
	}

	private class MovieSearchAdapter extends ArrayAdapter<Serie>
	{
		private List<Serie> items;

		public MovieSearchAdapter(Context context, int textViewResourceId, List<Serie> movies) {
			super(context, textViewResourceId, movies);
			this.items = movies;
		}

		public View getView(int position, View convertView, ViewGroup parent) {
			View v = convertView;
			if (v == null) {
				LayoutInflater vi = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
				v = vi.inflate(R.layout.row_search_movies, parent, false);
			}
			final Serie o = items.get(position);
			if (o != null) {
				TextView sn = (TextView) v.findViewById(R.id.seriename);
				CheckedTextView ctv = (CheckedTextView) v.findViewById(R.id.addserieBtn);
				if (sn != null) {
					String year = "";
					if (o.getFirstAired() != null && o.getFirstAired().length() >= 4)
						year = " ("+ o.getFirstAired().substring(0, 4) +")";
					sn.setText(o.getSerieName() + year);
				}
				if (ctv != null) {
					boolean alreadyExists = false;
					for (String movieId : movies) {
						if (movieId.equals(o.getId())) {
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
								addMovie(o);
							}
						});
					}
				}
			}
			return v;
		}
	}
}
