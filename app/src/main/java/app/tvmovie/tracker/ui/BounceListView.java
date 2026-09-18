package app.tvmovie.tracker.ui;

import app.tvmovie.tracker.DroidShows;
import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.ListView;

public class BounceListView extends ListView {
	private static final int MAX_OVERSCROLL_DISTANCE = 70;
	private int maxOverScrollDistance;
	private Context context;
	private float startY;
	private boolean allowOverScroll = false;
	public boolean gettingNextLogged = false;

	public BounceListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		this.context = context;
		final float density = context.getResources().getDisplayMetrics().density;
		maxOverScrollDistance = (int) density * MAX_OVERSCROLL_DISTANCE;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		switch(event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				startY = event.getY();
				break;
			case MotionEvent.ACTION_MOVE:
				if (DroidShows.logMode)
					allowOverScroll = event.getY() < startY && getAdapter().getCount() - 1 == getLastVisiblePosition();
				else
					allowOverScroll = event.getY() > startY && getChildCount() > 0 && getChildAt(0).getTop() == 0 && getFirstVisiblePosition() == 0;
				break;
			case MotionEvent.ACTION_UP:
				allowOverScroll = false;
		}
		return super.onTouchEvent(event);
	}

	@Override
	protected void onOverScrolled(int scrollX, int scrollY, boolean clampedX, boolean clampedY) {
		// Log-mode pagination only. The old overscroll-to-sync gesture is gone:
		// pull-to-refresh was removed, sync now lives in the + popup menu.
		if (DroidShows.logMode && !gettingNextLogged && allowOverScroll) {
			gettingNextLogged = true;
			((DroidShows)context).getNextLogged();
		}
		super.onOverScrolled(scrollX, scrollY, clampedX, clampedY);
	}

	@Override
	protected boolean overScrollBy(int deltaX, int deltaY, int scrollX, int scrollY, int scrollRangeX, int scrollRangeY, int maxOverScrollX, int maxOverScrollY, boolean isTouchEvent) {
		if (allowOverScroll)
			maxOverScrollY = maxOverScrollDistance;
		return super.overScrollBy(deltaX, deltaY, scrollX, scrollY, scrollRangeX, scrollRangeY, maxOverScrollX, maxOverScrollY, isTouchEvent);
	}
}
