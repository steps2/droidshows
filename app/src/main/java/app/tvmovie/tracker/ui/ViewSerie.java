package app.tvmovie.tracker.ui;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import app.tvmovie.tracker.R;
import app.tvmovie.tracker.utils.SQLiteStore;
import app.tvmovie.tracker.utils.SwipeDetect;
import android.app.Activity;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;

import java.text.ParseException;

import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.webkit.WebView;
import android.webkit.WebView.HitTestResult;
import android.webkit.WebViewClient;
import android.widget.ImageView;
import android.widget.CheckBox;
import android.widget.TextView;

public class ViewSerie extends Activity
{
	private String serieId = null,
		serieName = "",
		posterURL = "#",
		fanartURL = "#",
		imdbId = "";
	private WebView posterView = null;
	private boolean posterLoaded = false;
	private String uri = "imdb:///";
	private List<String> actors = new ArrayList<String>();
	private SQLiteStore db;
	private SwipeDetect swipeDetect = new SwipeDetect();
	private boolean isMovie = false;
	private String movieEpisodeId = null;
	private double currentUserRating = 0;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		// Apply the saved theme (plus Material You dynamic colors) before the window is created.
		app.tvmovie.tracker.ThemeHelper.applyTheme(this);
		this.overridePendingTransition(R.anim.left_enter, R.anim.left_exit);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.view_serie);
		db = SQLiteStore.getInstance(this);
		View view = findViewById(R.id.viewSerie);
		view.setOnTouchListener(swipeDetect);
		serieId = getIntent().getStringExtra("serieId");
	
		String query = "SELECT serieName, posterThumb, poster, fanart, overview, status, firstAired, airsDayOfWeek, "
			+ "airsTime, runtime, network, rating, contentRating, imdbId, mediaType, userRating, tmdbId FROM series WHERE id = '" + serieId + "'";
		Cursor c = db.Query(query);
		if (c != null && c.moveToFirst()) {
			int snameCol = c.getColumnIndex("serieName");
			int posterThumbCol = c.getColumnIndex("posterThumb");
			int posterCol = c.getColumnIndex("poster");
			int fanartCol = c.getColumnIndex("fanart");
			int overviewCol = c.getColumnIndex("overview");
			int statusCol = c.getColumnIndex("status");
			int firstAiredCol = c.getColumnIndex("firstAired");
			int airsdayofweekCol = c.getColumnIndex("airsDayOfWeek");
			int airstimeCol = c.getColumnIndex("airsTime");
			int runtimeCol = c.getColumnIndex("runtime");
			int networkCol = c.getColumnIndex("network");
			int ratingCol = c.getColumnIndex("rating");
			int contentRatingCol = c.getColumnIndex("contentRating");
			int imdbIdCol = c.getColumnIndex("imdbId");
			int mediaTypeCol = c.getColumnIndex("mediaType");
			int userRatingCol = c.getColumnIndex("userRating");
			serieName = c.getString(snameCol);
			String posterThumb = c.getString(posterThumbCol);
			posterURL = c.getString(posterCol);
			fanartURL = c.getString(fanartCol);
			String serieOverview = c.getString(overviewCol);
			String status = c.getString(statusCol);
			String firstAired = c.getString(firstAiredCol);
			String airday = c.getString(airsdayofweekCol);
			String airtime = c.getString(airstimeCol);
			String runtime = c.getString(runtimeCol);
			String network = c.getString(networkCol);
			String rating = c.getString(ratingCol);
			String contentRating = c.getString(contentRatingCol);
			imdbId = c.getString(imdbIdCol);
			isMovie = (mediaTypeCol != -1 && c.getInt(mediaTypeCol) == 1);
			currentUserRating = (userRatingCol != -1 ? c.getDouble(userRatingCol) : 0);
			c.close();
					
			if (network != null && !network.equalsIgnoreCase("null")) {
				TextView networkV = (TextView) findViewById(R.id.network);
				networkV.setText(network);
			}
	
			if (contentRating != null && !contentRating.equalsIgnoreCase("null")) {
				TextView contentRatingV = (TextView) findViewById(R.id.contentRating);
				contentRatingV.setText(contentRating);
			}
			
			TextView serieNameV = (TextView) findViewById(R.id.serieName);
			serieNameV.setText(serieName);

			if (isMovie) {
				final CheckBox watchedV = (CheckBox) findViewById(R.id.movie_watched);
				Cursor cmovie = db.Query("SELECT id, seen FROM episodes WHERE serieId='"+ serieId +"' LIMIT 1");
				if (cmovie != null && cmovie.moveToFirst()) {
					movieEpisodeId = cmovie.getString(cmovie.getColumnIndex("id"));
					watchedV.setChecked(cmovie.getInt(cmovie.getColumnIndex("seen")) > 0);
					watchedV.setVisibility(View.VISIBLE);
					watchedV.setOnClickListener(new View.OnClickListener() {
						public void onClick(View v) {
							// The checkbox already flipped its visual state; mirror it in the database
							db.updateUnwatchedEpisode(serieId, movieEpisodeId);
						}
					});
				}
				if (cmovie != null) cmovie.close();
			}
	
			ImageView posterThumbV = (ImageView) findViewById(R.id.posterThumb);
			try {
				BitmapDrawable posterThumbD = (BitmapDrawable) BitmapDrawable.createFromPath(posterThumb);
				posterThumbD.setTargetDensity(getResources().getDisplayMetrics().densityDpi);	// Don't auto-resize to screen density
				posterThumbV.setImageDrawable(posterThumbD);
			}
			catch (Exception e) {}
					
			List<String> genres = new ArrayList<String>();
			Cursor cgenres = db.Query("SELECT genre FROM genres WHERE serieId='"+ serieId + "'");
			if (cgenres != null && cgenres.moveToFirst()) {
				do {
					genres.add(cgenres.getString(0));
				} while (cgenres.moveToNext());
			}
			if (cgenres != null) cgenres.close();
			if (!genres.isEmpty()) {
				TextView genreV = (TextView) findViewById(R.id.genre);
				genreV.setText(genres.toString().replace("]", "").replace("[", ""));
				genreV.setVisibility(View.VISIBLE);
			}

			TextView ratingV = (TextView) findViewById(R.id.rating);
			if (rating != null && !rating.equalsIgnoreCase("null") && !rating.equals(""))
				ratingV.setText((isMovie ? "TMDB: " : "IMDb: ")+ rating);
			else
				ratingV.setText(isMovie ? "TMDB Info" : "IMDb Info");
			ratingV.setOnTouchListener(swipeDetect);
			updateUserRatingRow();
					
			if (firstAired != null && !firstAired.equals("null") && !firstAired.equals("")) {
				TextView firstAiredV = (TextView) findViewById(R.id.firstAired);
				try {
					// dateFormat is shared across threads: parse under its lock.
					Date epDate;
					synchronized (SQLiteStore.dateFormat) { epDate = SQLiteStore.dateFormat.parse(firstAired); }
					firstAired = SimpleDateFormat.getDateInstance().format(epDate);
				} catch (ParseException e) {
					Log.e(SQLiteStore.TAG, e.getMessage());
				}
				if (status != null && !status.equalsIgnoreCase("null") && !status.equalsIgnoreCase(""))
					status = " ("+ translateStatus(status) +")";
				else
					status = "";
				firstAiredV.setText(firstAired + status);
				firstAiredV.setVisibility(View.VISIBLE);
			}
	
			if (airday != null && !airday.equalsIgnoreCase("null") && !airday.equals("")) {
				TextView airtimeV = (TextView) findViewById(R.id.airtime);
				if (airday.equalsIgnoreCase("Daily"))
					airday = getString(R.string.messages_daily);
				if (airtime != null && !airtime.equalsIgnoreCase("null") && !airtime.equals("")) {
					airtimeV.setText(airday +" "+ getString(R.string.messages_at) +" "+ airtime);
					airtimeV.setVisibility(View.VISIBLE);
				}
			}
	
			if (runtime != null && !runtime.equalsIgnoreCase("null") && !runtime.equals("")) {
				TextView runtimeV = (TextView) findViewById(R.id.runtime);
				runtimeV.setText(runtime +" "+ getString(R.string.series_runtime_minutes));
				runtimeV.setVisibility(View.VISIBLE);
				try {
					int runtimeInt = Integer.parseInt(runtime);
					int epCount = db.getEpsWatched(serieId);
					if (epCount > 0 && !isMovie) {
						int minutes = runtimeInt * epCount;
						int hours = minutes / 60;
						minutes = minutes % 60;
						runtimeV.setText(runtimeV.getText()
							+" ("+ (hours > 0 ? hours +":" : "")
							+ (minutes < 10 ? "0" : "") + minutes
							+" "+ getString(R.string.messages_marked_seen) +")");
					}
				} catch (Exception e) { e.printStackTrace(); }
			}
			
			TextView serieOverviewV = (TextView) findViewById(R.id.serieOverview);
			serieOverviewV.setText(serieOverview);

			Cursor cactors = db.Query("SELECT actor FROM actors WHERE serieId='"+ serieId + "'");
			if (cactors != null && cactors.moveToFirst()) {
				do {
					actors.add(cactors.getString(0));
				} while (cactors.moveToNext());
			}
			if (cactors != null) cactors.close();
			if (!actors.isEmpty()) {
				TextView serieActorsV = (TextView) findViewById(R.id.actors);
				serieActorsV.setText(actors.toString().replace("]", "").replace("[", ""));
				serieActorsV.setOnTouchListener(swipeDetect);
				View actorsField = (View) findViewById(R.id.actorsField);
				actorsField.setOnTouchListener(swipeDetect);
				actorsField.setVisibility(View.VISIBLE);
			}
		}
		
		Intent testForApp = new Intent(Intent.ACTION_VIEW, Uri.parse("imdb:///find"));
		if (getApplicationContext().getPackageManager().resolveActivity(testForApp, 0) == null)
			uri = "https://m.imdb.com/";

		loadWatchProviders();
	}
	
	/* Personal rating row + dialog (shared with the long-press menu). */
	private void updateUserRatingRow() {
		TextView userRatingV = (TextView) findViewById(R.id.userRating);
		if (userRatingV == null) return;
		if (currentUserRating > 0) {
			userRatingV.setText(getString(R.string.your_rating) + " " + RatingDialog.starsLabel(currentUserRating));
			userRatingV.setVisibility(View.VISIBLE);
		} else {
			userRatingV.setVisibility(View.GONE);
		}
	}

	public void rateShow(View v) {
		if (swipeDetect.value != 0) return;
		RatingDialog.show(this, getString(R.string.rate_title) + ": " + serieName, currentUserRating,
			new RatingDialog.OnRated() {
				public void onRated(double rating10) {
					db.setUserRating(serieId, rating10);
					currentUserRating = rating10;
					updateUserRatingRow();
				}
			});
	}

	/* Where-to-watch: TMDB watch/providers for the device region. Movies use
	 * their TMDB id directly; TV shows resolve via the cached tmdbId, falling
	 * back to an IMDb-id lookup that is then cached. The section stays hidden
	 * when the TMDB key is unset or no providers are found. */
	private void loadWatchProviders() {
		final String apiKey = getSharedPreferences("DroidShowsPref", 0)
			.getString(app.tvmovie.tracker.DroidShows.TMDB_API_KEY_NAME, "");
		if (apiKey == null || apiKey.isEmpty()) return;
		new Thread(new Runnable() {
			public void run() {
				final app.tvmovie.tracker.provider.TMDB tmdb =
					new app.tvmovie.tracker.provider.TMDB(apiKey);
				String tmdbId = "";
				String kind = "tv";
				if (isMovie) {
					tmdbId = serieId;
					kind = "movie";
				} else {
					tmdbId = db.getTmdbId(serieId);
					if (tmdbId.isEmpty() && imdbId != null && imdbId.startsWith("tt")) {
						tmdbId = tmdb.findTvShowIdByImdb(imdbId);
						if (!tmdbId.isEmpty() && !"0".equals(tmdbId))
							db.setTmdbId(serieId, tmdbId);
					}
				}
				final List<String> providers = (!tmdbId.isEmpty() && !"0".equals(tmdbId))
					? tmdb.getWatchProviders(kind, tmdbId) : new ArrayList<String>();
				runOnUiThread(new Runnable() {
					public void run() {
						if (isFinishing() || providers.isEmpty()) return;
						View field = findViewById(R.id.watchField);
						TextView tv = (TextView) findViewById(R.id.watchProviders);
						if (field != null && tv != null) {
							StringBuilder sb = new StringBuilder();
							for (String pr : providers) {
								if (sb.length() > 0) sb.append("\u2022 ");
								sb.append(pr).append("  ");
							}
							tv.setText(sb.toString().trim());
							field.setVisibility(View.VISIBLE);
						}
					}
				});
			}
		}).start();
	}

	private String translateStatus(String statusValue) {
		if (statusValue.equalsIgnoreCase("Continuing")) {
			return getString(R.string.showstatus_continuing);
		} else if (statusValue.equalsIgnoreCase("Ended")) {
			return getString(R.string.showstatus_ended);
		} else {
			return statusValue;
		}
	}
	
	public void IMDbDetails(View v) {
		if (swipeDetect.value != 0) return;
		String uri = this.uri;
		if (imdbId != null && imdbId.startsWith("tt")) {
			uri += "title/"+ imdbId;
		} else {
			uri += "find?q="+ Uri.encode(serieName);
		}
		Intent imdb = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
		imdb.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(imdb);
	}
	
	public void IMDbNames(View v) {
		if (swipeDetect.value != 0) return;
		new MaterialAlertDialogBuilder(this)
			.setTitle(R.string.menu_search)
			.setItems(actors.toArray(new CharSequence[actors.size()]), new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int item) {
					Intent imdb = new Intent(Intent.ACTION_VIEW, Uri.parse(uri +"find?q="+ Uri.encode(actors.get(item))));
					imdb.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					startActivity(imdb);
				}
			})
			.show();
	}
	
	public void posterView(View v) {
		if (!posterLoaded) {
			posterView = (WebView) findViewById(R.id.posterView);
			if (posterURL == null || posterURL.isEmpty() || posterURL.equalsIgnoreCase("null")) {
				if (fanartURL != null && !fanartURL.isEmpty() && !fanartURL.equalsIgnoreCase("null")) {
					posterURL = fanartURL;
				} else {
					return;
				}
			}
			if (fanartURL == null || fanartURL.isEmpty() || fanartURL.equalsIgnoreCase("null"))
				fanartURL = posterURL;

			posterView.getSettings().setBuiltInZoomControls(true);
			posterView.getSettings().setLoadWithOverviewMode(true);
			posterView.getSettings().setUseWideViewPort(true);
			posterView.loadData(getURL(posterURL, "ds:fanart"), "text/html", "UTF-8");
			posterView.setBackgroundColor(Color.BLACK);
			posterView.setInitialScale(1);
			posterView.setOverScrollMode(View.OVER_SCROLL_NEVER);
			posterView.setWebViewClient(new WebViewHandler());
			posterView.setLongClickable(true);
			posterView.setOnLongClickListener(new OnLongClickListener() {
				public boolean onLongClick(View arg0) {
					HitTestResult hit = posterView.getHitTestResult();
					if (hit.getType() == HitTestResult.IMAGE_TYPE ||
				            hit.getType() == HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
						Intent extViewIntent = new Intent();
						extViewIntent.setAction(Intent.ACTION_VIEW);
						extViewIntent.setDataAndType(Uri.parse(hit.getExtra()), "image/*");
						extViewIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(extViewIntent);
					}
					return true;
				}
			});
			posterLoaded = true;
		}
		posterView.setVisibility(View.VISIBLE);
	}
	
	private class WebViewHandler extends WebViewClient {
		@Override
		public boolean shouldOverrideUrlLoading(WebView v, String url) {
			String img = posterURL, a = "ds:fanart";
			if (url.equals("ds:fanart")) {
				img = fanartURL;
				a = "ds:poster";
			}
			v.loadData(getURL(img, a), "text/html", "UTF-8");
			return true;
		}

		/*@Override
		public void onPageFinished(WebView view, String url) {
		    super.onPageFinished(view, url);
		    view.clearCache(true);
		}*/
	}
	
	private String getURL(String img, String a) {
		return "<html><head><meta name=\"viewport\" content=\"width=device-width,user-scalable=1\">"
			+ "<style>*{margin:0;padding:0}</style></head><body><a href=\""+ a +"\"><img src=\""+ img +"\"/></a></body></html>";
	}
	
	@Override
	public void onBackPressed() {
		if (posterView != null && posterView.getVisibility() == View.VISIBLE)
			posterView.setVisibility(View.GONE);
		else {
			super.onBackPressed();
			overridePendingTransition(R.anim.right_enter, R.anim.right_exit);
		}
	}

	@Override
	protected void onDestroy() {
		// A WebView holds onto its renderer process unless destroyed: detach
		// and destroy it here so leaving the details screen frees the memory.
		if (posterView != null) {
			try {
				((android.view.ViewGroup) posterView.getParent()).removeView(posterView);
			} catch (Exception e) {}
			posterView.stopLoading();
			posterView.loadUrl("about:blank");
			posterView.setWebViewClient(null);
			posterView.destroy();
			posterView = null;
		}
		super.onDestroy();
	}
}
