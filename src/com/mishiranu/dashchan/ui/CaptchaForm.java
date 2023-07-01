package com.mishiranu.dashchan.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.SystemClock;
import android.text.InputFilter;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.view.MarginLayoutParamsCompat;
import androidx.core.view.ViewCompat;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.Preferences;
import com.mishiranu.dashchan.content.async.ReadCaptchaTask;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.util.ViewUtils;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import chan.content.ChanConfiguration;

public class CaptchaForm implements View.OnClickListener, View.OnLongClickListener, TextView.OnEditorActionListener {
	public enum CaptchaViewType {LOADING, IMAGE, SKIP, SKIP_LOCK, ERROR}

	private final Callback callback;
	private final boolean hideInput;
	private final boolean applyHeight;
	private final View blockParentView;
	private final View blockView;
	private final View skipBlockView;
	private final TextView skipTextView;
	private final View loadingView;
	private final ImageView imageView;
	private final View inputParentView;
	private final EditText inputView;
	private final ImageView cancelView;
	private final TextView lifetimeTimerView;

	private final boolean captchaLifetimeTimerEnabled;
	private int captchaLifetimeSeconds;
	private Bitmap captchaImage;

	private ChanConfiguration.Captcha.Input captchaInput;

	public interface Callback {
		void onRefreshCaptcha(boolean forceRefresh);

		void onConfirmCaptcha();

		void onCaptchaLifetimeEnded();

		void showCaptchaOptionsDialog(CaptchaOptionsDialog dialog);
	}

	public static class Captcha implements Parcelable {
		private final Bitmap image;
		private final int lifetimeSeconds;
		private final long creationTimeMillis;

		public Captcha(Bitmap image, int lifetimeSeconds) {
			this.image = image;
			this.lifetimeSeconds = Math.max(0, lifetimeSeconds);
			creationTimeMillis = SystemClock.elapsedRealtime();
		}

		protected Captcha(Parcel in) {
			image = in.readParcelable(Bitmap.class.getClassLoader());
			lifetimeSeconds = in.readInt();
			creationTimeMillis = in.readLong();
		}

		public long getCreationTimeMillis() {
			return creationTimeMillis;
		}

		public boolean alive() {
			if (hasLifetime()) {
				return getRemainingLifetimeSeconds() > 0;
			} else {
				return true;
			}
		}

		private int getRemainingLifetimeSeconds() {
			if (hasLifetime()) {
				long now = SystemClock.elapsedRealtime();
				int secondsPassedSinceCaptchaCreation = (int) TimeUnit.MILLISECONDS.toSeconds(now - creationTimeMillis);
				return Math.max(0, lifetimeSeconds - secondsPassedSinceCaptchaCreation);
			} else {
				return 0;
			}
		}

		public boolean hasLifetime() {
			return lifetimeSeconds > 0;
		}

		@Override
		public int describeContents() {
			return 0;
		}

		public Bitmap getImage() {
			return image;
		}

		@Override
		public void writeToParcel(Parcel dest, int flags) {
			dest.writeParcelable(image, flags);
			dest.writeInt(lifetimeSeconds);
			dest.writeLong(creationTimeMillis);
		}

		public static final Creator<Captcha> CREATOR = new Creator<Captcha>() {
			@Override
			public Captcha createFromParcel(Parcel in) {
				return new Captcha(in);
			}

			@Override
			public Captcha[] newArray(int size) {
				return new Captcha[size];
			}
		};
	}

	public CaptchaForm(Callback callback, boolean hideInput, boolean applyHeight,
					   View container, View inputParentView, EditText inputView, ChanConfiguration.Captcha captcha) {
		this.callback = callback;
		this.hideInput = hideInput;
		this.applyHeight = applyHeight;
		blockParentView = container.findViewById(R.id.captcha_block_parent);
		blockView = container.findViewById(R.id.captcha_block);
		imageView = container.findViewById(R.id.captcha_image);
		loadingView = container.findViewById(R.id.captcha_loading);
		skipBlockView = container.findViewById(R.id.captcha_skip_block);
		skipTextView = container.findViewById(R.id.captcha_skip_text);
		cancelView = container.findViewById(R.id.captcha_cancel);
		lifetimeTimerView = container.findViewById(R.id.captcha_lifetime_timer);
		captchaLifetimeTimerEnabled = Preferences.isHugeCaptcha() && Preferences.isCaptchaTimer();
		this.inputParentView = inputParentView;
		this.inputView = inputView;
		if (hideInput) {
			inputView.setVisibility(View.GONE);
		}
		if (C.API_LOLLIPOP) {
			cancelView.setImageTintList(ResourceUtils.getColorStateList(cancelView.getContext(),
					android.R.attr.textColorPrimary));
			skipTextView.setAllCaps(true);
			skipTextView.setTypeface(ResourceUtils.TYPEFACE_MEDIUM);
			ViewUtils.setTextSizeScaled(skipTextView, 12);
		}
		updateCaptchaHeight(false);
		captchaInput = captcha.input;
		if (captchaInput == null) {
			captchaInput = ChanConfiguration.Captcha.Input.ALL;
		}
		updateCaptchaInput(captchaInput);
		inputView.setFilters(new InputFilter[]{new InputFilter.LengthFilter(50)});
		inputView.setOnEditorActionListener(this);
		cancelView.setOnClickListener(this);
		blockParentView.setOnClickListener(this);
		blockParentView.setOnLongClickListener(this);
		if (inputParentView != null) {
			inputParentView.setOnClickListener(this);
		}
	}

