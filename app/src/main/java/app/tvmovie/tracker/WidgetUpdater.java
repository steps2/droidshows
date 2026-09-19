package app.tvmovie.tracker;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.List;

import app.tvmovie.tracker.ui.ViewSerie;
import app.tvmovie.tracker.utils.SQLiteStore;

/**
 * Refreshes every Up next widget from the library. Runs its own background
 * thread; safe to call from anywhere (sync, restore, per-show update,
 * mark-watched). Shows the next unwatched episode of up to five Watching
 * shows plus the unwatched film count. Tapping a row opens that show;
 * tapping anywhere else opens the app.
 */
public class WidgetUpdater {

	private static final int MAX_ROWS = 5;
	private static final int[] ROW_IDS = {
		R.id.widget_row_0, R.id.widget_row_1, R.id.widget_row_2,
		R.id.widget_row_3, R.id.widget_row_4
	};

	public static void refresh(final Context context) {
		new Thread(new Runnable() {
			public void run() {
				doRefresh(context.getApplicationContext());
			}
		}).start();
	}

	private static void doRefresh(Context context) {
		SQLiteStore db;
		try {
			db = SQLiteStore.getInstance(context);
		} catch (Exception e) {
			Log.e("WidgetUpdater", "no database", e);
			return;
		}
		List<String[]> upNext = new ArrayList<String[]>();	// {serieId, text}
		int moviesUnwatched = 0;
		Cursor c = null;
		try {
			c = db.Query("SELECT s.id, s.serieName, e.seasonNumber, e.episodeNumber, e.episodeName "
				+ "FROM episodes e JOIN series s ON s.id=e.serieId "
				+ "WHERE (s.passiveStatus=0 OR s.passiveStatus IS NULL) AND s.mediaType=0 "
				+ "AND e.seen=0 AND e.seasonNumber<>0 "
				+ "ORDER BY s.serieName COLLATE NOCASE, e.seasonNumber, e.episodeNumber");
			if (c != null && c.moveToFirst()) {
				String lastShow = null;
				do {
					String serieId = c.getString(0);
					if (serieId != null && !serieId.equals(lastShow) && upNext.size() < MAX_ROWS) {
						lastShow = serieId;
						String name = c.getString(1);
						String se = "S" + pad(c.getString(2)) + "E" + pad(c.getString(3));
						String title = c.getString(4);
						String text = (name != null ? name : "") + " \u00b7 " + se;
						if (title != null && !title.trim().isEmpty())
							text += " \u00b7 " + title.trim();
						upNext.add(new String[] { serieId, text });
					}
				} while (c.moveToNext());
			}
		} catch (SQLiteException e) {
			Log.e("WidgetUpdater", e.getMessage());
		}
		if (c != null) c.close();

		Cursor cm = null;
		try {
			cm = db.Query("SELECT COUNT(e.id) FROM episodes e JOIN series s ON s.id=e.serieId "
				+ "WHERE (s.passiveStatus=0 OR s.passiveStatus IS NULL) AND s.mediaType=1 AND e.seen=0");
			if (cm != null && cm.moveToFirst())
				moviesUnwatched = cm.getInt(0);
		} catch (SQLiteException e) {
			Log.e("WidgetUpdater", e.getMessage());
		}
		if (cm != null) cm.close();

		RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_up_next);
		views.setTextViewText(R.id.widget_header, context.getString(R.string.widget_up_next));
		if (upNext.isEmpty()) {
			views.setTextViewText(R.id.widget_row_0, context.getString(R.string.widget_nothing));
			views.setViewVisibility(R.id.widget_row_0, View.VISIBLE);
			views.setOnClickPendingIntent(R.id.widget_row_0, appIntent(context));
			for (int i = 1; i < MAX_ROWS; i++)
				views.setViewVisibility(ROW_IDS[i], View.GONE);
		} else {
			for (int i = 0; i < MAX_ROWS; i++) {
				if (i < upNext.size()) {
					views.setViewVisibility(ROW_IDS[i], View.VISIBLE);
					views.setTextViewText(ROW_IDS[i], upNext.get(i)[1]);
					views.setOnClickPendingIntent(ROW_IDS[i], showIntent(context, upNext.get(i)[0]));
				} else {
					views.setViewVisibility(ROW_IDS[i], View.GONE);
				}
			}
		}
		if (moviesUnwatched > 0) {
			views.setViewVisibility(R.id.widget_movies, View.VISIBLE);
			views.setTextViewText(R.id.widget_movies,
				String.format(context.getString(R.string.widget_movies_line), moviesUnwatched));
		} else {
			views.setViewVisibility(R.id.widget_movies, View.GONE);
		}
		views.setOnClickPendingIntent(R.id.widget_root, appIntent(context));
		views.setOnClickPendingIntent(R.id.widget_header, appIntent(context));

		try {
			AppWidgetManager mgr = AppWidgetManager.getInstance(context);
			ComponentName cn = new ComponentName(context, UpNextWidget.class);
			for (int widgetId : mgr.getAppWidgetIds(cn))
				mgr.updateAppWidget(widgetId, views);
		} catch (Exception e) {
			Log.e("WidgetUpdater", "updateAppWidget failed", e);
		}
	}

	private static String pad(String n) {
		try {
			int v = Integer.parseInt(n);
			return v < 10 ? "0" + v : String.valueOf(v);
		} catch (NumberFormatException e) {
			return n != null ? n : "";
		}
	}

	private static PendingIntent appIntent(Context context) {
		Intent i = new Intent(context, DroidShows.class);
		return PendingIntent.getActivity(context, 9001, i,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
	}

	private static PendingIntent showIntent(Context context, String serieId) {
		Intent i = new Intent(context, ViewSerie.class);
		i.putExtra("serieId", serieId);
		return PendingIntent.getActivity(context, ("widget_" + serieId).hashCode(), i,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
	}
}
