package app.tvmovie.tracker;

import android.os.Handler;
import android.os.Looper;

import java.util.HashSet;
import java.util.Set;

/** App-wide sync/update progress. Any screen can drive it (DroidShows does)
 *  and any screen can display it by registering a Listener: the bar stays
 *  visible and keeps moving while the user navigates between activities,
 *  until the work finishes. All callbacks run on the main thread. */
public class SyncProgress {

	public interface Listener {
		void onSyncProgress(int progress, int max, boolean indeterminate, boolean active);
	}

	private static final Handler MAIN = new Handler(Looper.getMainLooper());
	private static final Set<Listener> LISTENERS = new HashSet<Listener>();
	private static boolean active = false;
	private static boolean indeterminate = true;
	private static int progress = 0;
	private static int max = 1;

	public static synchronized void show(boolean indeterminate, int max) {
		active = true;
		SyncProgress.indeterminate = indeterminate;
		SyncProgress.max = Math.max(1, max);
		progress = 0;
		dispatch();
	}

	public static synchronized void set(int progress) {
		SyncProgress.progress = progress;
		if (active) dispatch();
	}

	public static synchronized void hide() {
		active = false;
		dispatch();
	}

	public static synchronized void addListener(Listener l) {
		LISTENERS.add(l);
		final int p = progress, m = max;
		final boolean ind = indeterminate, a = active;
		MAIN.post(new Runnable() { public void run() {
			l.onSyncProgress(p, m, ind, a);
		}});
	}

	public static synchronized void removeListener(Listener l) {
		LISTENERS.remove(l);
	}

	private static void dispatch() {
		final int p = progress, m = max;
		final boolean ind = indeterminate, a = active;
		final Set<Listener> copy = new HashSet<Listener>(LISTENERS);
		MAIN.post(new Runnable() { public void run() {
			for (Listener l : copy) l.onSyncProgress(p, m, ind, a);
		}});
	}
}
