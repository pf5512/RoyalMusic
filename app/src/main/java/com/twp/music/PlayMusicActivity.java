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
import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AdapterView;
import android.widget.CompoundButton;
import android.widget.CursorAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SectionIndexer;
import android.widget.SeekBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

import com.twp.music.service.PlayBackService;
import com.twp.music.ui.lrc.DefaultLrcBuilder;
import com.twp.music.ui.lrc.ILrcBuilder;
import com.twp.music.ui.lrc.ILrcView;
import com.twp.music.ui.lrc.LrcRow;
import com.twp.music.ui.lrc.LrcView;
import com.twp.music.util.ListAlphabetIndexer;
import com.twp.music.util.Logger;
import com.twp.music.util.MusicUtils;

import java.util.Arrays;
import java.util.List;

/**
 * Created by pengqinping on 15/3/8.
 *
 * @email Royal.k.peng@gmail.com
 * @description 播放页面
 */
public class PlayMusicActivity extends Activity implements ServiceConnection {

    private static final String TAG = "PlayMusicActivity";
    private static final int HANDLE_EIXT = 1;
    private static final int HANDLE_REFRESH = 2;
    private static final int HANDLE_SETBACK = 3;
    private static final int HANDLE_GET_ALBUM_ART = 4;

    private MusicUtils.ServiceToken serviceToken;
    private IPlayBackService mService = null;
    //主线程handler
    private Handler mHandler = new Handler() {
        @Override
        public void dispatchMessage(Message msg) {
            super.dispatchMessage(msg);
            switch (msg.what) {
                case HANDLE_EIXT:
                    showServiceErrorDialog();
                    break;
                case HANDLE_REFRESH:
                    updateSeekBarAndText();
                    break;
                case HANDLE_SETBACK:
                    MusicUtils.setBackground(rootView, (Bitmap) msg.obj);
                    break;
                case HANDLE_GET_ALBUM_ART:
                    Logger.i(TAG, "get album Success !");
                    if (msg.obj != null) {
                        MusicUtils.scaleWithImageView(albumImage, (Bitmap) msg.obj);
                        albumImage.getDrawable().setDither(true);
                    }
                    break;
                default:
                    break;
            }
        }
    };
    private HandlerThread mWorkThread;
    private WorkHandler mWorkHandler;
    //seekbar 是否开始touch
    private boolean mFromTouch = false;
    private long mLastSeekEventTime;
    private SeekBar.OnSeekBarChangeListener mSeekListener = new SeekBar.OnSeekBarChangeListener() {
        public void onStartTrackingTouch(SeekBar bar) {
            mLastSeekEventTime = 0;
            mFromTouch = true;
        }

        public void onProgressChanged(SeekBar bar, int progress, boolean fromuser) {
            if (!fromuser || (mService == null)) return;
            long now = SystemClock.elapsedRealtime();
            //改变事件间隔大于 250 ms 才去调用接口 seek
            if ((now - mLastSeekEventTime) > 250) {
                mLastSeekEventTime = now;
                mPosOverride = mDuration * progress / 1000;
                try {
                    mService.seek(mPosOverride);
                } catch (RemoteException ex) {
                }

                // trackball event, allow progress updates
                if (!mFromTouch) {
                    refreshNow();
                    mPosOverride = -1;
                }
            }
        }

        public void onStopTrackingTouch(SeekBar bar) {
            mPosOverride = -1;
            mFromTouch = false;
        }
    };
    private View.OnClickListener topNavListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.imgBack: {
                    finish();
                }
                break;
                case R.id.imgList:
                    break;
                default:
                    break;

            }
        }
    };

    private View.OnClickListener bNavListner = new View.OnClickListener() {

        @Override
        public void onClick(View v) {
            if (mService == null) return;
            switch (v.getId()) {
                case R.id.btnPlayBefore: {
                    //play before
                    try {
                        //FIXME 为什么要做这个处理? 小于2s 才给播放上一首
                        if (mService.position() < 2000) {
                            mService.prev();
                        } else {
                            mService.seek(0);
                            mService.play();
                        }
                    } catch (RemoteException ex) {
                    }
                }
                break;
                case R.id.btnPlayOrPause:
                    //播放或者暂停，根据播放器状态来判断
                    doPlayOrPause();
                    break;
                case R.id.btnPlayNext: {
                    //play nex
                    try {
                        mService.next();
                    } catch (RemoteException ex) {
                    }
                }
                break;
                case R.id.btnPlayMode: {
                    doNextMode();
                }
                break;
                default:
                    break;
            }
        }
    };

    //PlayService 播放状态发生改变后，会发送广播，我们在这里处理改变
    private BroadcastReceiver mStatusListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(PlayBackService.META_CHANGED)) {
                // redraw the artist/title info and
                // set new max for progress bar
                updateInfo();
                queueNextRefresh(1);
                updateListView();

            } else if (action.equals(PlayBackService.QUEUE_CHANGED)) {
                //列表改变刷新列表
                updateContent();
            }
        }
    };


    class AblumInfo {
        AblumInfo(long audioId, long ablumId) {
            this.audioId = audioId;
            this.ablumId = ablumId;
        }

        public long audioId;
        public long ablumId;
    }

    //Work Handler 处理耗时操作
    class WorkHandler extends Handler {


        protected WorkHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLE_SETBACK:
                    setAlbumArtBackground();
                    break;
                case HANDLE_GET_ALBUM_ART:
                    AblumInfo info = (AblumInfo) msg.obj;
                    Logger.i(TAG, "info.ablumId:" + info.ablumId + " , mAblumId:" + mAblumId);
                    if (msg.what == HANDLE_GET_ALBUM_ART && (mAblumId != info.ablumId || info.ablumId < 0)) {
                        // while decoding the new image, show the default album art
                        Message numsg = mHandler.obtainMessage(HANDLE_GET_ALBUM_ART, null);
                        mHandler.removeMessages(HANDLE_GET_ALBUM_ART);
                        mHandler.sendMessageDelayed(numsg, 300);
                        // Don't allow default artwork here, because we want to fall back to song-specific
                        // album art if we can't find anything for the album.
                        Bitmap bm = MusicUtils.getArtwork(PlayMusicActivity.this, info.audioId, info.ablumId, false);
                        if (bm == null) {
                            bm = MusicUtils.getArtwork(PlayMusicActivity.this, info.ablumId, -1);
                            mAblumId = -1;
                        }
                        if (bm != null) {
                            numsg = mHandler.obtainMessage(HANDLE_GET_ALBUM_ART, bm);
                            mHandler.removeMessages(HANDLE_GET_ALBUM_ART);
                            mHandler.sendMessage(numsg);
                        }
                        mAblumId = info.ablumId;
                    } else {
                        Logger.i(TAG, "not need to get album");
                    }
                    break;
                default:
                    break;
            }
        }
    }

    ;

    private RadioButton.OnCheckedChangeListener radioGroupChanged = new RadioButton.OnCheckedChangeListener() {

        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                int index = Arrays.binarySearch(radioIds, buttonView.getId());
                if (index > -1) {
                    Logger.i("radio Checked:index:" + index);
                    if (null != content) {
                        content.setDisplayedChild(index);
                    }
                }
            }
        }
    };

    GestureDetector mGestureDetector = new GestureDetector(new GestureDetector.OnGestureListener() {
        @Override
        public boolean onDown(MotionEvent e) {
            return false;
        }

        @Override
        public void onShowPress(MotionEvent e) {

        }

        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return false;
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            return false;
        }

        @Override
        public void onLongPress(MotionEvent e) {

        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (content == null) {
                return false;
            }
            if (e2.getX() - e1.getX() > 70) {             // 从左向右滑动（左进右出）
                Animation rInAnim = AnimationUtils.loadAnimation(PlayMusicActivity.this, R.anim.push_right_in);    // 向右滑动左侧进入的渐变效果（alpha  0.1 -> 1.0）
                Animation rOutAnim = AnimationUtils.loadAnimation(PlayMusicActivity.this, R.anim.push_right_out); // 向右滑动右侧滑出的渐变效果（alpha 1.0  -> 0.1）

                content.setInAnimation(rInAnim);
                content.setOutAnimation(rOutAnim);
                content.showPrevious();
                int index = content.getDisplayedChild();
                Logger.i(TAG, "view flipper checked index:" + index);
                if (index == 0) {
                    //需要等待界面展示
//                    updateListView();
                }
                radioGraoup.check(radioIds[index]);
            } else if (e2.getX() - e1.getX() < -70) {         // 从右向左滑动（右进左出）
                Animation lInAnim = AnimationUtils.loadAnimation(PlayMusicActivity.this, R.anim.push_left_in);        // 向左滑动左侧进入的渐变效果（alpha 0.1  -> 1.0）
                Animation lOutAnim = AnimationUtils.loadAnimation(PlayMusicActivity.this, R.anim.push_left_out);    // 向左滑动右侧滑出的渐变效果（alpha 1.0  -> 0.1）

                content.setInAnimation(lInAnim);
                content.setOutAnimation(lOutAnim);
                content.showNext();
                int index = content.getDisplayedChild();
                Logger.i(TAG, "view flipper checked index:" + index);
                if (index == 0) {
//                    updateListView();
                }
                radioGraoup.check(radioIds[index]);
            }
            //其他情况自己不处理，交给系统去分配
            return false;
        }
    });

    private LinearLayout rootView;
    private ImageView imgBack, imgList;
    private TextView tvMusicTitle, tvMusicAuthor, tvPlayedTime, tvTotalTime;
    private ImageButton btnPre, btnPauseOrPlay, btnNext, btnPlayMode, btnPlayLike, btnPlayDownLoad;
    private SeekBar sbMusicProgress;
    private ViewFlipper content;
    private RadioButton radio1, radio2, radio3;
    private RadioGroup radioGraoup;
    private ListView playListView;
    private ImageView albumImage;
    private LrcView mLrcView;
    private MusicApdater adapter;

    //activity是否进入到pause状态.
    private boolean isPause;

    //Gets the duration of the file, ms
    private long mDuration;
    private long mAblumId = -1;
    //当前位置,记得初始值为 -1
    private long mPosOverride = -1;
    //播放模式 对应 显示的String
    private String[] modeToastStr = new String[3];
    //播放模式对象的ResId
    private int[] modeResId = new int[3];
    private int[] radioIds = new int[3];
    private String[] mCursorCols;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //设置在这个Activity的时候，音量键控制的是媒体的音量
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.activity_play_music);
        mWorkThread = new HandlerThread("PlayMusic Work HandlerThread");
        mWorkThread.start();
        mWorkHandler = new WorkHandler(mWorkThread.getLooper());
        initData();
        initView();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mWorkThread != null) {
            mWorkThread.quit();
        }
    }


    private void initData() {
        modeToastStr[0] = getResources().getString(R.string.normalPlay);
        modeToastStr[1] = getResources().getString(R.string.nonePlay);
        modeToastStr[2] = getResources().getString(R.string.shufflePlay);
        modeResId[0] = R.drawable.ic_p_mode_list;
        modeResId[1] = R.drawable.ic_p_mode_single;
        modeResId[2] = R.drawable.ic_p_mode_random;
        radioIds[0] = R.id.radio1;
        radioIds[1] = R.id.radio2;
        radioIds[2] = R.id.radio3;


        mCursorCols = new String[]{
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ARTIST_ID,
                MediaStore.Audio.Media.DURATION
        };

    }

    private void initView() {
        imgBack = (ImageView) findViewById(R.id.imgBack);
        imgList = (ImageView) findViewById(R.id.imgList);
        tvMusicTitle = (TextView) findViewById(R.id.tvMusicTitle);
        tvMusicAuthor = (TextView) findViewById(R.id.tvMusicAuthor);
        tvPlayedTime = (TextView) findViewById(R.id.tvPlayedTime);
        tvTotalTime = (TextView) findViewById(R.id.txTotalTime);

        btnPre = (ImageButton) findViewById(R.id.btnPlayBefore);
        btnPauseOrPlay = (ImageButton) findViewById(R.id.btnPlayOrPause);
        btnNext = (ImageButton) findViewById(R.id.btnPlayNext);
        btnPlayMode = (ImageButton) findViewById(R.id.btnPlayMode);
        btnPlayLike = (ImageButton) findViewById(R.id.btnPlayLike);
        btnPlayDownLoad = (ImageButton) findViewById(R.id.btnPlayDownLoad);

        sbMusicProgress = (SeekBar) findViewById(R.id.sbMusicProgress);
        sbMusicProgress.setMax(1000);
        sbMusicProgress.setOnSeekBarChangeListener(mSeekListener);

        content = (ViewFlipper) findViewById(R.id.content);

        //set Listener
        imgBack.setOnClickListener(topNavListener);
        imgList.setOnClickListener(topNavListener);

        //
        btnPre.setOnClickListener(bNavListner);
        btnPauseOrPlay.setOnClickListener(bNavListner);
        btnNext.setOnClickListener(bNavListner);
        btnPlayMode.setOnClickListener(bNavListner);

        rootView = (LinearLayout) findViewById(R.id.rootView);

        radio1 = (RadioButton) findViewById(radioIds[0]);
        radio2 = (RadioButton) findViewById(radioIds[1]);
        radio3 = (RadioButton) findViewById(radioIds[2]);

        radio1.setOnCheckedChangeListener(radioGroupChanged);
        radio2.setOnCheckedChangeListener(radioGroupChanged);
        radio3.setOnCheckedChangeListener(radioGroupChanged);

        radioGraoup = (RadioGroup) findViewById(R.id.radioGraoup);
        //fill content
        fillContent();


    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return mGestureDetector.onTouchEvent(event);
    }

    private void setAlbumArtBackground() {
        try {
            long albumid = Long.valueOf(mAblumId);
            Bitmap bm = MusicUtils.getArtwork(PlayMusicActivity.this, -1, albumid, false);
            if (bm != null) {
                mHandler.sendMessage(mHandler.obtainMessage(HANDLE_SETBACK, bm));
                return;
            }
        } catch (Exception ex) {
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        isPause = false;
        serviceToken = MusicUtils.bindToService(this, this);
        if (serviceToken == null) {
            mHandler.sendEmptyMessage(HANDLE_EIXT);
        }
        registerStatusReceiver();
        updateInfo();
        updateContent();
    }

    //跟新背景图片和，专辑图片
    private void updateBackgroud(long songid, long ablumId) {
       /* mWorkHandler.removeMessages(HANDLE_SETBACK);
        mWorkHandler.sendEmptyMessage(HANDLE_SETBACK);*/
        Logger.i("enter updateBackgroud. songid:" + songid + " ,ablumId:" + ablumId);
        mWorkHandler.removeMessages(HANDLE_GET_ALBUM_ART);
        mWorkHandler.sendMessage(mWorkHandler.obtainMessage(HANDLE_GET_ALBUM_ART, new AblumInfo(songid, ablumId)));
    }

    //切换音乐时候跟新歌词
    private void updateLrc() {

    }

    //播放列表发送变化的时候更新列表
    private void updateContent() {
        Logger.i(TAG, "update playlist adapter:" + adapter + " ,mSerivce:" + mService);
        if (mService == null) return;
        if (null != adapter) {
            long[] mNowPlaying;
            int mSize = 0;
            try {
                mNowPlaying = mService.getQueue();
            } catch (RemoteException ex) {
                mNowPlaying = new long[0];
            }
            mSize = mNowPlaying.length;
            if (mSize == 0) {
                return;
            }

            StringBuilder where = new StringBuilder();
            where.append(MediaStore.Audio.Media._ID + " IN (");
            for (int i = 0; i < mSize; i++) {
                where.append(mNowPlaying[i]);
                if (i < mSize - 1) {
                    where.append(",");
                }
            }
            where.append(")");

            //使用同步查询
            adapter.getQueryHandler().doQuery(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, mCursorCols, where.toString(), null, MediaStore.Audio.Media._ID, true);

        }
    }

    private void fillContent() {
        playListView = (ListView) getLayoutInflater().inflate(R.layout.common_play_list, null).findViewById(R.id.listViewLocalMusic);
        albumImage = new ImageView(this);
        mLrcView = (LrcView) getLayoutInflater().inflate(R.layout.common_lrc, null).findViewById(R.id.lrcView);
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.FILL_PARENT);
        content.addView(playListView, params);
        albumImage.setLayoutParams(params);
        content.addView(albumImage, params);
        content.addView(mLrcView, params);
        content.setDisplayedChild(1);
        Logger.i(TAG, "Content add view success chilCount:" + content.getChildCount());
        adapter = new MusicApdater(this, this, null, null, null);
        playListView.setAdapter(adapter);
        playListView.setOnItemClickListener(playListItemClick);

        content.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom) {
                if(v instanceof ViewFlipper){
                    ViewFlipper vf = (ViewFlipper)v;
                    int index = vf.getDisplayedChild();
                    if(index == 2){
                        loadLrc();
                    }
                }
            }
        });

    }

    private AdapterView.OnItemClickListener playListItemClick = new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

            try {
                mService.setQueuePosition(position);
            } catch (RemoteException e) {

            }

        }
    };

    @Override
    protected void onStop() {
        isPause = true;
        mHandler.removeMessages(HANDLE_REFRESH);
        unRegisterStatusReceiver();
        MusicUtils.unbindFromService(serviceToken);
        mService = null;
        super.onStop();
    }

    private String mTitle = "";
    private String mArtist = "";
    private void loadLrc(){
        if("".equals(mTitle)||"".equals(mArtist)){
            Logger.i("Current not music info ");
        }
        ILrcBuilder builder = new DefaultLrcBuilder();
        String lrc = builder.getLrcForSdcard(mTitle+"-"+mArtist);
        List<LrcRow> rows = builder.getLrcRows(lrc);
        mLrcView.setLrc(rows);
        beginLrcPlay();

        mLrcView.setListener(new ILrcView.LrcViewListener() {

            public void onLrcSeeked(int newPosition, LrcRow row) {
                if (mService != null) {
                    Logger.d(TAG, "onLrcSeeked:" + row.time);

                    try {
                        mService.seek(row.time);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void beginLrcPlay(){
        queueNextRefresh(1);
    }
    /**
     * 注册播放状态改变广播
     * action1 : com.twp.music.metachanged;
     * action2 : com.twp.music.playstatechanged;
     */
    private void registerStatusReceiver() {
        IntentFilter f = new IntentFilter();
        f.addAction(PlayBackService.PLAYSTATE_CHANGED);
        f.addAction(PlayBackService.META_CHANGED);
        registerReceiver(mStatusListener, new IntentFilter(f));
    }

    /**
     * 反注册播放状态改变广播，在onstop里面调用
     */
    private void unRegisterStatusReceiver() {
        unregisterReceiver(mStatusListener);
    }

    //更新界面信息
    private void updateInfo() {
        if (mService == null) {
            Logger.i("updateInfo enter , mService is null!");
            return;
        }

        try {
            String path = mService.getPath();
            if (path == null) {
                finish();
                return;
            }

            long songid = mService.getAudioId();

            if (songid < 0 && path.toLowerCase().startsWith("http://")) {
                //网络地址处理
            } else {
                //content播放
            }

            //跟新界面显示
            updateTopNav(mService.getTrackName(), mService.getArtistName());
            updateBNav(mService.isPlaying());
            mDuration = mService.duration();
            tvTotalTime.setText(MusicUtils.makeTimeString(this, mDuration / 1000));
            updateSeekBarAndText();
            updateBackgroud(mService.getAudioId(), mService.getAlbumId());
            updateLrc();
            updateListView();
            udpatePlayMode();
        } catch (RemoteException e) {
            finish();
        }

    }

    //目前只有在一个地方调用了
    private void updateListView() {


        if (playListView != null &&  MusicUtils.sService != null) {
           playListView.invalidateViews();
            try {
                final long playAudioId = MusicUtils.sService.getAudioId();

                if (playAudioId != -1) {
                    final int index = adapter.getPlayIndexOfAudioId(playAudioId);
                    if (-1 != index) {
                        Logger.i(TAG, "current playList move to index:" + index);
                        playListView.post(new Runnable() {
                            @Override
                            public void run() {
                                playListView.smoothScrollToPositionFromTop(index, 0,index*20);
                                playListView.requestFocusFromTouch();
                            }
                        });

                    }
                }


            } catch (RemoteException e) {

            }
        }


    }

    private void updateSeekBarAndText() {
        long delay = refreshNow();
        queueNextRefresh(delay);
    }

    private void queueNextRefresh(long delay) {
        if (!isPause) {
            Message msg = mHandler.obtainMessage(HANDLE_REFRESH);
            mHandler.removeMessages(HANDLE_REFRESH);
            mHandler.sendMessageDelayed(msg, delay);
        }
    }

    private long refreshNow() {
        if (mService == null)
            return 500;
        try {
            long pos = mPosOverride < 0 ? mService.position() : mPosOverride;
            //Logger.i(TAG, "pos:" + pos + " , mPosOverride:" + mPosOverride);
            if ((pos >= 0) && (mDuration > 0)) {
                String strTime = MusicUtils.makeTimeString(this, pos / 1000);
                tvPlayedTime.setText(strTime);
                //Logger.i(TAG, "tvPlayedTime.setText:" + strTime);
                //(pos / mDuration) 表示总数的百分比， *1000 表示 在seekbar 的1000 份中显示多少
                int progress = (int) (1000 * pos / mDuration);
                sbMusicProgress.setProgress(progress);

                if (mService.isPlaying()) {
                    tvPlayedTime.setVisibility(View.VISIBLE);
                } else {
                    // blink the counter
                    int vis = tvPlayedTime.getVisibility();
                    tvPlayedTime.setVisibility(vis == View.INVISIBLE ? View.VISIBLE : View.INVISIBLE);
                    return 500;
                }
                if(mLrcView!=null){
                    mLrcView.seekLrcToTime(pos);
                }
            } else {
                tvPlayedTime.setText("--:--");
                sbMusicProgress.setProgress(1000);
            }
            // calculate the number of milliseconds until the next full second, so
            // the counter can be updated at just the right time
            long remaining = 1000 - (pos % 1000);// pos 加上多少刚好 %1000 等于0

            // approximate how often we would need to refresh the slider to
            // move it smoothly
            int width = sbMusicProgress.getWidth();
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


    private void updateTopNav(String trackName, String artistName) {
        Logger.i(TAG, "refresh TopNav status . trackName:" + trackName + " , artistName:" + artistName);
        mTitle = trackName;
        mArtist = artistName;
        tvMusicTitle.setText(trackName);
        tvMusicAuthor.setText(artistName);
    }

    private void doPlayOrPause() {
        try {
            boolean playStatus = mService.isPlaying();
            if (playStatus) {
                mService.pause();
            } else {
                mService.play();
            }
            updateBNav(!playStatus);
        } catch (RemoteException e) {

        }
    }

    /**
     * 切换到下一个播放模式
     */
    private void doNextMode() {
        if (mService == null) {
            return;
        }

        try {
            int mode = mService.getPlayMode();
            Logger.i(TAG, "Current Play (1=list,2=none,3=random)Mode:" + mode);
            mode++;
            //如果是随机播放模式，重置为 列表模式
            if (mode >= 4) {
                mode = 1;
            }
            mService.setPlayMode(mode);

            int index = mode - 1;
            btnPlayMode.setImageResource(modeResId[index]);
            Toast.makeText(PlayMusicActivity.this, modeToastStr[(index)], Toast.LENGTH_LONG).show();

        } catch (RemoteException e) {

        }

    }

    private void udpatePlayMode(){
        try {
            int mode = mService.getPlayMode();
            btnPlayMode.setImageResource(modeResId[mode-1]);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    private void updateBNav(boolean isPlaying) {
        int resId = isPlaying ? R.drawable.ic_p_pause_btn : R.drawable.ic_p_play_btn;
        btnPauseOrPlay.setImageResource(resId);
    }

    private void startPlayback() {

        if (mService == null)
            return;
        Intent intent = getIntent();
        String filename = "";
        Uri uri = intent.getData();
        //属于指定地址播放
        if (uri != null && uri.toString().length() > 0) {
            // If this is a file:// URI, just use the path directly instead
            // of going through the open-from-filedescriptor codepath.
            String scheme = uri.getScheme();
            if ("file".equals(scheme)) {
                filename = uri.getPath();
            } else {
                filename = uri.toString();
            }
            try {
                mService.stop();
                mService.openFile(filename);
                mService.play();
                setIntent(new Intent());
            } catch (Exception ex) {
                Logger.d("couldn't start playback: " + ex);
            }
        }

        updateInfo();
        updateContent();
    }

    private void showServiceErrorDialog() {
        // This can be moved back to onCreate once the bug that prevents
        // Dialogs from being started from onCreate/onResume is fixed.
        new AlertDialog.Builder(PlayMusicActivity.this)
                .setTitle(R.string.service_start_error_title)
                .setMessage(R.string.service_start_error_msg)
                .setPositiveButton(R.string.yesDelete,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                finish();
                            }
                        })
                .setCancelable(false)
                .show();
    }


    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        mService = IPlayBackService.Stub.asInterface(service);
        startPlayback();
        try {
            // Assume something is playing when the service says it is,
            // but also if the audio ID is valid but the service is paused.
            if (mService.getAudioId() >= 0 || mService.isPlaying() ||
                    mService.getPath() != null) {
                // something is playing now, we're done

                return;
            }
        } catch (RemoteException ex) {
        }
        // Service is dead or not playing anything. If we got here as part
        // of a "play this file" Intent, exit. Otherwise go to the Music
        // app start screen.
        if (getIntent().getData() == null) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setClass(PlayMusicActivity.this, LocalMusicActivity.class);
            startActivity(intent);
        }
        finish();
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        mService = null;
    }


    private void updateCursor(Cursor cursor) {
        adapter.changeCursor(cursor);
        adapter.updateColum(cursor);
        updateQueue(cursor);
    }

    private void updateQueue(Cursor cursor) {
        //第二次查询完成后更新下播放列表，看数据库中是否有删除的文件
        if (cursor != null) {
            final Cursor mCursor = cursor;
            int size = mCursor.getCount();
            //当前数据库中存储的最新id
            long[] mCursorIdxs = new long[size];
            mCursor.moveToFirst();
            int indexID = mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
            for (int i = 0; i < size; i++) {
                mCursorIdxs[i] = mCursor.getLong(indexID);
                mCursor.moveToNext();
            }
            // At this point we can verify the 'now playing' list we got
            // earlier to make sure that all the items in there still exist
            // in the database, and remove those that aren't. This way we
            // don't get any blank items in the list.
            try {
                int removed = 0;
                long[] mNowPlaying = mService.getQueue();
                for (int i = mNowPlaying.length - 1; i >= 0; i--) {
                    long trackid = mNowPlaying[i];
                    int crsridx = Arrays.binarySearch(mCursorIdxs, trackid);
                    if (crsridx < 0) {
                        //Log.i("@@@@@", "item no longer exists in db: " + trackid);
                        removed += mService.removeTrack(trackid);
                    }
                }
                if (removed > 0) {
                    //do somethings
                }
            } catch (RemoteException ex) {
            }


        }
    }


    static class MusicApdater extends CursorAdapter {

        private ListAlphabetIndexer indexer;
        private int mTitleIndex;
        private int mArtistIdx;

        private PlayMusicActivity mActivity;
        private MusicQueryHandler mQueryHandler;

        class MusicQueryHandler extends AsyncQueryHandler {

            class QueryArgs {
                public Uri uri;
                public String[] projection;
                public String selection;
                public String[] selectionArgs;
                public String orderBy;
            }

            MusicQueryHandler(ContentResolver cr) {
                super(cr);
            }

            /**
             * @param uri
             * @param projection
             * @param selection
             * @param selectionArgs
             * @param orderBy
             * @param async         是否是异步查询
             * @return
             */
            public Cursor doQuery(Uri uri, String[] projection,
                                  String selection, String[] selectionArgs,
                                  String orderBy, boolean async) {
                //如果是异步查询 ,首先查询100条刷新界面，然后再等待查询完成后再查下剩下的。
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
                //不是异步查询直接在Activity中查询
                return MusicUtils.query(mActivity,
                        uri, projection, selection, selectionArgs, orderBy);
            }

            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                super.onQueryComplete(token, cookie, cursor);
                Logger.i(TAG, "playList query success token:" + token + " cursor:" + cursor);
                if (cursor != null) {
                    cursor.moveToFirst();
                    Logger.i(TAG, "cursor count:" + cursor.getCount());
                    changeCursor(cursor);
                    //查询完成后，需要刷新列表移懂正则播放的item到top
                    mActivity.updateListView();
                }

                if (token == 0 && cookie != null && cursor != null && cursor.getCount() >= 100) {
                    QueryArgs args = (QueryArgs) cookie;
                    startQuery(1, null, args.uri, args.projection, args.selection,
                            args.selectionArgs, args.orderBy);
                }
            }
        }

        public int getPlayIndexOfAudioId(long playAudioId) {
            int count = getCount();
            for (int i = 0; i < count; i++) {
                Cursor mCursor = (Cursor) getItem(i);
                long id = mCursor.getLong(mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                if (id == playAudioId) {
                    return mCursor.getPosition();
                }
            }
            return -1;
        }


        public MusicApdater(Context context, PlayMusicActivity activity, Cursor cursor, String[] from, int[] to) {
            super(context, null);
            mActivity = activity;
            mQueryHandler = new MusicQueryHandler(context.getContentResolver());
            updateColum(cursor);
        }


        public MusicQueryHandler getQueryHandler() {
            return mQueryHandler;
        }

        private void updateColum(Cursor cursor) {
            if (cursor != null) {
                mTitleIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE);
                mArtistIdx = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST);
                if (indexer != null) {
                    indexer.setCursor(cursor);
                } else {
                    String key = mActivity.getString(R.string.fast_scroll_alphabet);
                    indexer = new ListAlphabetIndexer(cursor, mTitleIndex, key);
                }
            }
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = mActivity.getLayoutInflater().inflate(R.layout.item_list_title_and_author_no_swiper, null);
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.tvMusicTitle = (TextView) view.findViewById(R.id.tvMusicTitle);
            viewHolder.tvMusicAuthor = (TextView) view.findViewById(R.id.tvMusicAuthor);
            viewHolder.imgPlayingIcon = (ImageView) view.findViewById(R.id.imgPlayingIcon);
            view.setTag(viewHolder);
            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ViewHolder viewHolder = (ViewHolder) view.getTag();
            final String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
            viewHolder.tvMusicTitle.setText(title);
            String artist = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
            viewHolder.tvMusicAuthor.setText(artist);
            long playingId = -1;
            if (MusicUtils.sService != null) {
                try {
                    playingId = MusicUtils.sService.getAudioId();
                    if (playingId == cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Media._ID))) {
                        viewHolder.imgPlayingIcon.setVisibility(View.VISIBLE);
                        //直接选中
                        if(null != mActivity.playListView){
                            mActivity.playListView.setSelection(cursor.getPosition());
                        }
                    } else {
                        viewHolder.imgPlayingIcon.setVisibility(View.GONE);
                    }
                } catch (RemoteException e) {

                }
            }

        }

       static class ViewHolder {
            public TextView tvMusicTitle;
            public TextView tvMusicAuthor;
            public ImageView imgPlayingIcon;
        }

    }
}
