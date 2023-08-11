package com.mishiranu.dashchan.content.database;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.CursorWrapper;
import android.database.sqlite.SQLiteDatabase;
import android.os.CancellationSignal;
import android.os.OperationCanceledException;

import androidx.annotation.NonNull;

import com.mishiranu.dashchan.content.model.Post;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.WeakObservable;

import java.util.Objects;

import chan.util.StringUtils;

public class BookmarksDatabase implements CommonDatabase.Instance {
    
    private interface Schema {
        interface Bookmarks {
            String TABLE_NAME = "bookmarks";
            int MAX_COUNT = 10000;
            float MAX_COUNT_FACTOR = 0.75f;
            
            interface Columns {
                String CHAN_NAME = "chan_name";
                String BOARD_NAME = "board_name";
                String THREAD_NUMBER = "thread_number";
                String POST_NUMBER_MAJOR = "post_number_major";
                String POST_NUMBER_MINOR = "post_number_minor";
                String TIME = "time";
            }
        }
    }

    public static class BookmarksCursor extends CursorWrapper {

        public final boolean hasItems;

        private final int chanNameIndex;
        private final int boardNameIndex;
        private final int threadNumberIndex;
        private final int postNumberMajor;
        private final int postNumberMinor;
        private final int timeIndex;

        public BookmarksCursor(Cursor cursor, boolean hasItems) {
            super(cursor);
            this.hasItems = hasItems;
            chanNameIndex = cursor.getColumnIndex(Schema.Bookmarks.Columns.CHAN_NAME);
            boardNameIndex = cursor.getColumnIndex(Schema.Bookmarks.Columns.BOARD_NAME);
            threadNumberIndex = cursor.getColumnIndex(Schema.Bookmarks.Columns.THREAD_NUMBER);
            postNumberMajor = cursor.getColumnIndex(Schema.Bookmarks.Columns.POST_NUMBER_MAJOR);
            postNumberMinor = cursor.getColumnIndex(Schema.Bookmarks.Columns.POST_NUMBER_MINOR);
            timeIndex = cursor.getColumnIndex(Schema.Bookmarks.Columns.TIME);
        }
    }

    public static class BookmarkItem {
        public String chanName;
        public String boardName;
        public String threadNumber;
        public int postNumberMajor;
        public int postNumberMinor;
        public long time;

        public BookmarkItem update(BookmarksCursor cursor) {
            chanName = cursor.getString(cursor.chanNameIndex);
            boardName = cursor.getString(cursor.boardNameIndex);
            threadNumber = cursor.getString(cursor.threadNumberIndex);
            postNumberMajor = cursor.getInt(cursor.postNumberMajor);
            postNumberMinor = cursor.getInt(cursor.postNumberMinor);
            time = cursor.getLong(cursor.timeIndex);
            return this;
        }

        public BookmarkItem copy() {
            BookmarkItem bookmarkItem = new BookmarkItem();
            bookmarkItem.chanName = chanName;
            bookmarkItem.boardName = boardName;
            bookmarkItem.threadNumber = threadNumber;
            bookmarkItem.postNumberMajor = postNumberMajor;
            bookmarkItem.postNumberMinor = postNumberMinor;
            bookmarkItem.time = time;
            return bookmarkItem;
        }
    }
    
    private final CommonDatabase database;
    
    BookmarksDatabase(CommonDatabase database) {
        this.database = database;
    }
    
    @Override
    public void create(SQLiteDatabase database) {
        database.execSQL("CREATE TABLE IF NOT EXISTS " + BookmarksDatabase.Schema.Bookmarks.TABLE_NAME + " (" +
                BookmarksDatabase.Schema.Bookmarks.Columns.CHAN_NAME + " TEXT NOT NULL, " +
                BookmarksDatabase.Schema.Bookmarks.Columns.BOARD_NAME + " TEXT NOT NULL, " +
                BookmarksDatabase.Schema.Bookmarks.Columns.THREAD_NUMBER + " TEXT NOT NULL, " +
                BookmarksDatabase.Schema.Bookmarks.Columns.POST_NUMBER_MAJOR + " INTEGER NOT NULL, " +
                BookmarksDatabase.Schema.Bookmarks.Columns.POST_NUMBER_MINOR + " INTEGER NOT NULL, " +
                BookmarksDatabase.Schema.Bookmarks.Columns.TIME + " INTEGER NOT NULL, " +
                "PRIMARY KEY (" + BookmarksDatabase.Schema.Bookmarks.Columns.CHAN_NAME + ", " +
                BookmarksDatabase.Schema.Bookmarks.Columns.BOARD_NAME + ", " +
                BookmarksDatabase.Schema.Bookmarks.Columns.THREAD_NUMBER + ", " +
                BookmarksDatabase.Schema.Bookmarks.Columns.POST_NUMBER_MAJOR + ", " +
                BookmarksDatabase.Schema.Bookmarks.Columns.POST_NUMBER_MINOR + "))");
    }

