package com.mishiranu.dashchan.content.model;

import android.net.Uri;
import chan.content.ChanLocator;
import com.mishiranu.dashchan.content.service.DownloadService;
import com.mishiranu.dashchan.util.NavigationUtils;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

public class GalleryItem implements Serializable {
	private static final long serialVersionUID = 1L;

	private final String fileUriString;
	private final String thumbnailUriString;

	public final String boardName;
	public final String threadNumber;
	public final PostNumber postNumber;

	public final String originalName;

	public final int width;
	public final int height;

	public int size;

	private transient Uri fileUri;
	private transient Uri thumbnailUri;

	public GalleryItem(Uri fileUri, Uri thumbnailUri, String boardName, String threadNumber, PostNumber postNumber,
			String originalName, int width, int height, int size) {
		fileUriString = fileUri != null ? fileUri.toString() : null;
		thumbnailUriString = thumbnailUri != null ? thumbnailUri.toString() : null;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		this.postNumber = postNumber;
		this.originalName = originalName;
		this.width = width;
		this.height = height;
		this.size = size;
	}

	public GalleryItem(Uri fileUri, String boardName, String threadNumber) {
		fileUriString = null;
		thumbnailUriString = null;
		this.boardName = boardName;
		this.threadNumber = threadNumber;
		postNumber = null;
		originalName = null;
		width = 0;
		height = 0;
		size = 0;
		this.fileUri = fileUri;
	}

	public boolean isImage(ChanLocator locator) {
		return locator.isImageExtension(getFileName(locator));
	}

	public boolean isVideo(ChanLocator locator) {
		return locator.isVideoExtension(getFileName(locator));
	}

	public boolean isOpenableVideo(ChanLocator locator) {
		return NavigationUtils.isOpenableVideoPath(getFileName(locator));
	}

	public Uri getFileUri(ChanLocator locator) {
		if (fileUri == null && fileUriString != null) {
			fileUri = locator.convert(Uri.parse(fileUriString));
		}
		return fileUri;
	}

	public Uri getThumbnailUri(ChanLocator locator) {
		if (thumbnailUri == null && thumbnailUriString != null) {
			thumbnailUri = locator.convert(Uri.parse(thumbnailUriString));
		}
		return thumbnailUri;
	}

	public Uri getDisplayImageUri(ChanLocator locator) {
		return isImage(locator) ? getFileUri(locator) : getThumbnailUri(locator);
	}

	public String getFileName(ChanLocator locator) {
		Uri fileUri = getFileUri(locator);
		return locator.createAttachmentFileName(fileUri);
	}

	public void downloadStorage(DownloadService.Binder binder, ChanLocator locator, String threadTitle) {
		binder.downloadStorage(getFileUri(locator), getFileName(locator), originalName,
				locator.getChanName(), boardName, threadNumber, threadTitle);
	}

	public static class Set {
		private final boolean navigatePostSupported;
		private final TreeMap<PostNumber, List<GalleryItem>> galleryItems = new TreeMap<>();

		private String threadTitle;

		public Set(boolean navigatePostSupported) {
			this.navigatePostSupported = navigatePostSupported;
		}

		public void setThreadTitle(String threadTitle) {
			this.threadTitle = threadTitle;
		}

		public String getThreadTitle() {
			return threadTitle;
		}

		public void put(PostNumber postNumber, Collection<AttachmentItem> attachmentItems) {
			if (attachmentItems != null) {
				ArrayList<GalleryItem> galleryItems = new ArrayList<>();
				for (AttachmentItem attachmentItem : attachmentItems) {
					if (attachmentItem.isShowInGallery() && attachmentItem.canDownloadToStorage()) {
						galleryItems.add(attachmentItem.createGalleryItem());
					}
				}
				if (!galleryItems.isEmpty()) {
					this.galleryItems.put(postNumber, galleryItems);
				}
			}
		}

		public void remove(PostNumber postNumber) {
			galleryItems.remove(postNumber);
		}

		public void clear() {
			galleryItems.clear();
		}

		public int findIndex(PostItem postItem) {
			if (postItem.hasAttachments()) {
				int index = 0;
				PostNumber postNumber = postItem.getPostNumber();
				for (TreeMap.Entry<PostNumber, List<GalleryItem>> entry : galleryItems.entrySet()) {
					if (postNumber.equals(entry.getKey())) {
						return index;
					}
					index += entry.getValue().size();
				}
			}
			return -1;
		}

		public List<GalleryItem> createList() {
			ArrayList<GalleryItem> galleryItems = new ArrayList<>();
			for (List<GalleryItem> list : this.galleryItems.values()) {
				galleryItems.addAll(list);
			}
			return galleryItems;
		}

		public boolean isNavigatePostSupported() {
			return navigatePostSupported;
		}
	}
}
