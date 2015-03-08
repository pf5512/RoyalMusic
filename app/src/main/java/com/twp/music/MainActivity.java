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

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;

import com.twp.music.fragment.FindMusicFragment;
import com.twp.music.fragment.MyMusicFragment;
import com.twp.music.service.PlayBackService;
import com.twp.music.util.Logger;
import com.twp.music.util.MusicUtils;
/**
 * Created by pengqinping on 15/3/8.
 *
 * @email Royal.k.peng@gmail.com
 * @description 主页面
 */
public class MainActivity extends FragmentActivity implements ServiceConnection {

    private static final String TAG = "MainActivity";
    private static final int GET_ALBUM_ART = 1;
    private static final int ALBUM_ART_DECODED = 2;
    private static final int REFRESH = 3;
    //Top nav
    private RadioButton radioFindMusic, radioMyMusic;
    //Bottom Nav
    private ImageView imgAristIcon;
    private ImageButton btnPlayList, btnPauseOrPlay;
    private TextView tvMusicTitle, tvMusicArtist;
    private ProgressBar progressBar;

    private FragmentManager fm;
    private MusicUtils.ServiceToken token;
    private IPlayBackService mService;
    private AlbumArtHandler albumArtHandler;

    private long mPosOverride = -1;
    private long mDuration = -1;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.i(TAG,"onCreate ---- ");
        setContentView(R.layout.activity_main);
        fm = getFragmentManager();
        initView();
        token = MusicUtils.bindToService(this, this);

