package app.tvmovie.tracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms the daily new-episode check after a reboot (alarms don't survive). */
public class BootReceiver extends BroadcastReceiver {
	@Override
	public void onReceive(Context context, Intent intent) {
		if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
			boolean enabled = context.getSharedPreferences("DroidShowsPref", 0)
					.getBoolean(EpisodeNotifier.NOTIFY_NEW_EPISODES_PREF, true);
			if (enabled)
				EpisodeNotifier.scheduleDailyCheck(context);
		}
	}
}
