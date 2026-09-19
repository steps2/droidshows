package app.tvmovie.tracker;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.tvmovie.tracker.ui.ViewSerie;
import app.tvmovie.tracker.utils.SQLiteStore;

/**
 * Daily new-episode check. Finds Watching-list TV episodes that have aired,
 * are still unwatched and were never notified about, posts one notification
 * per show (inbox style when several), and marks them notified so each
 * episode notifies exactly once. Driven by an inexact daily AlarmManager
 * alarm (morning local time) and after every manual sync.
 */
public class EpisodeNotifier {

	public static final String NOTIFY_NEW_EPISODES_PREF = "notify_new_episodes";
	private static final String CHANNEL_ID = "tvmovie_tracker_new_episodes";
	private static final String TAG = "EpisodeNotifier";

	/** Schedule the daily morning check (inexact, battery-friendly). */
	public static void scheduleDailyCheck(Context context) {
		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		if (am == null) return;
		PendingIntent pi = checkPendingIntent(context);
		am.cancel(pi);
		java.util.Calendar cal = java.util.Calendar.getInstance();
		cal.set(java.util.Calendar.HOUR_OF_DAY, 8);
		cal.set(java.util.Calendar.MINUTE, 0);
		cal.set(java.util.Calendar.SECOND, 0);
		cal.set(java.util.Calendar.MILLISECOND, 0);
		if (cal.getTimeInMillis() <= System.currentTimeMillis())
			cal.add(java.util.Calendar.DAY_OF_YEAR, 1);
		am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(),
				AlarmManager.INTERVAL_DAY, pi);
	}

	public static void cancelDailyCheck(Context context) {
		AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
		if (am != null) am.cancel(checkPendingIntent(context));
	}

	private static PendingIntent checkPendingIntent(Context context) {
		Intent i = new Intent(context, EpisodeCheckReceiver.class);
		i.setAction("app.tvmovie.tracker.CHECK_NEW_EPISODES");
		return PendingIntent.getBroadcast(context, 0, i,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
	}

	/** Run the check on a background thread. Safe to call from anywhere. */
	public static void checkNow(final Context context) {
		new Thread(new Runnable() {
			public void run() {
				doCheck(context.getApplicationContext());
			}
		}).start();
	}

	/** The check itself. Call from a background thread. */
	public static void doCheck(Context context) {
		SharedPreferences prefs = context.getSharedPreferences("DroidShowsPref", 0);
		if (!prefs.getBoolean(NOTIFY_NEW_EPISODES_PREF, true)) return;
		if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return;
		SQLiteStore db;
		try {
			db = SQLiteStore.getInstance(context);
		} catch (Exception e) {
			Log.e(TAG, "doCheck: no database", e);
			return;
		}
		String today = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
		List<String[]> rows = db.getUnnotifiedAiredEpisodes(today);
		if (rows.isEmpty()) return;

		// Group episodes per show, preserving order.
		Map<String, List<String[]>> byShow = new LinkedHashMap<String, List<String[]>>();
		Map<String, String> showNames = new LinkedHashMap<String, String>();
		for (String[] r : rows) {
			String serieId = r[1];
			List<String[]> list = byShow.get(serieId);
			if (list == null) {
				list = new ArrayList<String[]>();
				byShow.put(serieId, list);
				showNames.put(serieId, r[2] != null ? r[2] : "");
			}
			list.add(r);
		}

		ensureChannel(context);
		NotificationManagerCompat nm = NotificationManagerCompat.from(context);
		List<String> notifiedRowIds = new ArrayList<String>();
		for (Map.Entry<String, List<String[]>> e : byShow.entrySet()) {
			String serieId = e.getKey();
			List<String[]> eps = e.getValue();
			String showName = showNames.get(serieId);

			NotificationCompat.Builder b = new NotificationCompat.Builder(context, CHANNEL_ID)
					.setSmallIcon(R.drawable.ic_stat_tv)
					.setAutoCancel(true)
					.setContentIntent(showIntent(context, serieId));
			if (eps.size() == 1) {
				String[] ep = eps.get(0);
				b.setContentTitle(showName)
				 .setContentText(epLabel(ep) + " has aired");
			} else {
				b.setContentTitle(showName)
				 .setContentText(eps.size() + " new episodes have aired");
				NotificationCompat.InboxStyle inbox = new NotificationCompat.InboxStyle()
						.setBigContentTitle(showName);
				for (String[] ep : eps)
					inbox.addLine(epLabel(ep) + " has aired");
				b.setStyle(inbox);
			}
			try {
				nm.notify(("newep_" + serieId).hashCode(), b.build());
			} catch (SecurityException se) {
				Log.e(TAG, "doCheck: notification permission revoked", se);
				return;
			}
			for (String[] ep : eps)
				notifiedRowIds.add(ep[0]);
		}
		// Mark notified only for episodes we actually posted about.
		db.markEpisodesNotified(notifiedRowIds);
	}

	private static String epLabel(String[] ep) {
		// ep: {rowId, serieId, serieName, seasonNumber, episodeNumber, episodeName, firstAired}
		String se = "S" + pad(ep[3]) + "E" + pad(ep[4]);
		String name = ep[5] != null ? ep[5].trim() : "";
		return name.isEmpty() ? se : se + " \u00b7 " + name;
	}

	private static String pad(String n) {
		try {
			int v = Integer.parseInt(n);
			return v < 10 ? "0" + v : String.valueOf(v);
		} catch (NumberFormatException e) {
			return n != null ? n : "";
		}
	}

	private static PendingIntent showIntent(Context context, String serieId) {
		Intent i = new Intent(context, ViewSerie.class);
		i.putExtra("serieId", serieId);
		return PendingIntent.getActivity(context, ("view_" + serieId).hashCode(), i,
				PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
	}

	private static void ensureChannel(Context context) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
		NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
		if (nm == null || nm.getNotificationChannel(CHANNEL_ID) != null) return;
		nm.createNotificationChannel(new NotificationChannel(
				CHANNEL_ID,
				context.getString(R.string.notify_channel_new_episodes),
				NotificationManager.IMPORTANCE_DEFAULT));
	}
}
