package com.mishiranu.dashchan.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.ResourceUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ImportantPostsMarksFastScrollBarDecoration {
	private final Paint userPostMarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final Paint replyMarkPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private final float postMarkMinSize;
	private Data data;

	public ImportantPostsMarksFastScrollBarDecoration(Context context) {
		postMarkMinSize = ResourceUtils.obtainDensity(context);

		int userPostMarkColor = ResourceUtils.getColor(context, R.attr.colorPostMarkUserPost);
		userPostMarkPaint.setColor(userPostMarkColor);

		int replyMarkColor = ResourceUtils.getColor(context, R.attr.colorPostMarkReply);
		replyMarkPaint.setColor(replyMarkColor);
	}

	public void setData(Data data) {
		this.data = data;
	}

	public boolean hasMarks() {
		return data != null && data.totalPostsCount > 0 && !(data.userPostsPositions.isEmpty() && data.repliesPositions.isEmpty());
	}

	public void draw(int scrollBarLeft, int scrollBarTop, int scrollBarRight, int scrollBarBottom, Canvas canvas) {
		if (data != null && data.totalPostsCount > 0) {
			int scrollBarHeight = scrollBarBottom - scrollBarTop;
			float postMarkRealHeight = scrollBarHeight / (float) data.totalPostsCount;
			drawPostMarks(data.userPostsPositions, userPostMarkPaint, postMarkRealHeight, scrollBarLeft, scrollBarTop, scrollBarRight, canvas);
			drawPostMarks(data.repliesPositions, replyMarkPaint, postMarkRealHeight, scrollBarLeft, scrollBarTop, scrollBarRight, canvas);
		}
	}

	private void drawPostMarks(List<Integer> postPositions, Paint postMarkPaint, float postMarkRealHeight, int scrollBarLeft, int scrollBarTop, int scrollBarRight, Canvas canvas) {
		int lastPostPositionIndex = postPositions.size() - 1;
		int postPositionIndex = 0;
		while (postPositionIndex <= lastPostPositionIndex) {
			int postPosition = postPositions.get(postPositionIndex++);
			float postMarkTop = scrollBarTop + (postMarkRealHeight * postPosition);
			float postMarkBottom = postMarkTop + postMarkRealHeight;

			int contiguousPostMarks = 1;
			while (postPositionIndex < lastPostPositionIndex - 1) {
				int nextPostPosition = postPositions.get(postPositionIndex);
				boolean postPositionsAreContiguous = postPosition + 1 == nextPostPosition;
				if (postPositionsAreContiguous) {
					postMarkBottom += postMarkRealHeight;
					contiguousPostMarks++;
					postPositionIndex++;
				} else {
					break;
				}
			}

			// real post mark can be too thin in very long threads, in this case increase its height to postMarkMinHeight for better visibility
			float postMarkHeight = postMarkRealHeight * contiguousPostMarks;
			float postMarkMinHeight = postMarkMinSize * contiguousPostMarks;
			if (postMarkHeight < postMarkMinHeight) {
				float heightDifference = postMarkMinHeight - postMarkHeight;
				postMarkHeight += heightDifference;
				postMarkTop -= heightDifference / 2;
				postMarkBottom += heightDifference / 2;
			}

			// align a mark to its center
			postMarkTop -= postMarkHeight / 2;
			postMarkTop += postMarkHeight / 2;

			canvas.drawRect(scrollBarLeft, postMarkTop, scrollBarRight, postMarkBottom, postMarkPaint);
		}
	}

	public static class Data {
		private final List<Integer> userPostsPositions = new ArrayList<>();
		private final List<Integer> repliesPositions = new ArrayList<>();
		private final int totalPostsCount;

		public Data(Set<Integer> userPostsPositions, Set<Integer> repliesPositions, int totalPostsCount) {
			this.totalPostsCount = totalPostsCount;

			this.userPostsPositions.addAll(userPostsPositions);
			Collections.sort(this.userPostsPositions);

			for (Integer replyPosition : repliesPositions) {
				boolean userPostAtPosition = Collections.binarySearch(this.userPostsPositions, replyPosition) >= 0;
				if (!userPostAtPosition) {
					this.repliesPositions.add(replyPosition);
				}
			}
			Collections.sort(this.repliesPositions);
		}
	}
}

