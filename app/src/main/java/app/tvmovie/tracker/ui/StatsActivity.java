package app.tvmovie.tracker.ui;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.view.View;
import android.widget.TextView;
import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

import app.tvmovie.tracker.DroidShowsApp;
import app.tvmovie.tracker.R;
import app.tvmovie.tracker.utils.SQLiteStore;

/**
 * Library statistics: episodes/hours watched, watch streaks, Watching vs
 * Finished counts and the average personal rating. Everything is computed
 * on a background thread with the thin top progress bar.
 */
public class StatsActivity extends Activity {

	private SQLiteStore db;

	private static class Stats {
		int episodesWatched;
		double hoursWatched;
		int currentStreak;
		int longestStreak;
		int watchingShows, finishedShows, watchingMovies, finishedMovies;
		double avgRating;
		boolean hasRating;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		app.tvmovie.tracker.ThemeHelper.applyTheme(this);
		super.onCreate(savedInstanceState);
		setContentView(R.layout.stats);
		MaterialToolbar toolbar = (MaterialToolbar) findViewById(R.id.toolbar);
		toolbar.setNavigationOnClickListener(new View.OnClickListener() {
			public void onClick(View v) { finish(); }
		});
		db = SQLiteStore.getInstance(this);
		DroidShowsApp.beginOperation(true, 0);
		new Thread(new Runnable() {
			public void run() {
				final Stats stats = compute();
				runOnUiThread(new Runnable() {
					public void run() {
						try {
							if (!isFinishing()) populate(stats);
						} finally {
							DroidShowsApp.endOperation();
						}
					}
				});
			}
		}).start();
	}

	private Stats compute() {
		Stats st = new Stats();
		st.episodesWatched = count("SELECT COUNT(*) FROM episodes WHERE seen>0");
		st.hoursWatched = computeHours();
		int[] streaks = computeStreaks();
		st.currentStreak = streaks[0];
		st.longestStreak = streaks[1];
		st.watchingShows = count("SELECT COUNT(*) FROM series WHERE (passiveStatus=0 OR passiveStatus IS NULL) AND mediaType=0");
		st.finishedShows = count("SELECT COUNT(*) FROM series WHERE passiveStatus>=1 AND mediaType=0");
		st.watchingMovies = count("SELECT COUNT(*) FROM series WHERE (passiveStatus=0 OR passiveStatus IS NULL) AND mediaType=1");
		st.finishedMovies = count("SELECT COUNT(*) FROM series WHERE passiveStatus>=1 AND mediaType=1");
		Cursor c = null;
		try {
			c = db.Query("SELECT AVG(userRating) FROM series WHERE userRating>0");
			if (c != null && c.moveToFirst() && !c.isNull(0)) {
				st.avgRating = c.getDouble(0);
				st.hasRating = true;
			}
		} catch (SQLiteException e) {
			Log.e(SQLiteStore.TAG, e.getMessage());
		}
		if (c != null) c.close();
		return st;
	}

	private int count(String sql) {
		int n = 0;
		Cursor c = null;
		try {
			c = db.Query(sql);
			if (c != null && c.moveToFirst()) n = c.getInt(0);
		} catch (SQLiteException e) {
			Log.e(SQLiteStore.TAG, e.getMessage());
		}
		if (c != null) c.close();
		return n;
	}

	private double computeHours() {
		double minutes = 0;
		Cursor c = null;
		try {
			c = db.Query("SELECT s.runtime, COUNT(e.id) FROM series s "
				+ "JOIN episodes e ON e.serieId=s.id WHERE e.seen>0 GROUP BY s.id");
			if (c != null && c.moveToFirst()) {
				do {
					String runtime = c.getString(0);
					int eps = c.getInt(1);
					if (runtime != null) {
						try {
							minutes += Integer.parseInt(runtime.trim()) * eps;
						} catch (NumberFormatException e) { /* no runtime: skip */ }
					}
				} while (c.moveToNext());
			}
		} catch (SQLiteException e) {
			Log.e(SQLiteStore.TAG, e.getMessage());
		}
		if (c != null) c.close();
		return minutes / 60.0;
	}

	/** Returns {currentStreak, longestStreak} in days from distinct seen-days. */
	private int[] computeStreaks() {
		List<Long> days = new ArrayList<Long>();
		Cursor c = null;
		try {
			c = db.Query("SELECT DISTINCT CAST(seen/86400 AS INTEGER) FROM episodes "
				+ "WHERE seen>0 ORDER BY 1");
			if (c != null && c.moveToFirst()) {
				do {
					days.add(c.getLong(0));
				} while (c.moveToNext());
			}
		} catch (SQLiteException e) {
			Log.e(SQLiteStore.TAG, e.getMessage());
		}
		if (c != null) c.close();
		if (days.isEmpty()) return new int[] {0, 0};
		int longest = 1, run = 1;
		for (int i = 1; i < days.size(); i++) {
			if (days.get(i) == days.get(i - 1) + 1) {
				run++;
				if (run > longest) longest = run;
			} else if (!days.get(i).equals(days.get(i - 1))) {
				run = 1;
			}
		}
		long today = System.currentTimeMillis() / 1000 / 86400L;
		long last = days.get(days.size() - 1);
		int current = 0;
		if (last == today || last == today - 1) {
			current = 1;
			for (int i = days.size() - 1; i > 0; i--) {
				if (days.get(i) == days.get(i - 1) + 1) current++;
				else break;
			}
		}
		return new int[] {current, longest};
	}

	private void populate(Stats st) {
		LinearLayout container = (LinearLayout) findViewById(R.id.stats_container);
		container.removeAllViews();
		addRow(container, getString(R.string.stats_episodes_watched), String.valueOf(st.episodesWatched));
		addRow(container, getString(R.string.stats_hours_watched),
				String.format(java.util.Locale.US, "%.1f", st.hoursWatched));
		addRow(container, getString(R.string.stats_current_streak),
				getResources().getQuantityString(R.plurals.stats_days, st.currentStreak, st.currentStreak));
		addRow(container, getString(R.string.stats_longest_streak),
				getResources().getQuantityString(R.plurals.stats_days, st.longestStreak, st.longestStreak));
		addRow(container, getString(R.string.stats_watching) + " \u00b7 " + getString(R.string.stats_shows),
				String.valueOf(st.watchingShows));
		addRow(container, getString(R.string.stats_finished) + " \u00b7 " + getString(R.string.stats_shows),
				String.valueOf(st.finishedShows));
		addRow(container, getString(R.string.stats_watching) + " \u00b7 " + getString(R.string.stats_movies),
				String.valueOf(st.watchingMovies));
		addRow(container, getString(R.string.stats_finished) + " \u00b7 " + getString(R.string.stats_movies),
				String.valueOf(st.finishedMovies));
		addRow(container, getString(R.string.stats_avg_rating), st.hasRating
				? RatingDialog.starsLabel(st.avgRating) : getString(R.string.stats_not_rated));
	}

	private void addRow(LinearLayout container, String label, String value) {
		LinearLayout row = new LinearLayout(this);
		row.setOrientation(LinearLayout.HORIZONTAL);
		row.setPadding(0, dp(8), 0, dp(8));
		TextView l = new TextView(this);
		l.setText(label);
		l.setTextSize(16);
		l.setLayoutParams(new LinearLayout.LayoutParams(0,
				LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
		TextView v = new TextView(this);
		v.setText(value);
		v.setTextSize(16);
		v.setGravity(Gravity.END);
		row.addView(l);
		row.addView(v);
		container.addView(row);
	}

	private int dp(int v) {
		return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
	}
}
