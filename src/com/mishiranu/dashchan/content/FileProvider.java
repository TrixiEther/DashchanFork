package com.mishiranu.dashchan.content;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import androidx.annotation.NonNull;

import com.mishiranu.dashchan.C;

import java.io.File;
import java.io.FileNotFoundException;

import chan.util.CommonUtils;

public class FileProvider extends ContentProvider {
	private static final String AUTHORITY = "com.mishiranu.providers.dashchan";
	private static final String PATH_UPDATES = "updates";
	private static final String PATH_DOWNLOADS = "downloads";
	private static final String PATH_SHARE = "share";
	private static final String PATH_CLIPBOARD = "clipboard";

	private static final int URI_MATCHER_CODE_UPDATES = 1;
	private static final int URI_MATCHER_CODE_DOWNLOADS = 2;
	private static final int URI_MATCHER_CODE_SHARE = 3;
	private static final int URI_MATCHER_CODE_CLIPBOARD = 4;

	private static final UriMatcher URI_MATCHER;

	static {
		URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
		URI_MATCHER.addURI(AUTHORITY, PATH_UPDATES + "/*", URI_MATCHER_CODE_UPDATES);
		URI_MATCHER.addURI(AUTHORITY, PATH_DOWNLOADS + "/*", URI_MATCHER_CODE_DOWNLOADS);
		URI_MATCHER.addURI(AUTHORITY, PATH_SHARE + "/*", URI_MATCHER_CODE_SHARE);
		URI_MATCHER.addURI(AUTHORITY, PATH_CLIPBOARD + "/*", URI_MATCHER_CODE_CLIPBOARD);
	}

	@Override
	public boolean onCreate() {
		return true;
	}

	public static File getUpdatesDirectory() {
		File directory = MainApplication.getInstance().getExternalCacheDir();
		if (directory == null) {
			return null;
		}
		directory = new File(directory, "updates");
		directory.mkdirs();
		return directory;
	}

	public static File getUpdatesFile(String name) {
		File directory = getUpdatesDirectory();
		if (directory != null) {
			File file = new File(directory, name);
			if (file.exists()) {
				return file;
			}
		}
		return null;
	}

	public static Uri convertUpdatesUri(Uri uri) {
		if (C.API_NOUGAT && "file".equals(uri.getScheme())) {
			File fileParent = new File(uri.getPath()).getParentFile();
			File directory = getUpdatesDirectory();
			if (fileParent != null && fileParent.equals(directory)) {
				return new Uri.Builder().scheme("content").authority(AUTHORITY)
						.appendPath(PATH_UPDATES).appendPath(uri.getLastPathSegment()).build();
			}
		}
		return uri;
	}

	private static class InternalFile {
		public final File file;
		public final String type;
		public final Uri uri;

		public InternalFile(File file, String type, Uri uri) {
			this.file = file;
			this.uri = uri;
			this.type = type;
		}
	}

	private static InternalFile downloadsFile;
	private static InternalFile shareFile;
	private static InternalFile clipboardFile;

	public static Uri convertDownloadsLegacyFile(File file, String type) {
		return convertToInternalFile(Preferences.getDownloadDirectoryLegacy(), file, type, PATH_DOWNLOADS, URI_MATCHER_CODE_DOWNLOADS);
	}

	public static Uri convertShareFile(File directory, File file, String type) {
		return convertToInternalFile(directory, file, type, PATH_SHARE, URI_MATCHER_CODE_SHARE);
	}

	public static Uri convertClipboardFile(File directory, File file, String type) {
		return convertToInternalFile(directory, file, type, PATH_CLIPBOARD, URI_MATCHER_CODE_CLIPBOARD);
	}

	private static Uri convertToInternalFile(File directory, File file, String type, String path, int uriMatcherCode) {
		InternalFile internalFile = createInternalFile(directory, file, type, path);
		if (internalFile != null) {
			switch (uriMatcherCode) {
				case URI_MATCHER_CODE_DOWNLOADS: {
					downloadsFile = internalFile;
					break;
				}
				case URI_MATCHER_CODE_SHARE: {
					shareFile = internalFile;
					break;
				}
				case URI_MATCHER_CODE_CLIPBOARD: {
					clipboardFile = internalFile;
					break;
				}
				default: {
					throw new IllegalArgumentException("No internal file for uri matcher code: " + uriMatcherCode);
				}
			}
			return internalFile.uri;
		}
		return Uri.fromFile(file);
	}

