package app.tvmovie.tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Fires the daily new-episode check from the AlarmManager alarm. */
public class EpisodeCheckReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		EpisodeNotifier.checkNow(context);
	}
}
