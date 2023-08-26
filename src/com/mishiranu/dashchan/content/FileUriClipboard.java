package com.mishiranu.dashchan.content;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import com.mishiranu.dashchan.C;
import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.widget.ClickableToast;

import java.io.File;

public final class FileUriClipboard {

	private FileUriClipboard() {
	}

	public static void copyFileUriToClipboard(File file, String originalFileName) {
		Uri fileUri = CacheManager.getInstance().prepareFileForClipboard(file, originalFileName);
		if (fileUri == null) {
			ClickableToast.show(R.string.cache_is_unavailable);
			return;
		}
		Context context = MainApplication.getInstance();
		ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
		ContentResolver contentResolver = context.getContentResolver();
		ClipData clip = ClipData.newUri(contentResolver, originalFileName, fileUri);
		clipboard.setPrimaryClip(clip);
		if (!C.API_S_V2) {
			ClickableToast.show(R.string.copied_to_clipboard);
		}
	}

}