        //start 子线程 ，维护子线程的消息队列，把 子线程的looper给handler
        HandlerThread alnumArtThread = new HandlerThread("Album Art HandlerThread ");
        alnumArtThread.start();
        albumArtHandler = new AlbumArtHandler(alnumArtThread.getLooper());
    }

    @Override
    protected void onStart() {
        Logger.i(TAG,"onStart ---- ");
        registerStatusListener();
        updateBNavInfo();
        super.onStart();
    }

    @Override
    protected void onStop() {
        Logger.i(TAG,"onStop ---- ");
        unRegisterStatusListener();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        Logger.i(TAG,"onDestory ---- ");
        albumArtHandler.getLooper().quit();
        if (null != token) {
            MusicUtils.unbindFromService(token);
        }
        super.onDestroy();
    }

    private void initView() {
        radioFindMusic = (RadioButton) findViewById(R.id.radioFindMusic);
        radioMyMusic = (RadioButton) findViewById(R.id.radioMyMusic);
        radioMyMusic.setOnCheckedChangeListener(radioCheckedListener);
        radioFindMusic.setOnCheckedChangeListener(radioCheckedListener);
        radioFindMusic.setChecked(true);
        radioMyMusic.setChecked(false);


        //init bottom
        imgAristIcon = (ImageView) findViewById(R.id.imgArtistIcon);
        btnPlayList = (ImageButton) findViewById(R.id.btnPlayList);
        btnPauseOrPlay = (ImageButton) findViewById(R.id.btnPauseOrPlay);
        tvMusicTitle = (TextView) findViewById(R.id.tvMusicTitle);
        tvMusicArtist = (TextView) findViewById(R.id.tvMusicAuthor);
        progressBar = (ProgressBar) findViewById(R.id.progressBar);
        //end init bottom

        //set onclickListener

        btnPlayList.setOnClickListener(bNavClickListener);
        btnPauseOrPlay.setOnClickListener(bNavClickListener);

        changePage(PageType.FINDMUSIC);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Logger.i(TAG,"onNewIntent");
    }

    private void updateBNavInfo() {
        if (mService != null) {
            try {
                Logger.i(TAG,"mService.getAudioId() = "+mService.getAudioId());
                if(mService.getAudioId() != -1) {
                    tvMusicTitle.setText(mService.getTrackName());
                    tvMusicArtist.setText(mService.getArtistName());
                    btnPauseOrPlay.setImageResource(mService.isPlaying() ? R.drawable.ic_h_music_btn_pause : R.drawable.ic_h_music_btn_play);

                    // send msg to get ablumArt.
                    albumArtHandler.removeMessages(GET_ALBUM_ART);
                    albumArtHandler.obtainMessage(GET_ALBUM_ART, new AlbumSongIdWrapper(mService.getAlbumId(), mService.getAudioId())).sendToTarget();

                    mDuration = mService.duration();
                    //update progressbar.
                    queueNextRefresh(1);
                    Logger.i("Music start play!");
                    findViewById(R.id.bottomPlay).setVisibility(View.VISIBLE);
                    findViewById(R.id.content).setPadding(0, (int) getResources().getDimension(R.dimen.h_nav_height), 0, (int) getResources().getDimension(R.dimen.h_music_pre_height));
                    return;
                }
            } catch (RemoteException e) {

            }

            Logger.i("Music not start play!");
            findViewById(R.id.bottomPlay).setVisibility(View.GONE);
            findViewById(R.id.content).setPadding(0,(int)getResources().getDimension(R.dimen.h_nav_height),0,0);
        }
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
            updateBNavInfo();
        }
    };

    public void jumpPlayMusicActivity(View view) {
        startActivity(new Intent(this, PlayMusicActivity.class));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void changePage(PageType type) {
        Fragment fg = null;
        switch (type) {

            case FINDMUSIC:
                fg = new FindMusicFragment();
                break;
            case MYMUSIC:
                fg = new MyMusicFragment();
                break;
            default:

                break;
        }

        FragmentTransaction ft = fm.beginTransaction();
        ft.replace(R.id.content, fg);
        ft.commit();
    }

    private OnCheckedChangeListener radioCheckedListener = new OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView,
                                     boolean isChecked) {
            if (isChecked) {

                int id = buttonView.getId();
                if (PageType.FINDMUSIC.id == id) {
                    Logger.i("findMusic Checked!");
                    changePage(PageType.FINDMUSIC);
                } else if (PageType.MYMUSIC.id == id) {
                    changePage(PageType.MYMUSIC);
                    Logger.i("MyMusic Checked!");
                }
            }
        }

    };

    private enum PageType {
        FINDMUSIC(R.id.radioFindMusic), MYMUSIC(R.id.radioMyMusic);

        private PageType(int id) {
            this.id = id;
        }

        public int id;

    }


    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {

        mService = IPlayBackService.Stub.asInterface(service);
        updateBNavInfo();

    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mService = null;
    }


    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case ALBUM_ART_DECODED:
                    MusicUtils.scaleWithImageView(imgAristIcon, (Bitmap) msg.obj);
                    imgAristIcon.getDrawable().setDither(true);
                    break;

                case REFRESH:
                    long next = refreshNow();
                    queueNextRefresh(next);
                    break;

                default:
                    break;
            }
        }
    };


    private View.OnClickListener  bNavClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Logger.i(TAG,"bNavClickListener onClick");
            if (mService == null) return;
            switch (v.getId()) {
                case R.id.btnPauseOrPlay: {
                    doPlayOrPause();
                    break;
                }
                case R.id.btnPlayList: {
                    try {
                        mService.next();
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                    break;
                }
                default:
                    break;
            }

        }
    };

    private void doPlayOrPause() {
        try {
            boolean playStatus = mService.isPlaying();
            Logger.i("handler play playStatus:"+playStatus);
            if (playStatus) {
                mService.pause();
            } else {
                mService.play();
            }
            updateBNav(!playStatus);
        } catch (RemoteException e) {

        }
    }

    private void updateBNav(boolean flag){
        btnPauseOrPlay.setImageResource(flag?R.drawable.ic_h_music_btn_pause:R.drawable.ic_h_music_btn_play);
    }

    private void queueNextRefresh(long delay) {
        Message msg = mHandler.obtainMessage(REFRESH);
        mHandler.removeMessages(REFRESH);
        mHandler.sendMessageDelayed(msg, delay);
    }

    private long refreshNow() {
        if (mService == null)
            return 500;
        try {
            long pos = mPosOverride < 0 ? mService.position() : mPosOverride;
            //Logger.i(TAG, "pos:" + pos + " , mPosOverride:" + mPosOverride);
            if ((pos >= 0) && (mDuration > 0)) {

                //(pos / mDuration) 表示总数的百分比， *1000 表示 在seekbar 的1000 份中显示多少
                int progress = (int) (1000 * pos / mDuration);
                progressBar.setProgress(progress);

            } else {
                progressBar.setProgress(1000);
            }
            // calculate the number of milliseconds until the next full second, so
            // the counter can be updated at just the right time
            long remaining = 1000 - (pos % 1000);// pos 加上多少刚好 %1000 等于0

            // approximate how often we would need to refresh the slider to
            // move it smoothly
            int width = progressBar.getWidth();
            if (width == 0) width = 320;

            //width 的每一个像素可以代表 mDuration 中的多长
            long smoothrefreshtime = mDuration / width;

            if (smoothrefreshtime > remaining) return remaining;
            if (smoothrefreshtime < 20) return 20;
            return smoothrefreshtime;
        } catch (RemoteException ex) {
        }
        return 500;
    }

    /**
     * 获取专辑图片封面使用后台线程获取，
     * AlbumArtHandler 为子线程handler,在这个Activity中只做一件事，获取专辑图片
     */
    public class AlbumArtHandler extends Handler {

        //避免相同专辑封面获取两次
        private long mAlbumId = -1;

        public AlbumArtHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            long albumid = ((AlbumSongIdWrapper) msg.obj).albumid;
            long songid = ((AlbumSongIdWrapper) msg.obj).songid;
            if (msg.what == GET_ALBUM_ART && (mAlbumId != albumid || albumid < 0)) {
                // while decoding the new image, show the default album art
                Message numsg = mHandler.obtainMessage(ALBUM_ART_DECODED, null);
                mHandler.removeMessages(ALBUM_ART_DECODED);
                mHandler.sendMessageDelayed(numsg, 300);
                // Don't allow default artwork here, because we want to fall back to song-specific
                // album art if we can't find anything for the album.
                Bitmap bm = MusicUtils.getArtwork(MainActivity.this, songid, albumid, false);
                if (bm == null) {
                    bm = MusicUtils.getArtwork(MainActivity.this, songid, -1);
                    albumid = -1;
                }
                if (bm != null) {
                    numsg = mHandler.obtainMessage(ALBUM_ART_DECODED, bm);
                    mHandler.removeMessages(ALBUM_ART_DECODED);
                    mHandler.sendMessage(numsg);
                }
                mAlbumId = albumid;
            } else {
                Logger.i("not need to get album");
            }
        }
    }

    private static class AlbumSongIdWrapper {
        public long albumid;
        public long songid;

        AlbumSongIdWrapper(long aid, long sid) {
            albumid = aid;
            songid = sid;
        }
    }

}