	private static InternalFile createInternalFile(File directory, File file, String type, String providerPath) {
		if (C.API_NOUGAT) {
			String filePath = file.getAbsolutePath();
			String directoryPath = directory.getAbsolutePath();
			if (filePath.startsWith(directoryPath)) {
				filePath = filePath.substring(directoryPath.length());
				if (filePath.startsWith("/")) {
					filePath = filePath.substring(1);
				}
				Uri uri = new Uri.Builder().scheme("content").authority(AUTHORITY)
						.appendPath(providerPath).appendEncodedPath(filePath).build();
				return new InternalFile(file, type, uri);
			}
		}
		return null;
	}

	private static InternalFile getInternalFileForUriMatcherCode(int uriMatcherCode) {
		InternalFile internalFile;
		switch (uriMatcherCode) {
			case URI_MATCHER_CODE_DOWNLOADS: {
				internalFile = downloadsFile;
				break;
			}
			case URI_MATCHER_CODE_SHARE: {
				internalFile = shareFile;
				break;
			}
			case URI_MATCHER_CODE_CLIPBOARD: {
				internalFile = clipboardFile;
				break;
			}
			default: {
				internalFile = null;
				break;
			}
		}
		return internalFile;
	}

	@Override
	public String getType(@NonNull Uri uri) {
		int uriMatcherCode = URI_MATCHER.match(uri);
		if (uriMatcherCode == URI_MATCHER_CODE_UPDATES) {
			return "application/vnd.android.package-archive";
		} else {
			InternalFile internalFile = getInternalFileForUriMatcherCode(uriMatcherCode);
			if (internalFile != null) {
				return internalFile.type;
			} else {
				throw new IllegalArgumentException("Unknown URI: " + uri);
			}
		}
	}

	@Override
	public ParcelFileDescriptor openFile(@NonNull Uri uri, @NonNull String mode) throws FileNotFoundException {
		int uriMatcherCode = URI_MATCHER.match(uri);
		File file = null;
		if (uriMatcherCode == URI_MATCHER_CODE_UPDATES && "r".equals(mode)) {
			file = getUpdatesFile(uri.getLastPathSegment());
		} else {
			InternalFile internalFile = getInternalFileForUriMatcherCode(uriMatcherCode);
			if (internalFile != null && uri.equals(internalFile.uri)) {
				file = internalFile.file;
			}
		}

		if (file != null) {
			return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
		} else {
			throw new FileNotFoundException();
		}
	}

	private static final String[] ALLOWED_PROJECTION = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};

	@Override
	public Cursor query(@NonNull Uri uri, String[] projection,
						String selection, String[] selectionArgs, String sortOrder) {
		int uriMatcherCode = URI_MATCHER.match(uri);
		if (uriMatcherCode == UriMatcher.NO_MATCH) {
			throw new IllegalArgumentException("Unknown URI: " + uri);
		}

		if (projection == null) {
			projection = ALLOWED_PROJECTION;
		}
		else {
			OUTER:
			for (String column : projection) {
				for (String allowedColumn : ALLOWED_PROJECTION) {
					if (CommonUtils.equals(column, allowedColumn)) {
						continue OUTER;
					}
				}
				throw new SQLiteException("No such column: " + column);
			}
		}

		MatrixCursor cursor = new MatrixCursor(projection);
		File file;
		if (uriMatcherCode == URI_MATCHER_CODE_UPDATES) {
			file = getUpdatesFile(uri.getLastPathSegment());
		} else {
			InternalFile internalFile = getInternalFileForUriMatcherCode(uriMatcherCode);
			file = internalFile != null ? internalFile.file : null;
		}
		if (file != null) {
			Object[] values = new Object[projection.length];
			for (int i = 0; i < projection.length; i++) {
				if (OpenableColumns.DISPLAY_NAME.equals(projection[i])) {
					values[i] = file.getName();
				} else if (OpenableColumns.SIZE.equals(projection[i])) {
					values[i] = file.length();
				}
			}
			cursor.addRow(values);
		}
		return cursor;
	}

	@Override
	public Uri insert(@NonNull Uri uri, ContentValues values) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int update(@NonNull Uri uri, ContentValues values, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int delete(@NonNull Uri uri, String selection, String[] selectionArgs) {
		throw new UnsupportedOperationException();
	}
}
