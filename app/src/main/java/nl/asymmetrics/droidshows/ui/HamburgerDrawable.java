package nl.asymmetrics.droidshows.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;

import androidx.appcompat.graphics.drawable.DrawerArrowDrawable;

/**
 * Drawer indicator that morphs between a clean three-line hamburger (drawer
 * closed) and an X (drawer open), driven by the drawer slide offset.
 * Replaces the stock hamburger-to-arrow animation — no arrow is drawn at
 * any point of the interaction.
 */
public class HamburgerDrawable extends DrawerArrowDrawable {

	public HamburgerDrawable(Context context) {
		super(context);
		// Round caps for a crisper, softer look than the stock butt caps.
		getPaint().setStrokeCap(Paint.Cap.ROUND);
	}

	@Override
	public void draw(Canvas canvas) {
		Rect bounds = getBounds();
		if (bounds.isEmpty())
			return;

		float progress = getProgress(); // 0 = hamburger, 1 = X
		float cx = bounds.centerX();
		float cy = bounds.centerY();
		float size = Math.min(bounds.width(), bounds.height());

		float barLength = getBarLength();
		if (barLength <= 0)
			barLength = size * 0.72f;
		float half = barLength / 2f;

		float thickness = getBarThickness();
		if (thickness <= 0)
			thickness = Math.max(2f, size / 12f);

		float gap = size * 0.17f; // centre-to-outer-bar distance when closed

		Paint paint = getPaint();
		paint.setStrokeWidth(thickness);

		// Top bar: slides to the centre and rotates to +45 degrees.
		canvas.save();
		canvas.translate(cx, cy - gap * (1f - progress));
		canvas.rotate(45f * progress);
		canvas.drawLine(-half, 0f, half, 0f, paint);
		canvas.restore();

		// Bottom bar: slides to the centre and rotates to -45 degrees.
		canvas.save();
		canvas.translate(cx, cy + gap * (1f - progress));
		canvas.rotate(-45f * progress);
		canvas.drawLine(-half, 0f, half, 0f, paint);
		canvas.restore();

		// Middle bar: fades (and shrinks) away as the X forms.
		int baseAlpha = paint.getAlpha();
		paint.setAlpha(Math.round(baseAlpha * (1f - progress)));
		float midHalf = half * (1f - progress);
		canvas.drawLine(cx - midHalf, cy, cx + midHalf, cy, paint);
		paint.setAlpha(baseAlpha);
	}
}
