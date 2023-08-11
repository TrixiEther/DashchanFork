package com.mishiranu.dashchan.ui.navigator.page;

import android.content.Context;
import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mishiranu.dashchan.R;
import com.mishiranu.dashchan.content.async.GetBookmarksTask;
import com.mishiranu.dashchan.content.database.BookmarksDatabase;
import com.mishiranu.dashchan.content.database.CommonDatabase;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.ui.navigator.Page;
import com.mishiranu.dashchan.ui.navigator.adapter.BookmarksAdapter;
import com.mishiranu.dashchan.ui.navigator.adapter.HistoryAdapter;
import com.mishiranu.dashchan.ui.navigator.adapter.PostsAdapter;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.ui.navigator.manager.ViewUnit;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.ConcurrentUtils;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.util.ResourceUtils;
import com.mishiranu.dashchan.widget.ListPosition;
import com.mishiranu.dashchan.widget.PaddedRecyclerView;
import com.mishiranu.dashchan.widget.PostsLayoutManager;

import java.util.HashMap;
import java.util.HashSet;

import chan.content.ChanConfiguration;

public class BookmarksPage extends ListPage implements BookmarksAdapter.Callback, UiManager.Observer, GetBookmarksTask.Callback {

    private String chanName;
    private boolean firstLoad = true;

    private BookmarksAdapter getAdapter() {
        return (BookmarksAdapter) getRecyclerView().getAdapter();
    }

    @Override
    public void onItemClick(View view, PostItem postItem) {

    }

    @Override
    public boolean onItemLongClick(PostItem postItem) {
        return false;
    }

    private static class RetainableExtra implements Retainable {
        public static final ExtraFactory<BookmarksPage.RetainableExtra> FACTORY = BookmarksPage.RetainableExtra::new;

        public final HashMap<PostNumber, PostItem> postItems = new HashMap<>();

    }

    private static class ParcelableExtra implements Parcelable {

        public static final ExtraFactory<BookmarksPage.ParcelableExtra> FACTORY = BookmarksPage.ParcelableExtra::new;
        public final HashSet<PostNumber> bookmarkedPosts = new HashSet<>();

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(bookmarkedPosts.size());
            for (PostNumber number : bookmarkedPosts) {
                number.writeToParcel(dest, flags);
            }
        }

        public static final Creator<BookmarksPage.ParcelableExtra> CREATOR = new Creator<BookmarksPage.ParcelableExtra>() {
            @Override
            public BookmarksPage.ParcelableExtra createFromParcel(Parcel source) {
                BookmarksPage.ParcelableExtra parcelableExtra = new BookmarksPage.ParcelableExtra();
                int bookmarkedPostsCount = source.readInt();
                for (int i = 0; i < bookmarkedPostsCount; i++) {
                    parcelableExtra.bookmarkedPosts.add(PostNumber.CREATOR.createFromParcel(source));
                }
                return parcelableExtra;
            }
            @Override
            public BookmarksPage.ParcelableExtra[] newArray(int size) {
                return new BookmarksPage.ParcelableExtra[size];
            }
        };
    }

    @Override
    protected void onCreate() {
        Context context = getContext();
        PaddedRecyclerView recyclerView = getRecyclerView();
        recyclerView.setLayoutManager(new PostsLayoutManager(recyclerView.getContext()));
        Page page = getPage();
        this.chanName = page.chanName;
        UiManager uiManager = getUiManager();
        uiManager.view().bindThreadsPostRecyclerView(recyclerView);
        float density = ResourceUtils.obtainDensity(context);
        int dividerPadding = (int) (12f * density);
        BookmarksPage.RetainableExtra retainableExtra = getRetainableExtra(BookmarksPage.RetainableExtra.FACTORY);
        BookmarksPage.ParcelableExtra parcelableExtra = getParcelableExtra(BookmarksPage.ParcelableExtra.FACTORY);
        CommonDatabase.getInstance().getBookmarks().registerObserver(updateBookmarksRunnable);
        uiManager.observable().register(this);
    }

    @Override
    protected void onDestroy() {
        CommonDatabase.getInstance().getBookmarks().unregisterObserver(updateBookmarksRunnable);
        getAdapter().setCursor(null);
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private final ListViewUtils.UnlimitedRecycledViewPool postsViewPool =
            new ListViewUtils.UnlimitedRecycledViewPool();

    public void bindPostsRecyclerView(RecyclerView recyclerView) {
        recyclerView.setRecycledViewPool(postsViewPool);
        ((LinearLayoutManager) recyclerView.getLayoutManager()).setRecycleChildrenOnDetach(true);
    }

    @Override
    public String obtainTitle() {
        return getString(R.string.bookmarks);
    }

    private final Runnable updateBookmarksRunnable = this::updateBookmarks;
    private GetBookmarksTask task;

    private void updateBookmarks() {
        if (task != null) {
            task.cancel();
        }
        task = new GetBookmarksTask(this, chanName);
        task.execute(ConcurrentUtils.PARALLEL_EXECUTOR);
    }

    @Override
    public void onGetBookmarksResult(BookmarksDatabase.BookmarksCursor cursor) {
        task = null;
        boolean firstLoad = this.firstLoad;
        this.firstLoad = false;
        getAdapter().setCursor(cursor);
        ListPosition listPosition = takeListPosition();
        if (cursor.hasItems) {
            switchList();
            if (firstLoad && listPosition != null) {
                listPosition.apply(getRecyclerView());
            }
        } else {
            switchError(R.string.history_is_empty);
        }
    }

}