	private void updateCaptchaInput(ChanConfiguration.Captcha.Input input) {
		switch (input) {
			case ALL: {
				inputView.setInputType(InputType.TYPE_CLASS_TEXT);
				break;
			}
			case LATIN: {
				inputView.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
				break;
			}
			case NUMERIC: {
				inputView.setInputType(InputType.TYPE_CLASS_NUMBER);
				break;
			}
		}
	}

	private void updateCaptchaHeight(boolean large) {
		if (applyHeight) {
			float density = ResourceUtils.obtainDensity(inputView);
			int height = (int) ((Preferences.isHugeCaptcha() || large ? 96f : 48f) * density);
			ViewGroup.LayoutParams layoutParams = blockView.getLayoutParams();
			if (height != layoutParams.height) {
				layoutParams.height = height;
				blockView.requestLayout();
			}
		}
	}

	@Override
	public void onClick(View v) {
		if (v == cancelView) {
			callback.onRefreshCaptcha(true);
		} else if (v == blockParentView && v.isClickable()) {
			callback.onRefreshCaptcha(false);
		} else if (inputParentView != null && v == inputParentView) {
			inputView.requestFocus();
			InputMethodManager inputMethodManager = (InputMethodManager) v.getContext()
					.getSystemService(Context.INPUT_METHOD_SERVICE);
			if (inputMethodManager != null) {
				inputMethodManager.showSoftInput(inputView, InputMethodManager.SHOW_IMPLICIT);
			}
		}
	}

	@Override
	public boolean onLongClick(View v) {
		if (v == blockParentView) {
			if (captchaImage != null) {
				callback.showCaptchaOptionsDialog(new CaptchaOptionsDialog(captchaImage));
			} else {
				callback.onRefreshCaptcha(true);
			}
			return true;
		}
		return false;
	}