    @Override
    public void upgrade(SQLiteDatabase database, CommonDatabase.Migration migration) {
        switch (migration) {
            case FROM_8_TO_9: {
                database.execSQL("CREATE TABLE bookmarks (chan_name TEXT NOT NULL, " +
                        "board_name TEXT NOT NULL, thread_number TEXT NOT NULL, " +
                        "post_number_major INTEGER NOT NULL, post_number_minor INTEGER NOT NULL, " +
                        "time INTEGER NOT NULL, " +
                        "PRIMARY KEY (chan_name, board_name, thread_number, " +
                        "post_number_major, post_number_minor))");
                break;
            }
            default: {
                throw new UnsupportedOperationException();
            }
        }
    }

    @Override
    public void open(SQLiteDatabase database) {
        boolean clean;
        try (Cursor cursor = database.rawQuery("SELECT COUNT(*) FROM " + BookmarksDatabase.Schema.Bookmarks.TABLE_NAME, null)) {
            clean = cursor.moveToFirst() && cursor.getInt(0) > BookmarksDatabase.Schema.Bookmarks.MAX_COUNT;
        }
        if (clean) {
            Long time;
            String[] projection = {BookmarksDatabase.Schema.Bookmarks.Columns.TIME};
            try (Cursor cursor = database.query(BookmarksDatabase.Schema.Bookmarks.TABLE_NAME,
                    projection, null, null, null, null, BookmarksDatabase.Schema.Bookmarks.Columns.TIME + " DESC",
                    (int) (BookmarksDatabase.Schema.Bookmarks.MAX_COUNT_FACTOR * BookmarksDatabase.Schema.Bookmarks.MAX_COUNT) + ", 1")) {
                time = cursor.moveToFirst() ? cursor.getLong(0) : null;
            }
            if (time != null) {
                database.delete(BookmarksDatabase.Schema.Bookmarks.TABLE_NAME, BookmarksDatabase.Schema.Bookmarks.Columns.TIME + " <= " + time, null);
            }
        }
    }

    private final WeakObservable<Runnable> observable = new WeakObservable<>();

    public void registerObserver(Runnable runnable) {
        observable.register(runnable);
    }

    public void unregisterObserver(Runnable runnable) {
        observable.unregister(runnable);
    }

    private final Runnable onChanged = () -> {
        for (Runnable runnable : observable) {
            runnable.run();
        }
    };

    public void addBookmarkAsync(@NonNull String chanName, String boardName, @NonNull String threadNumber, Post post) {
        Objects.requireNonNull(chanName);
        Objects.requireNonNull(threadNumber);
        database.enqueue(db -> {
            ContentValues values = new ContentValues();
            values.put(Schema.Bookmarks.Columns.CHAN_NAME, chanName);
            values.put(Schema.Bookmarks.Columns.BOARD_NAME, StringUtils.emptyIfNull(boardName));
            values.put(Schema.Bookmarks.Columns.THREAD_NUMBER, threadNumber);
            values.put(Schema.Bookmarks.Columns.POST_NUMBER_MAJOR, post.number.major);
            values.put(Schema.Bookmarks.Columns.POST_NUMBER_MINOR, post.number.minor);
            values.put(Schema.Bookmarks.Columns.TIME, System.currentTimeMillis());
            db.replace(Schema.Bookmarks.TABLE_NAME, null, values);
            ConcurrentUtils.HANDLER.post(onChanged);
            return null;
        });
    }

    public BookmarksCursor getBookmarks(@NonNull String chanName, CancellationSignal signal) throws OperationCanceledException {
        int count = database.execute(database -> {
            String[] projection = {"COUNT(*)"};
            Expression.Filter.Builder filterBuilder = Expression.filter();
            if (chanName != null) {
                filterBuilder.equals(Schema.Bookmarks.Columns.CHAN_NAME, chanName);
            }
            Expression.Filter filter = filterBuilder.build();
            try (Cursor cursor = database.query(false, Schema.Bookmarks.TABLE_NAME,
                    projection, filter.value, filter.args, null, null, null, null, signal)) {
                if (cursor.moveToFirst()) {
                    return cursor.getInt(0);
                }
            }
            return 0;
        });
        String[] projection = {"rowid", "*"};
        Expression.Filter.Builder filterBuilder = Expression.filter();
        Expression.Filter filter = filterBuilder.build();
        Cursor cursor = database.query(database -> database.query(false, Schema.Bookmarks.TABLE_NAME, projection,
                filter.value, filter.args, null, null, Schema.Bookmarks.Columns.TIME + " DESC", null, signal));
        return new BookmarksCursor(cursor, count > 0);
    }
}
