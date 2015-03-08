/*
 * Copyright (C) 2015. 彭钦平 Technologies. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *     http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.twp.music;

import android.app.Activity;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.twp.music.service.PlayBackService;
import com.twp.music.util.Logger;
import com.twp.music.util.MusicUtils;


/**
 * Created by pengqinping on 15/3/8.
 *
 * @email Royal.k.peng@gmail.com
 * @description 音乐列表页面
 */
public class MusicListsActivity extends Activity implements ServiceConnection {

    private static final String TAG = "MusicListsActivity";

    //显示3种情况的列表，提供全部播放列表
    //1.歌手所有歌曲。
    //2.专辑所有歌曲。
    //3.列表所有歌曲。
    public enum ShowType {
        ARTIST(1), ABLUM(2), LIST(3);

        private ShowType(int value) {
            this.mValue = value;
        }

        private int mValue;

        public int intValue() {
            return mValue;
        }

        public static ShowType getTyepWithIntValue(int value) {
            if (value == 1) {
                return ARTIST;
            } else if (value == 2) {
                return ABLUM;
            } else if (value == 3) {
                return LIST;
            } else {
                throw new RuntimeException(value + " this value dont't has enum.");
            }
        }


    }

    private View.OnClickListener listener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            //PLAY ALL
            //get service audioId if play or pause ,use position or not .play all.
            if (adapter != null &&null != MusicUtils.sService) {
                //判断是在播放还是暂停还是没有播

                MusicUtils.playAll(MusicListsActivity.this, adapter.getCursor(), 0);
            }

        }
    };

    private AdapterView.OnItemClickListener itemClickListener = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            MusicUtils.playAll(MusicListsActivity.this, adapter.getCursor(), position);
        }
    };

    private ShowType showType;
    private Cursor mTrackCursor;
    private PlayListAdapter adapter;
    private ListView listView;
    private TextView tvTitle;
    private ImageButton btnPlayAll;
    private String title;
    private MusicUtils.ServiceToken token;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_music_lists);
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        token = MusicUtils.bindToService(this, this);
        listView = (ListView) findViewById(R.id.listViewLocalMusic);
        listView.setBackgroundResource(R.drawable.ic_playlist_item_bg);
        listView.setOnItemClickListener(itemClickListener);
        tvTitle = (TextView) findViewById(R.id.tvTitle);
        btnPlayAll = (ImageButton) findViewById(R.id.btnPlayAll);
        btnPlayAll.setOnClickListener(listener);
        Intent in = getIntent();
        int type = in.getIntExtra("showType", 0);
        long id = in.getLongExtra("id", -1);
        title = in.getStringExtra("title");
        showType = ShowType.getTyepWithIntValue(type);
        adapter = new PlayListAdapter(this, this, null);
        listView.setAdapter(adapter);
        updateTitle(title);
        handlerShow(id);

        findViewById(R.id.imgBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshListView();
    }

    @Override
    protected void onStart() {
        super.onStart();
        registerStatusListener();
    }

    @Override
    protected void onStop() {
        super.onStop();
        unRegisterStatusListener();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        MusicUtils.unbindFromService(token);
    }

    private void updateTitle(String title) {
        tvTitle.setText(title);
    }

    private void refreshListView() {

        if (null != listView && listView.getVisibility() == View.VISIBLE) {
            listView.invalidateViews();
            if (MusicUtils.sService != null) {
                try {
                    long playAudioId = MusicUtils.sService.getAudioId();

                    if (-1 == playAudioId) {
                        return;
                    }
                    final int index = adapter.getAudioIdPosition(playAudioId);
                    Logger.i(TAG, "move to index:" + index);

                    if (index != -1) {

                        listView.post(new Runnable() {
                            @Override
                            public void run() {
                                listView.smoothScrollToPositionFromTop(index, 0);

                            }
                        });

                    }

                } catch (RemoteException e) {
                    return;
                }


            }
        }
    }

    private String[] project = {MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.DURATION, MediaStore.Audio.Media.SIZE};
    Uri uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;

    private void handlerShow(long id) {
        if (-1 == id) {
            //TODO 展示空列表
            return;
        }
        switch (showType) {
            case ARTIST: {

                String selection = MediaStore.Audio.Media.ARTIST_ID + " = ?";
                String[] args = {String.valueOf(id)};
                adapter.getQueryHandler().doQuery(uri, project, selection, args, null, true);
                //adapter.getQueryHandler().startQuery(ShowType.ARTIST.intValue(),null,uri,project,selection,args,null);
                break;
            }
            case ABLUM: {
                String selection = MediaStore.Audio.Media.ALBUM_ID + " = ? ";
                String[] args = {String.valueOf(id)};
                adapter.getQueryHandler().doQuery(uri, project, selection, args, null, true);
                break;
            }
            case LIST: {
                //查找播放列表所有歌曲时
                String[] mPlaylistMemberCols = new String[]{
                        MediaStore.Audio.Playlists.Members._ID,
                        MediaStore.Audio.Media.TITLE,
                        MediaStore.Audio.Media.DATA,
                        MediaStore.Audio.Media.ALBUM,
                        MediaStore.Audio.Media.ARTIST,
                        MediaStore.Audio.Media.ARTIST_ID,
                        MediaStore.Audio.Media.DURATION,
                        MediaStore.Audio.Playlists.Members.PLAY_ORDER,
                        MediaStore.Audio.Playlists.Members.AUDIO_ID,
                        MediaStore.Audio.Media.IS_MUSIC
                };
                Uri uri = MediaStore.Audio.Playlists.Members.getContentUri("external",
                        Long.valueOf(id));
                String mSortOrder = MediaStore.Audio.Playlists.Members.DEFAULT_SORT_ORDER;
                String where = MediaStore.Audio.Media.TITLE + " != ''";
                adapter.getQueryHandler().doQuery(uri, mPlaylistMemberCols, where, null, mSortOrder, true);
                break;
            }
            default:
                break;
        }
    }


    private void init(Cursor cursor, boolean isLimited) {
        adapter.changeCursor(cursor);
    }

    static class PlayListAdapter extends CursorAdapter {


        private MusicListsActivity mActivity;
        private QueryHandler queryHandler;
        private int mTitleIdx;
        private int mDurationIdx;
        private int mAudioIdIdx;
        private int mArtistIdx;

        private int getAudioIdPosition(long audioId) {
            int count = getCount();
            Logger.i("count" + count);
            for (int i = 0; i < count; i++) {
                Cursor mCursor = (Cursor) getItem(i);
                long mAudioId = mCursor.getLong(mAudioIdIdx);
                Logger.i(TAG, " mAudioId:" + mAudioId + " audioId:" + audioId + " , position:" + i);
                if (mAudioId == audioId) {
                    return mCursor.getPosition();
                }
            }
            return -1;
        }

        //在bindView 中每次都去获取index ，不太好。。。
        //我们在 构造器 和 指针发生改变的时候去更新index就可以了
        private void getColumnIndices(Cursor cursor) {
            if (cursor != null) {
                mTitleIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                mArtistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                mDurationIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION);
                try {
                    //列表Cursor
                    mAudioIdIdx = cursor.getColumnIndexOrThrow(
                            MediaStore.Audio.Playlists.Members.AUDIO_ID);
                } catch (IllegalArgumentException ex) {
                    //MediaCursor
                    mAudioIdIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                }
            }
        }


        PlayListAdapter(Context context, MusicListsActivity activity, Cursor c) {
            super(context, c);
            mActivity = activity;
            queryHandler = new QueryHandler(context.getContentResolver());
            getColumnIndices(c);
        }

        public QueryHandler getQueryHandler() {
            return queryHandler;
        }

        class QueryHandler extends AsyncQueryHandler {
            class QueryArgs {
                public Uri uri;
                public String[] projection;
                public String selection;
                public String[] selectionArgs;
                public String orderBy;
            }

            QueryHandler(ContentResolver cr) {
                super(cr);
            }

            public Cursor doQuery(Uri uri, String[] projection,
                                  String selection, String[] selectionArgs,
                                  String orderBy, boolean async) {
                if (async) {
                    // Get 100 results first, which is enough to allow the user to start scrolling,
                    // while still being very fast.
                    Uri limituri = uri.buildUpon().appendQueryParameter("limit", "100").build();
                    QueryArgs args = new QueryArgs();
                    args.uri = uri;
                    args.projection = projection;
                    args.selection = selection;
                    args.selectionArgs = selectionArgs;
                    args.orderBy = orderBy;

                    startQuery(0, args, limituri, projection, selection, selectionArgs, orderBy);
                    return null;
                }
                return MusicUtils.query(mActivity,
                        uri, projection, selection, selectionArgs, orderBy);
            }

            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                //Log.i("@@@", "query complete: " + cursor.getCount() + "   " + mActivity);
                mActivity.init(cursor, cookie != null);
                if (token == 0 && cookie != null && cursor != null && cursor.getCount() >= 100) {
                    QueryArgs args = (QueryArgs) cookie;
                    startQuery(1, null, args.uri, args.projection, args.selection,
                            args.selectionArgs, args.orderBy);
                }
            }

        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = mActivity.getLayoutInflater().inflate(R.layout.item_list_title_and_author_no_swiper, null);
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.tvMusicTitle = (TextView) v.findViewById(R.id.tvMusicTitle);
            viewHolder.tvMusicAuthor = (TextView) v.findViewById(R.id.tvMusicAuthor);
            viewHolder.imgPlayingIcon = (ImageView) v.findViewById(R.id.imgPlayingIcon);
            viewHolder.duration = (TextView) v.findViewById(R.id.tvDuration);
            v.setTag(viewHolder);
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ViewHolder viewHolder = (ViewHolder) view.getTag();
            String title = cursor.getString(mTitleIdx);
            String artist = cursor.getString(mArtistIdx);
            long duration = cursor.getLong(mDurationIdx);
            viewHolder.tvMusicTitle.setText(title);
            viewHolder.tvMusicAuthor.setText(artist);
            viewHolder.duration.setVisibility(View.VISIBLE);
            viewHolder.duration.setText(MusicUtils.makeTimeString(mActivity, duration / 1000));

            try {
                long audioId = cursor.getLong(mAudioIdIdx);
                long playId = MusicUtils.sService.getAudioId();
                if (audioId == playId) {
                    viewHolder.imgPlayingIcon.setVisibility(View.VISIBLE);
                } else {
                    viewHolder.imgPlayingIcon.setVisibility(View.GONE);
                }
            } catch (RemoteException e) {
            }


        }

        @Override
        public void changeCursor(Cursor cursor) {
            if (mActivity.isFinishing() && cursor != null) {
                cursor.close();
                cursor = null;
            }
            if (cursor != mActivity.mTrackCursor) {
                mActivity.mTrackCursor = cursor;
                super.changeCursor(cursor);
                getColumnIndices(cursor);
            }
        }

        static class ViewHolder {
            TextView tvMusicTitle;
            TextView tvMusicAuthor;
            TextView duration;
            ImageView imgPlayingIcon;
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {

        updatePlayAll();
    }

    private void updatePlayAll() {
        try {
            if (MusicUtils.sService != null) {
                switch (showType) {
                    case ARTIST: {
                        String playArtist = MusicUtils.sService.getArtistName();
                        Logger.i(TAG, "ARTIST TYPE playArtist:" + playArtist + " , title:" + title);
                        if (null != playArtist && playArtist.equals(title)) {
                            btnPlayAll.setImageResource(R.drawable.ic_h_list_pause);
                        } else {
                            btnPlayAll.setImageResource(R.drawable.ic_h_list_play);
                        }
                        break;
                    }
                    case ABLUM: {
                        String playAlbum = MusicUtils.sService.getAlbumName();
                        Logger.i(TAG, "ABLUM TYPE playAblum:" + playAlbum + " , title:" + title);
                        if (null != playAlbum && playAlbum.equals(title)) {
                            btnPlayAll.setImageResource(R.drawable.ic_h_list_pause);
                        } else {
                            btnPlayAll.setImageResource(R.drawable.ic_h_list_play);
                        }
                        break;
                    }
                    case LIST: {
                        Logger.i(TAG, "play list not update ");
                        break;
                    }
                    default:
                        break;

                }
            }
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    private void registerStatusListener() {
        IntentFilter f = new IntentFilter();
        f.addAction(PlayBackService.META_CHANGED);
        f.addAction(PlayBackService.QUEUE_CHANGED);
        registerReceiver(mTrackListListener, f);
    }

    private void unRegisterStatusListener() {
        unregisterReceiver(mTrackListListener);
    }

    private BroadcastReceiver mTrackListListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updatePlayAll();
            refreshListView();
        }
    };

}