	@Override
	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		callback.onConfirmCaptcha();
		return true;
	}

	public void showCaptcha(ReadCaptchaTask.CaptchaState captchaState, ChanConfiguration.Captcha.Input input,
							Captcha captcha, boolean large, boolean invertColors) {
		switch (captchaState) {
			case CAPTCHA: {
				if (captcha != null) {
					if (captchaLifetimeTimerEnabled && !captcha.alive()) {
						callback.onCaptchaLifetimeEnded();
					} else {
						captchaImage = captcha.image;
						imageView.setImageBitmap(captcha.image);
						imageView.setColorFilter(invertColors ? GraphicsUtils.INVERT_FILTER : null);
						captchaLifetimeSeconds = captcha.getRemainingLifetimeSeconds();
						switchToCaptchaView(CaptchaViewType.IMAGE, input, large);
					}
				} else {
					showError();
				}
				break;
			}
			case NEED_LOAD:
			case MAY_LOAD:
			case MAY_LOAD_SOLVING: {
				skipTextView.setText(R.string.load_captcha);
				cancelView.setVisibility(captchaState == ReadCaptchaTask.CaptchaState.MAY_LOAD_SOLVING
						? View.VISIBLE : View.GONE);
				switchToCaptchaView(CaptchaViewType.SKIP, null, false);
				break;
			}
			case SKIP: {
				skipTextView.setText(R.string.captcha_is_not_required);
				cancelView.setVisibility(View.VISIBLE);
				switchToCaptchaView(CaptchaViewType.SKIP_LOCK, null, false);
				break;
			}
			case PASS: {
				skipTextView.setText(R.string.captcha_pass);
				cancelView.setVisibility(View.VISIBLE);
				switchToCaptchaView(CaptchaViewType.SKIP, null, false);
				break;
			}
		}
	}

	public void showError() {
		imageView.setImageResource(android.R.color.transparent);
		skipTextView.setText(R.string.load_captcha);
		cancelView.setVisibility(View.GONE);
		switchToCaptchaView(CaptchaViewType.ERROR, null, false);
	}

	public void showLoading() {
		inputView.setText(null);
		switchToCaptchaView(CaptchaViewType.LOADING, null, false);
	}

	public void setText(String text) {
		inputView.setText(text);
	}

	public String getInput() {
		return inputView.getText().toString();
	}

	private void setInputEnabled(boolean enabled, boolean switchVisibility) {
		inputView.setEnabled(enabled);
		if (hideInput && switchVisibility) {
			inputView.setVisibility(enabled ? View.VISIBLE : View.GONE);
		}
	}

	private void switchToCaptchaView(CaptchaViewType captchaViewType,
									 ChanConfiguration.Captcha.Input input, boolean large) {
		if (captchaViewType != CaptchaViewType.IMAGE) {
			hideCaptchaLifetimeTimer();
			stopCaptchaLifetimeTimer();
			captchaImage = null;
		}

		switch (captchaViewType) {
			case LOADING: {
				blockParentView.setClickable(true);
				blockView.setVisibility(View.VISIBLE);
				imageView.setVisibility(View.GONE);
				imageView.setImageResource(android.R.color.transparent);
				loadingView.setVisibility(View.VISIBLE);
				skipBlockView.setVisibility(View.GONE);
				setInputEnabled(false, false);
				updateCaptchaHeight(false);
				break;
			}
			case ERROR: {
				blockParentView.setClickable(true);
				blockView.setVisibility(View.VISIBLE);
				imageView.setVisibility(View.VISIBLE);
				loadingView.setVisibility(View.GONE);
				skipBlockView.setVisibility(View.VISIBLE);
				setInputEnabled(false, false);
				updateCaptchaHeight(false);
				break;
			}
			case IMAGE: {
				blockParentView.setClickable(true);
				blockView.setVisibility(View.VISIBLE);
				imageView.setVisibility(View.VISIBLE);
				loadingView.setVisibility(View.GONE);
				skipBlockView.setVisibility(View.GONE);
				setInputEnabled(true, true);
				updateCaptchaInput(input != null ? input : captchaInput);
				updateCaptchaHeight(large);
				if (captchaLifetimeTimerAvailable()) {
					startCaptchaLifetimeTimer();
					showCaptchaLifetimeTimer();
				} else {
					hideCaptchaLifetimeTimer();
				}
				break;
			}
			case SKIP:
			case SKIP_LOCK: {
				blockParentView.setClickable(captchaViewType != CaptchaViewType.SKIP_LOCK);
				blockView.setVisibility(View.INVISIBLE);
				imageView.setVisibility(View.VISIBLE);
				imageView.setImageResource(android.R.color.transparent);
				loadingView.setVisibility(View.GONE);
				skipBlockView.setVisibility(View.VISIBLE);
				setInputEnabled(false, true);
				updateCaptchaHeight(false);
				break;
			}
		}
	}

	private boolean captchaLifetimeTimerAvailable() {
		return captchaLifetimeTimerEnabled && captchaLifetimeSeconds > 0 && lifetimeTimerView != null;
	}

	private void showCaptchaLifetimeTimer() {
		if (!ViewCompat.isLaidOut(imageView)) {
			showCaptchaLifetimeTimerWhenImageViewIsLaidOut();
			return;
		}
		alignCaptchaLifetimeTimerWithCaptchaImage();
		lifetimeTimerView.setVisibility(View.VISIBLE);
	}

	private void showCaptchaLifetimeTimerWhenImageViewIsLaidOut() {
		imageView.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
			@Override
			public void onGlobalLayout() {
				imageView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
				showCaptchaLifetimeTimer();
			}
		});
	}

	private void alignCaptchaLifetimeTimerWithCaptchaImage() {
		int captchaImageRealWidth = getCaptchaImageRealWidth();
		if (captchaImageRealWidth > 0) {
			int captchaImageViewWidth = imageView.getWidth();
			int lifetimeTimerViewEndMargin = (captchaImageViewWidth - captchaImageRealWidth) / 2;
			FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) lifetimeTimerView.getLayoutParams();
			MarginLayoutParamsCompat.setMarginEnd(params, lifetimeTimerViewEndMargin);
			lifetimeTimerView.setLayoutParams(params);
		}
	}

	private int getCaptchaImageRealWidth() {
		Drawable captchaImageDrawable = imageView.getDrawable();
		if (captchaImageDrawable == null) {
			return 0;
		}

		int captchaImageWidth = captchaImageDrawable.getIntrinsicWidth();
		float[] imageMatrix = new float[9];
		imageView.getImageMatrix().getValues(imageMatrix);

		return Math.round(captchaImageWidth * imageMatrix[Matrix.MSCALE_X]);
	}

	private void hideCaptchaLifetimeTimer() {
		if (lifetimeTimerView != null) {
			lifetimeTimerView.setVisibility(View.GONE);
		}
	}

	private final Runnable captchaLifetimeUpdateRunnable = new Runnable() {
		@Override
		public void run() {
			boolean captchaAlive = captchaLifetimeSeconds > 0;
			if (captchaAlive) {
				lifetimeTimerView.setText(String.format(Locale.getDefault(), "%d", captchaLifetimeSeconds--));
				ConcurrentUtils.HANDLER.postDelayed(this, 1000);
			} else {
				callback.onCaptchaLifetimeEnded();
			}
		}
	};

	public void onDestroyView() {
		stopCaptchaLifetimeTimer();
	}

	private void startCaptchaLifetimeTimer() {
		captchaLifetimeUpdateRunnable.run();
	}

	private void stopCaptchaLifetimeTimer() {
		ConcurrentUtils.HANDLER.removeCallbacks(captchaLifetimeUpdateRunnable);
	}

}
