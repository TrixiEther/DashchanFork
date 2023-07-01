package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ThemeEngine;

public class PostBorderView extends View {
	private BorderStyle borderStyle;
	private BorderConfiguration borderConfiguration;

	public PostBorderView(Context context, @Nullable AttributeSet attrs) {
		super(context, attrs);
	}

	public void setBorderStyle(BorderStyle borderStyle) {
		if (this.borderStyle != borderStyle) {
			this.borderStyle = borderStyle;
			initializeBorderConfigurationForStyle();
			requestLayout();
		}
	}

	private void initializeBorderConfigurationForStyle() {
		if (borderStyle == null) {
			borderConfiguration = null;
		} else {
			switch (borderStyle) {
				case USER_POST:
					borderConfiguration = new UserPostBorderConfiguration(getContext());
					break;
				case REPLY:
					borderConfiguration = new ReplyBorderConfiguration(getContext());
					break;
				default:
					borderConfiguration = null;
			}
		}
	}

	@Override
	protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
		super.onMeasure(widthMeasureSpec, heightMeasureSpec);
		if (borderConfiguration != null) {
			int borderLeft = getPaddingLeft();
			int borderTop = getPaddingTop();
			int borderRight = getMeasuredWidth() - getPaddingRight();
			int borderBottom = getMeasuredHeight() - getPaddingBottom();
			borderConfiguration.configure(borderLeft, borderTop, borderRight, borderBottom);
		}
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (borderConfiguration != null) {
			borderConfiguration.draw(canvas);
		}
	}

	public enum BorderStyle {
		USER_POST, REPLY
	}

	private interface BorderConfiguration {
		void configure(int borderLeft, int borderTop, int borderRight, int borderBottom);

		void draw(Canvas canvas);
	}

	private static class UserPostBorderConfiguration implements BorderConfiguration {
		private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
		private float borderTop;
		private float borderBottom;
		private float borderMid;
		private final float density;

		UserPostBorderConfiguration(Context context) {
			paint.setColor(ThemeEngine.getTheme(context).accent);
			paint.setStrokeCap(Paint.Cap.ROUND);
			density = ResourceUtils.obtainDensity(context);
		}

		@Override
		public void configure(int borderLeft, int borderTop, int borderRight, int borderBottom) {
			float userPostBorderSize = borderRight - borderLeft - density;
			if (userPostBorderSize > 0) {
				paint.setStrokeWidth(userPostBorderSize);

				float borderCapSize = userPostBorderSize / 2;
				this.borderTop = borderTop + borderCapSize;
				this.borderBottom = borderBottom - borderCapSize;
				this.borderMid = (borderLeft + borderRight) / 2f;
			}
		}

		@Override
		public void draw(Canvas canvas) {
			canvas.drawLine(borderMid, borderTop, borderMid, borderBottom, paint);
		}

	}

	private static class ReplyBorderConfiguration implements BorderConfiguration {
		private final Paint paint = new Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
		private float dotRadius;
		private int dotsCount;
		private float spaceBetweenDots;
		private float dotCenterX;
		private float dotCenterY;

		ReplyBorderConfiguration(Context context) {
			paint.setColor(ThemeEngine.getTheme(context).accent);
		}

		@Override
		public void configure(int borderLeft, int borderTop, int borderRight, int borderBottom) {
			int dotDiameter = borderRight - borderLeft;
			if (dotDiameter > 0) {
				dotRadius = dotDiameter / 2f;

				int availableHeight = borderBottom - borderTop;
				dotsCount = (availableHeight / 2) / dotDiameter;

				spaceBetweenDots = 0;
				if (dotsCount >= 2) {
					int remainingHeight = availableHeight - (dotsCount * dotDiameter);
					spaceBetweenDots += dotDiameter;
					spaceBetweenDots += (float) remainingHeight / (dotsCount - 1); // the last dot does not need a space after it, so divide remaining height between all dots except last
				}

				dotCenterX = dotRadius;
				dotCenterY = borderTop + dotRadius;
			}
		}

		@Override
		public void draw(Canvas canvas) {
			for (int dotNumber = 0; dotNumber < dotsCount; dotNumber++) {
				canvas.drawCircle(dotCenterX, dotCenterY + (spaceBetweenDots * dotNumber), dotRadius, paint);
			}
		}
	}

}
