package nl.asymmetrics.droidshows;

import android.app.Activity;
import android.view.View;

import com.google.android.material.progressindicator.LinearProgressIndicator;

/** Binds an activity's top progress bar (R.id.top_progress) to the global
 *  SyncProgress: call onResume/onPause from the activity's own. The bar
 *  reflects the current sync state immediately, so it survives navigation. */
public class TopProgressBinder implements SyncProgress.Listener {

	private final Activity activity;
	private LinearProgressIndicator bar;

	public TopProgressBinder(Activity activity) {
		this.activity = activity;
	}

	public void onResume() {
		bar = (LinearProgressIndicator) activity.findViewById(R.id.top_progress);
		SyncProgress.addListener(this);
	}

	public void onPause() {
		SyncProgress.removeListener(this);
	}

	@Override
	public void onSyncProgress(int progress, int max, boolean indeterminate, boolean active) {
		if (bar == null) bar = (LinearProgressIndicator) activity.findViewById(R.id.top_progress);
		if (bar == null) return;
		if (!active) {
			bar.setVisibility(View.GONE);
			return;
		}
		bar.setIndeterminate(indeterminate);
		if (!indeterminate) {
			bar.setMax(Math.max(1, max));
			bar.setProgress(progress);
		}
		bar.setVisibility(View.VISIBLE);
	}
}
