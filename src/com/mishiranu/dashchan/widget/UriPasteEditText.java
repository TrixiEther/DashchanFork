package com.mishiranu.dashchan.widget;

import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

import androidx.core.content.MimeTypeFilter;
import androidx.core.view.inputmethod.EditorInfoCompat;
import androidx.core.view.inputmethod.InputConnectionCompat;

import com.mishiranu.dashchan.R;

import java.util.List;

public class UriPasteEditText extends SafePasteEditText implements ActionMode.Callback {
	private Callback callback;
	private String[] allowedUriMimeTypes;
	private ActionMode actionMode;
	private ActionMode.Callback actionModeCallbackDelegate;

	public interface Callback {
		void onUriWithAllowedMimeTypePasted(Uri uri);
	}

	public UriPasteEditText(Context context) {
		super(context);
	}

	public UriPasteEditText(Context context, AttributeSet attrs) {
		super(context, attrs);
	}

	public UriPasteEditText(Context context, AttributeSet attrs, int defStyleAttr) {
		super(context, attrs, defStyleAttr);
	}

	@SuppressWarnings("unused")
	@TargetApi(Build.VERSION_CODES.LOLLIPOP)
	public UriPasteEditText(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);
	}

	public void setCallback(Callback callback, List<String> allowedUriMimeTypes) {
		this.callback = callback;
		this.allowedUriMimeTypes = allowedUriMimeTypes.toArray(new String[0]);
	}

	@Override
	public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
		InputConnection inputConnection = super.onCreateInputConnection(outAttrs);
		if (uriPasteAllowed()) {
			EditorInfoCompat.setContentMimeTypes(outAttrs, allowedUriMimeTypes);

			InputConnectionCompat.OnCommitContentListener cb = (inputContentInfo, flags, opts) -> {
				inputContentInfo.requestPermission();
				callback.onUriWithAllowedMimeTypePasted(inputContentInfo.getContentUri());
				inputContentInfo.releasePermission();
				ClickableToast.show(R.string.pasted);
				return true;
			};

			inputConnection = InputConnectionCompat.createWrapper(inputConnection, outAttrs, cb);
		}
		return inputConnection;
	}

	@Override
	public boolean onTextContextMenuItem(int id) {
		if (id == android.R.id.paste && uriPasteAllowed()) {
			Uri uriFromClipboard = getUriFromClipboard();
			if (uriFromClipboard != null && uriMimeTypeAllowed(uriFromClipboard)) {
				callback.onUriWithAllowedMimeTypePasted(uriFromClipboard);
				cancelActionMode();
				return true;
			}
		}
		return super.onTextContextMenuItem(id);
	}

	private boolean uriPasteAllowed() {
		return callback != null && allowedUriMimeTypes != null && allowedUriMimeTypes.length != 0;
	}

	private Uri getUriFromClipboard() {
		ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
		ClipData clip = clipboardManager.getPrimaryClip();
		if (clip != null) {
			return clip.getItemAt(0).getUri();
		} else {
			return null;
		}
	}

	private boolean uriMimeTypeAllowed(Uri uri) {
		ContentResolver contentResolver = getContext().getContentResolver();
		String uriMimeType = contentResolver.getType(uri);
		return MimeTypeFilter.matches(uriMimeType, allowedUriMimeTypes) != null;
	}

	private void cancelActionMode() {
		if (actionMode != null) {
			actionMode.finish();
		}
	}

	@Override
	public ActionMode startActionMode(ActionMode.Callback callback) {
		actionModeCallbackDelegate = callback;
		actionMode = super.startActionMode(this);
		return actionMode;
	}

	@Override
	public ActionMode startActionMode(ActionMode.Callback callback, int type) {
		actionModeCallbackDelegate = callback;
		actionMode = super.startActionMode(this, type);
		return actionMode;
	}

	@Override
	public boolean onCreateActionMode(ActionMode mode, Menu menu) {
		return actionModeCallbackDelegate.onCreateActionMode(mode, menu);
	}

	@Override
	public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
		return actionModeCallbackDelegate.onPrepareActionMode(mode, menu);
	}

	@Override
	public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
		return actionModeCallbackDelegate.onActionItemClicked(mode, item);
	}

	@Override
	public void onDestroyActionMode(ActionMode mode) {
		actionModeCallbackDelegate.onDestroyActionMode(mode);
		actionMode = null;
		actionModeCallbackDelegate = null;
	}

}
