package com.mishiranu.dashchan.content.async;

import android.os.SystemClock;
import chan.content.ChanConfiguration;
import chan.content.ChanLocator;
import chan.content.ChanPerformer;
import chan.content.ExtensionException;
import chan.content.InvalidResponseException;
import chan.http.HttpException;
import chan.http.HttpHolder;
import chan.util.CommonUtils;
import com.mishiranu.dashchan.content.model.ErrorItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import java.net.HttpURLConnection;

public class ReadSinglePostTask extends HttpHolderTask<Void, Void, PostItem> {
	private final Callback callback;
	private final String boardName;
	private final String chanName;
	private final String threadNumber;
	private final PostNumber postNumber;

	private ErrorItem errorItem;

	public interface Callback {
		void onReadSinglePostSuccess(PostItem postItem);
		void onReadSinglePostFail(ErrorItem errorItem);
	}

	public ReadSinglePostTask(Callback callback, String chanName,
			String boardName, String threadNumber, PostNumber postNumber) {
		this.callback = callback;
		this.boardName = boardName;
		this.chanName = chanName;
		this.threadNumber = threadNumber;
		this.postNumber = postNumber;
	}

	@Override
	protected PostItem doInBackground(HttpHolder holder, Void... params) {
		long startTime = SystemClock.elapsedRealtime();
		try {
			ChanPerformer performer = ChanPerformer.get(chanName);
			String postNumber;
			if (this.postNumber != null) {
				postNumber = this.postNumber.toString();
			} else {
				postNumber = threadNumber;
			}
			ChanPerformer.ReadSinglePostResult result = performer.safe().onReadSinglePost(new ChanPerformer
					.ReadSinglePostData(boardName, postNumber, holder));
			if (result == null || result.post == null) {
				throw HttpException.createNotFoundException();
			}
			startTime = 0L;
			return PostItem.createPost(result.post.post, ChanLocator.get(chanName),
					chanName, boardName, result.post.threadNumber, result.post.originalPostNumber);
		} catch (HttpException e) {
			errorItem = e.getErrorItemAndHandle();
			if (errorItem.httpResponseCode == HttpURLConnection.HTTP_NOT_FOUND ||
					errorItem.httpResponseCode == HttpURLConnection.HTTP_GONE) {
				errorItem = new ErrorItem(ErrorItem.Type.POST_NOT_FOUND);
			}
		} catch (ExtensionException | InvalidResponseException e) {
			errorItem = e.getErrorItemAndHandle();
		} finally {
			ChanConfiguration.get(chanName).commit();
			CommonUtils.sleepMaxRealtime(startTime, 500);
		}
		return null;
	}

	@Override
	protected void onPostExecute(PostItem result) {
		if (result != null) {
			callback.onReadSinglePostSuccess(result);
		} else {
			callback.onReadSinglePostFail(errorItem);
		}
	}
}
