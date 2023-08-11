package com.mishiranu.dashchan.content.async;

import android.os.CancellationSignal;
import android.os.OperationCanceledException;

import com.mishiranu.dashchan.content.database.BookmarksDatabase;
import com.mishiranu.dashchan.content.database.CommonDatabase;

public class GetBookmarksTask extends ExecutorTask<Void, BookmarksDatabase.BookmarksCursor> {

    public interface Callback {
        void onGetBookmarksResult(BookmarksDatabase.BookmarksCursor cursor);
    }

    private final GetBookmarksTask.Callback callback;
    private final String chanName;
    private final CancellationSignal signal = new CancellationSignal();

    public GetBookmarksTask(GetBookmarksTask.Callback callback, String chanName) {
        this.callback = callback;
        this.chanName = chanName;
    }

    @Override
    protected BookmarksDatabase.BookmarksCursor run() {
        try {
            return CommonDatabase.getInstance().getBookmarks().getBookmarks(chanName, signal);
        } catch (OperationCanceledException e) {
            return null;
        }
    }

    @Override
    protected void onComplete(BookmarksDatabase.BookmarksCursor cursor) {
        callback.onGetBookmarksResult(cursor);
    }
}
