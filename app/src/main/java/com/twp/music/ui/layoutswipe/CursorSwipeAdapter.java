package com.twp.music.ui.layoutswipe;

import android.content.Context;
import android.database.Cursor;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.v4.widget.CursorAdapter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.twp.music.R;
import com.twp.music.util.Logger;
import com.twp.music.util.MusicUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public abstract class CursorSwipeAdapter extends CursorAdapter implements SwipeItemMangerInterface, SwipeAdapterInterface {

    private SwipeItemMangerImpl mItemManger = new SwipeItemMangerImpl(this);

    private Map<Integer, Boolean> opens = new HashMap<Integer, Boolean>();

    protected CursorSwipeAdapter(Context context, Cursor c, boolean autoRequery) {
        super(context, c, autoRequery);
    }

    public abstract void onTrashOnclick(String name, final int id);

    public abstract void onPlayingPosition(int position ,long audioId);

    protected CursorSwipeAdapter(Context context, Cursor c, int flags) {
        super(context, c, flags);
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final int index = position;
        if (!mDataValid) {
            throw new IllegalStateException("this should only be called when the cursor is valid");
        }
        if (!mCursor.moveToPosition(position)) {
            throw new IllegalStateException("couldn't move cursor to position " + position);
        }
        ViewHolder viewHolder;
        if (null == convertView) {

            viewHolder = new ViewHolder();
            convertView = newView(mContext, mCursor, parent);
            viewHolder.tvMusicTitle = (TextView) convertView.findViewById(R.id.tvMusicTitle);
            viewHolder.tvMusicAuthor = (TextView) convertView.findViewById(R.id.tvMusicAuthor);
            viewHolder.swipeLayout = (SwipeLayout) convertView.findViewById(R.id.swipe);
            viewHolder.imgDeleteItem = (ImageView) convertView.findViewById(R.id.trash);
            viewHolder.imgPlayingIcon = (ImageView) convertView.findViewById(R.id.imgPlayingIcon);
            viewHolder.swipeLayout.addSwipeListener(new SwipeListenerAll(index));
            mItemManger.initialize(convertView, position);
            convertView.setTag(viewHolder);
        } else {
            mItemManger.updateConvertView(convertView, position);
            viewHolder = (ViewHolder) convertView.getTag();
        }

        //fill
        viewHolder.imgDeleteItem.setTag(position);
        final String title = mCursor.getString(mCursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
        viewHolder.tvMusicTitle.setText(title);
        String artist = mCursor.getString(mCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
        viewHolder.tvMusicAuthor.setText(artist);
        long playingId = -1;
        if (MusicUtils.sService != null) {
            try {
                playingId = MusicUtils.sService.getAudioId();
                if (playingId == mCursor.getInt(mCursor.getColumnIndex(MediaStore.Audio.Media._ID)))
                {
                    onPlayingPosition(position,playingId);
                    viewHolder.imgPlayingIcon.setVisibility(View.VISIBLE);
                }else{
                    viewHolder.imgPlayingIcon.setVisibility(View.GONE);
                }
            } catch (RemoteException e) {

            }
        }
        viewHolder.imgDeleteItem.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (v instanceof ImageView) {
                    int cPos = (Integer) v.getTag();
                    if (!mCursor.moveToPosition(cPos)) {
                        throw new IllegalStateException("couldn't move cursor to position " + cPos);
                    }
                    int id = mCursor.getInt(mCursor.getColumnIndex(MediaStore.Audio.Media._ID));
                    String cTitle = mCursor.getString(mCursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                    onTrashOnclick(cTitle, id);
                    //Logger.i("Click Position : " + cPos + " title:" + title +" ,id:"+id);
                }
            }
        });

        if (opens.containsKey(position)) {
            if (opens.get(position)) {
                //Logger.i("position " + position + " statues is Opend");
                openItem(position);
            } else {
                closeItem(position);
                viewHolder.swipeLayout.requestLayout();
                // Logger.i("position " + position + " statues is closed");
            }
        } else {
            closeItem(position);
            viewHolder.swipeLayout.requestLayout();
            // Logger.i("position " + position + " statues is closed");
        }


        return convertView;
    }


    class SwipeListenerAll implements SwipeLayout.SwipeListener {
        private int index;

        SwipeListenerAll(int index) {
            this.index = index;
        }

        @Override
        public void onStartOpen(SwipeLayout layout) {
            Logger.i("onStartOpen == " + layout);
        }

        @Override
        public void onOpen(SwipeLayout layout) {
            Logger.i("onOpen == " + layout);
            opens.put(index, true);
            dumpOpens("onOpen");
        }

        @Override
        public void onStartClose(SwipeLayout layout) {
            Logger.i("onStartClose == " + layout);
        }

        @Override
        public void onClose(SwipeLayout layout) {
            Logger.i("onClose == " + layout);
            opens.put(index, false);
            opens.remove(index);
            dumpOpens("onOpen");
        }

        @Override
        public void onUpdate(SwipeLayout layout, int leftOffset, int topOffset) {
            Logger.i("onUpdate == " + layout);
        }

        @Override
        public void onHandRelease(SwipeLayout layout, float xvel, float yvel) {
            Logger.i("onHandRelease == " + layout);
        }
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {

    }

    private void dumpOpens(String key) {
        Iterator<Integer> keyIt = opens.keySet().iterator();
        Logger.i("[");
        while (keyIt.hasNext()) {
            int position = keyIt.next();
            boolean st = opens.get(position);
            Logger.i(key + "  index:" + position + ", st:" + st);
        }
        Logger.i("]");
    }

    static class ViewHolder {
        public SwipeLayout swipeLayout;
        public TextView tvMusicTitle;
        public TextView tvMusicAuthor;
        public ImageView imgDeleteItem;
        public ImageView imgPlayingIcon;
    }

    @Override
    public void openItem(int position) {
        mItemManger.openItem(position);
    }

    @Override
    public void closeItem(int position) {
        mItemManger.closeItem(position);
    }

    @Override
    public void closeAllExcept(SwipeLayout layout) {
        mItemManger.closeAllExcept(layout);
    }

    @Override
    public List<Integer> getOpenItems() {
        return mItemManger.getOpenItems();
    }

    @Override
    public List<SwipeLayout> getOpenLayouts() {
        return mItemManger.getOpenLayouts();
    }

    @Override
    public void removeShownLayouts(SwipeLayout layout) {
        mItemManger.removeShownLayouts(layout);
    }

    @Override
    public boolean isOpen(int position) {
        return mItemManger.isOpen(position);
    }

    @Override
    public SwipeItemMangerImpl.Mode getMode() {
        return mItemManger.getMode();
    }

    @Override
    public void setMode(SwipeItemMangerImpl.Mode mode) {
        mItemManger.setMode(mode);
    }
}
