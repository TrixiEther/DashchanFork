package com.mishiranu.dashchan.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.util.GraphicsUtils;
import com.mishiranu.dashchan.widget.ClickableToast;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import chan.util.DataFile;

public class CaptchaOptionsDialog extends DialogFragment {
	private static final String KEY_CAPTCHA_IMAGE = "captcha_image";
	private CaptchaOptionsViewModel viewModel;

	public CaptchaOptionsDialog() {
	}

	public CaptchaOptionsDialog(@NonNull Bitmap captchaImage) {
		Bundle args = new Bundle();
		args.putParcelable(KEY_CAPTCHA_IMAGE, captchaImage);
		setArguments(args);
	}

	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		viewModel = new ViewModelProvider(this).get(CaptchaOptionsViewModel.class);
		observeCaptchaImageAttachment();
		observeCaptchaImageDownload();
	}

	@NonNull
	@Override
	public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
		Bitmap captchaImage = requireArguments().getParcelable(KEY_CAPTCHA_IMAGE);
		String[] buttonTitles = {getString(R.string.attach), getString(R.string.download_file), getString(R.string.refresh)};
		AlertDialog dialog = new AlertDialog.Builder(requireContext()).setItems(buttonTitles, null).create();
		dialog.getListView().setOnItemClickListener((parent, view, position, id) -> { // set onclick listener here to prevent the dialog from dismissing when an item is clicked, we will dismiss the dialog manually when selected action is done
			switch (position) {
				case 0: {
					viewModel.onAttachClicked(captchaImage);
					break;
				}
				case 1: {
					viewModel.onDownloadClicked(captchaImage);
					break;
				}
				case 2: {
					refreshCaptchaAndDismiss();
					break;
				}
				default: {
					throw new IllegalStateException("Unexpected value: " + position);
				}
			}
		});
		return dialog;
	}

	private void observeCaptchaImageAttachment() {
		viewModel.captchaImageAttachmentDataFile.observe(this, captchaImageAttachmentDataFile -> {
			if (captchaImageAttachmentDataFile != null) {
				getCallback().attachCaptchaImageToPost(captchaImageAttachmentDataFile);
			} else {
				ClickableToast.show(R.string.unknown_error);
			}
			dismiss();
		});
	}

	private void observeCaptchaImageDownload() {
		viewModel.captchaImageDownloadInputStreamAndFileName.observe(this, (captchaImageDownloadInputStreamAndName -> {
			InputStream captchaImageInputStream = captchaImageDownloadInputStreamAndName.first;
			String captchaImageName = captchaImageDownloadInputStreamAndName.second;
			CaptchaImageDownloadParameters captchaImageDownloadParameters = getCallback().getCaptchaImageDownloadParameters();
			String chanName = captchaImageDownloadParameters.chanName;
			String boardName = captchaImageDownloadParameters.boardName;
			String threadNumber = captchaImageDownloadParameters.threadNumber;
			((FragmentHandler) requireActivity()).getDownloadBinder().downloadStorage(captchaImageInputStream, chanName, boardName, threadNumber, null, captchaImageName, true, false);
			dismiss();
		}));
	}

	private void refreshCaptchaAndDismiss() {
		getCallback().refreshCaptcha();
		dismiss();
	}

	private Callback getCallback() {
		Fragment parentFragment = requireParentFragment();
		if (parentFragment instanceof Callback) {
			return (Callback) parentFragment;
		} else {
			throw new IllegalStateException("Parent fragment must implement CaptchaOptionsDialog.Callback");
		}
	}

	public interface Callback {
		void attachCaptchaImageToPost(DataFile captchaImageAttachmentDataFile);

		CaptchaImageDownloadParameters getCaptchaImageDownloadParameters();

		void refreshCaptcha();
	}

	public static class CaptchaImageDownloadParameters {
		private final String chanName;
		private final String boardName;
		private final String threadNumber;

		public CaptchaImageDownloadParameters(String chanName, String boardName, String threadNumber) {
			this.chanName = chanName;
			this.boardName = boardName;
			this.threadNumber = threadNumber;
		}

	}

	public static class CaptchaOptionsViewModel extends ViewModel {
		private final MutableLiveData<DataFile> captchaImageAttachmentDataFile = new MutableLiveData<>();
		private final MutableLiveData<Pair<InputStream, String>> captchaImageDownloadInputStreamAndFileName = new MutableLiveData<>();
		private final ExecutorService executor = Executors.newSingleThreadExecutor();


		private void onAttachClicked(Bitmap captchaImage) {
			executor.execute(() -> {
				try {
					String captchaImageFileName = getCaptchaImageFileName();
					DataFile captchaImageDataFile = DataFile.obtain(DataFile.Target.CACHE, captchaImageFileName);
					writeCaptchaImagePNGToOutputStream(captchaImage, captchaImageDataFile.openOutputStream());
					captchaImageAttachmentDataFile.postValue(captchaImageDataFile);
				} catch (IOException e) {
					captchaImageAttachmentDataFile.postValue(null);
				}
			});
		}

		private void onDownloadClicked(Bitmap captchaImage) {
			executor.execute(() -> {
				ByteArrayOutputStream captchaImageOutputStream = new ByteArrayOutputStream();
				writeCaptchaImagePNGToOutputStream(captchaImage, captchaImageOutputStream);
				byte[] captchaImageBytes = captchaImageOutputStream.toByteArray();
				ByteArrayInputStream captchaImageInputStream = new ByteArrayInputStream(captchaImageBytes);
				String captchaImageFileName = getCaptchaImageFileName();
				captchaImageDownloadInputStreamAndFileName.postValue(new Pair<>(captchaImageInputStream, captchaImageFileName));
			});
		}

		private void writeCaptchaImagePNGToOutputStream(Bitmap captchaImage, OutputStream outputStream) {
			processCaptchaImage(captchaImage).compress(Bitmap.CompressFormat.PNG, 0, outputStream);
		}

		private Bitmap processCaptchaImage(Bitmap captchaImage) {
			if (GraphicsUtils.isBlackAndWhiteCaptchaImage(captchaImage)) {
				return createCaptchaImageWithWhiteBackground(captchaImage);
			} else {
				return captchaImage;
			}
		}

		private Bitmap createCaptchaImageWithWhiteBackground(Bitmap captchaImage) {
			Bitmap newCaptchaImage = Bitmap.createBitmap(captchaImage.getWidth(), captchaImage.getHeight(), captchaImage.getConfig());
			Canvas canvas = new Canvas(newCaptchaImage);
			canvas.drawColor(Color.WHITE);
			canvas.drawBitmap(captchaImage, 0, 0, null);
			return newCaptchaImage;
		}

		private String getCaptchaImageFileName() {
			return "captcha-" + System.currentTimeMillis() + ".png";
		}

		@Override
		protected void onCleared() {
			executor.shutdownNow();
		}

	}

}
