package com.mishiranu.dashchan.ui;

import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.ConsoleMessage;
import android.webkit.GeolocationPermissions;
import android.webkit.JsPromptResult;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.fragment.app.DialogFragment;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;


public abstract class WebViewDialog extends DialogFragment {
	protected WebView webView;
	protected TextView titleTextView;
	protected ProgressBar pageLoadingProgressBar;

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		Dialog dialog = super.onCreateDialog(savedInstanceState);
		dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		return dialog;
	}

	@Nullable
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View rootView = inflater.inflate(R.layout.dialog_webview, container);
		webView = rootView.findViewById(R.id.dialog_webview_webview);
		titleTextView = rootView.findViewById(R.id.dialog_webview_title);
		pageLoadingProgressBar = rootView.findViewById(R.id.dialog_webview_progressbar);

		webView.setWebChromeClient(new WebChromeClientWrapper());

		View closeIcon = rootView.findViewById(R.id.dialog_webview_icon_close);
		closeIcon.setOnClickListener((v -> dismiss()));

		View refreshIcon = rootView.findViewById(R.id.dialog_webview_icon_refresh);
		refreshIcon.setOnClickListener((v -> webView.reload()));

		return rootView;
	}

	@Override
	public void onStart() {
		super.onStart();
		Dialog dialog = getDialog();
		if (dialog != null) {
			dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
		}
	}

	protected void setWebChromeClient(WebChromeClient webChromeClient) {
		webView.setWebChromeClient(new WebChromeClientWrapper(webChromeClient));
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		webView.stopLoading();
		webView.setWebChromeClient(null);
		webView.destroy();
	}

	private class WebChromeClientWrapper extends WebChromeClient {
		private final WebChromeClient delegate;
		private final ObjectAnimator progressBarVisibilityAnimator = ObjectAnimator.ofFloat(pageLoadingProgressBar, "alpha", 1f);
		private int lastProgress = 0;

		WebChromeClientWrapper(){
			delegate = new WebChromeClient();
		}

		WebChromeClientWrapper(WebChromeClient delegate){
			this.delegate = delegate;
		}

		@Override
		public void onProgressChanged(WebView view, int newProgress) {
			delegate.onProgressChanged(view, newProgress);
			animateProgressBarVisibility(newProgress);
			if (C.API_NOUGAT) {
				pageLoadingProgressBar.setProgress(newProgress, true);
			} else {
				pageLoadingProgressBar.setProgress(newProgress);
			}
			lastProgress = newProgress;
		}

		private void animateProgressBarVisibility(int newProgress) {
			boolean animateHide = newProgress == 100 && lastProgress != 100;
			boolean animateShow = lastProgress == 0 || (newProgress != 100 && lastProgress == 100);
			boolean animate = animateHide || animateShow;
			if (animate) {
				if (progressBarVisibilityAnimator.isRunning()) {
					progressBarVisibilityAnimator.cancel();
				}
				float finalAlphaValue;
				int animationDurationMillis;
				if (animateHide) {
					finalAlphaValue = 0;
					animationDurationMillis = 200;
				} else {
					finalAlphaValue = 1;
					animationDurationMillis = 250;
				}
				progressBarVisibilityAnimator.setFloatValues(finalAlphaValue);
				progressBarVisibilityAnimator.setDuration(animationDurationMillis);
				progressBarVisibilityAnimator.start();
			}
		}

		@Override
		public void onReceivedTitle(WebView view, String title) {
			delegate.onReceivedTitle(view, title);
			titleTextView.setText(title);
		}

		@Override
		public void onReceivedIcon(WebView view, Bitmap icon) {
			delegate.onReceivedIcon(view, icon);
		}

		@Override
		public void onReceivedTouchIconUrl(WebView view, String url, boolean precomposed) {
			delegate.onReceivedTouchIconUrl(view, url, precomposed);
		}

		@Override
		public void onShowCustomView(View view, CustomViewCallback callback) {
			delegate.onShowCustomView(view, callback);
		}

		@Override
		@Deprecated
		public void onShowCustomView(View view, int requestedOrientation, CustomViewCallback callback) {
			delegate.onShowCustomView(view, requestedOrientation, callback);
		}

		@Override
		public void onHideCustomView() {
			delegate.onHideCustomView();
		}

		@Override
		public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
			return delegate.onCreateWindow(view, isDialog, isUserGesture, resultMsg);
		}

		@Override
		public void onRequestFocus(WebView view) {
			delegate.onRequestFocus(view);
		}

		@Override
		public void onCloseWindow(WebView window) {
			delegate.onCloseWindow(window);
		}

		@Override
		public boolean onJsAlert(WebView view, String url, String message, JsResult result) {
			return delegate.onJsAlert(view, url, message, result);
		}

		@Override
		public boolean onJsConfirm(WebView view, String url, String message, JsResult result) {
			return delegate.onJsConfirm(view, url, message, result);
		}

		@Override
		public boolean onJsPrompt(WebView view, String url, String message, String defaultValue, JsPromptResult result) {
			return delegate.onJsPrompt(view, url, message, defaultValue, result);
		}

		@Override
		public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
			return delegate.onJsBeforeUnload(view, url, message, result);
		}

		@Override
		@Deprecated
		public void onExceededDatabaseQuota(String url, String databaseIdentifier, long quota, long estimatedDatabaseSize, long totalQuota, WebStorage.QuotaUpdater quotaUpdater) {
			delegate.onExceededDatabaseQuota(url, databaseIdentifier, quota, estimatedDatabaseSize, totalQuota, quotaUpdater);
		}

		@Override
		@Deprecated
		public void onReachedMaxAppCacheSize(long requiredStorage, long quota, WebStorage.QuotaUpdater quotaUpdater) {
			delegate.onReachedMaxAppCacheSize(requiredStorage, quota, quotaUpdater);
		}

		@Override
		public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
			delegate.onGeolocationPermissionsShowPrompt(origin, callback);
		}

		@Override
		public void onGeolocationPermissionsHidePrompt() {
			delegate.onGeolocationPermissionsHidePrompt();
		}

		@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
		@Override
		public void onPermissionRequest(PermissionRequest request) {
			delegate.onPermissionRequest(request);
		}

		@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
		@Override
		public void onPermissionRequestCanceled(PermissionRequest request) {
			delegate.onPermissionRequestCanceled(request);
		}

		@Override
		@Deprecated
		public boolean onJsTimeout() {
			return delegate.onJsTimeout();
		}

		@Override
		public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
			return delegate.onConsoleMessage(consoleMessage);
		}

		@Nullable
		@Override
		public Bitmap getDefaultVideoPoster() {
			return delegate.getDefaultVideoPoster();
		}

		@Nullable
		@Override
		public View getVideoLoadingProgressView() {
			return delegate.getVideoLoadingProgressView();
		}

		@Override
		public void getVisitedHistory(ValueCallback<String[]> callback) {
			delegate.getVisitedHistory(callback);
		}

		@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
		@Override
		public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
			return delegate.onShowFileChooser(webView, filePathCallback, fileChooserParams);
		}

	}

}
