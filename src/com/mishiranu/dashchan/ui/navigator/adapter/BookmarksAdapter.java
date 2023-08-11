package com.mishiranu.dashchan.ui.navigator.adapter;

import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.mishiranu.dashchan.content.HidePerformer;
import com.mishiranu.dashchan.content.database.BookmarksDatabase;
import com.mishiranu.dashchan.content.database.HistoryDatabase;
import com.mishiranu.dashchan.content.model.GalleryItem;
import com.mishiranu.dashchan.content.model.PostItem;
import com.mishiranu.dashchan.content.model.PostNumber;
import com.mishiranu.dashchan.ui.navigator.manager.UiManager;
import com.mishiranu.dashchan.ui.navigator.manager.ViewUnit;
import com.mishiranu.dashchan.ui.posting.Replyable;
import com.mishiranu.dashchan.util.ListViewUtils;
import com.mishiranu.dashchan.widget.CommentTextView;
import com.mishiranu.dashchan.widget.CursorAdapter;
import com.mishiranu.dashchan.widget.ViewFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;

public class BookmarksAdapter extends CursorAdapter<BookmarksDatabase.BookmarksCursor, RecyclerView.ViewHolder>
        implements CommentTextView.LinkListener, UiManager.PostsProvider {

    @Override
    public PostItem findPostItem(PostNumber postNumber) {
        return null;
    }

    @Override
    public void onLinkClick(CommentTextView view, Uri uri, Extra extra, boolean confirmed) {

    }

    @Override
    public void onLinkLongClick(CommentTextView view, Uri uri, Extra extra) {

    }

    @NonNull
    @Override
    public Iterator<PostItem> iterator() {
        return null;
    }

    public interface Callback extends ListViewUtils.ClickCallback<PostItem, RecyclerView.ViewHolder> {
        void onItemClick(View view, PostItem postItem);
        boolean onItemLongClick(PostItem postItem);

        @Override
        default boolean onItemClick(RecyclerView.ViewHolder holder,
                                    int position, PostItem postItem, boolean longClick) {
            if (longClick) {
                return onItemLongClick(postItem);
            } else {
                onItemClick(holder.itemView, postItem);
                return true;
            }
        }
    }

    private final UiManager uiManager;
    private final String chanName;
    private final CommentTextView.RecyclerKeeper recyclerKeeper;
    private final RecyclerView recyclerView;
    private final UiManager.ConfigurationSet configurationSet;
    private final GalleryItem.Set gallerySet = new GalleryItem.Set(true);
    private final UiManager.DemandSet demandSet = new UiManager.DemandSet();

    private final ArrayList<PostNumber> postNumbers = new ArrayList<>();
    private final Map<PostNumber, PostItem> postItemsMap;
    private final BookmarksDatabase.BookmarkItem bookmarkItem = new BookmarksDatabase.BookmarkItem();

    public BookmarksAdapter(Callback callback, String chanName, UiManager uiManager, Replyable replyable,
                            FragmentManager fragmentManager, RecyclerView recyclerView, Map<PostNumber, PostItem> postItemsMap) {
        this.uiManager = uiManager;
        recyclerKeeper = new CommentTextView.RecyclerKeeper(recyclerView);
        configurationSet = new UiManager.ConfigurationSet(chanName, replyable, this, null,
                gallerySet, fragmentManager, uiManager.dialog().createStackInstance(), this, callback,
                true, false, true, true, true, null);
        this.recyclerView = recyclerView;
        super.registerAdapterDataObserver(recyclerKeeper);
        this.postItemsMap = postItemsMap;
        this.chanName = chanName;
        postNumbers.addAll(postItemsMap.keySet());
        Collections.sort(postNumbers);
    }

    public void insertItems(Map<PostNumber, PostItem> bookmarkedPosts) {
        postItemsMap.putAll(bookmarkedPosts);
        postNumbers.clear();
        postNumbers.addAll(postItemsMap.keySet());
        Collections.sort(postNumbers);
    }

    @Override
    public void registerAdapterDataObserver(@NonNull RecyclerView.AdapterDataObserver observer) {
        super.registerAdapterDataObserver(observer);
        super.unregisterAdapterDataObserver(recyclerKeeper);
        super.registerAdapterDataObserver(recyclerKeeper);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return uiManager.view().createView(parent, ViewUnit.ViewType.values()[viewType]);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        UiManager.DemandSet demandSet = this.demandSet;
        demandSet.lastInList = position == getItemCount() - 1;
        uiManager.view().bindBookmarkView(holder, null, configurationSet, demandSet);
    }

}
