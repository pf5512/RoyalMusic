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
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.FragmentActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.RadioButton;

import com.twp.music.fragment.LocalAllMusicFragment;
import com.twp.music.fragment.LocalArtistFragment;
import com.twp.music.fragment.LocalFileFragment;
import com.twp.music.service.PlayBackService;
import com.twp.music.util.Logger;
import com.twp.music.util.MusicUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by pengqinping on 15/3/8.
 *
 * @email Royal.k.peng@gmail.com
 * @description
 */
public class LocalMusicActivity extends FragmentActivity implements CompoundButton.OnCheckedChangeListener ,ServiceConnection{


    private static LocalType showType;
    private RadioButton allMusic, artistBrowse, dirBrowse;

    private enum LocalType {
        ALLMUSIC(R.id.radioAllMusic),
        ARTISTBROWSE(R.id.radioArtistBrowse),
        DIRBROWSE(R.id.radioDirBrowse);

        private LocalType(int id) {
            this.id = id;
        }

        public int id;

        public static LocalType getTypeById(int resId){
            if(resId == ALLMUSIC.id) {
                return ALLMUSIC;
            }else if(resId == ARTISTBROWSE.id){
                return ARTISTBROWSE;
            }else if(resId == DIRBROWSE.id){
                return DIRBROWSE;
            }else{
                throw new IllegalArgumentException("no this id "+resId +" in LocalType!");
            }
        }
    };

    private MusicUtils.ServiceToken mToken;
    private BroadcastReceiver mStatusListener = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(PlayBackService.META_CHANGED)) {
                //通知注册的fragment
                Iterator<String> itKey = serviceListeners.keySet().iterator();
                while(itKey.hasNext()){
                    serviceListeners.get(itKey.next()).onStatusMetaChange(intent);
                }

            }
        }
    };
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //设置在这个Activity的时候，音量键控制的是媒体的音量
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
        setContentView(R.layout.activity_local_music);
        allMusic = (RadioButton) findViewById(R.id.radioAllMusic);
        artistBrowse = (RadioButton) findViewById(R.id.radioArtistBrowse);
        dirBrowse = (RadioButton) findViewById(R.id.radioDirBrowse);
        allMusic.setOnCheckedChangeListener(this);
        artistBrowse.setOnCheckedChangeListener(this);
        dirBrowse.setOnCheckedChangeListener(this);
        findViewById(R.id.imgBack).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        checkedPage(LocalType.ALLMUSIC);
        mToken = MusicUtils.bindToService(this, this);
        registerStatusChange();
    }
    @Override
    protected void onDestroy() {
        MusicUtils.unbindFromService(mToken);
        unRegisterStatusChange();;
        super.onDestroy();
    }


    private void checkedPage(LocalType type) {
        Fragment f = null;
        showType = type;
        switch (type) {
            case ALLMUSIC:
                int count = getIntent().getIntExtra("MusicCount", 0);
                f = LocalAllMusicFragment.newInstance(count);
                break;
            case ARTISTBROWSE:
                f = LocalArtistFragment.newInstance();
                break;
            case DIRBROWSE:
                f = LocalFileFragment.newInstance();
                break;
        }
        if (null != f) {
            FragmentTransaction ft =getFragmentManager().beginTransaction();
            ft.replace(R.id.content, f);
            ft.commit();
        }else{
            Logger.w("not find LocalType ,");
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(showType != null){
            checkedPage(showType);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.local_music, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            checkedPage(LocalType.getTypeById(buttonView.getId()));
        }
    }


    public void registerStatusChange(){
        IntentFilter f = new IntentFilter();
        f.addAction(PlayBackService.META_CHANGED);
        f.addAction(PlayBackService.QUEUE_CHANGED);
        registerReceiver(mStatusListener,f);
    }

    public void unRegisterStatusChange(){
        unregisterReceiver(mStatusListener);
    }

    //绑定播放服务成功后，
    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        //通知注册的fragment
        Iterator<String> itKey = serviceListeners.keySet().iterator();
        while(itKey.hasNext()){
            serviceListeners.get(itKey.next()).onServiceConnected(name,service);
        }

    }


    //与播放服务之间连接断开
    @Override
    public void onServiceDisconnected(ComponentName name) {
        //通知注册的fragment
        Iterator<String> itKey = serviceListeners.keySet().iterator();
        while(itKey.hasNext()){
            serviceListeners.get(itKey.next()).onServiceDisconnected(name);
        }
    }


    public Map<String,PlayServiceListener> serviceListeners = new HashMap<String,PlayServiceListener>();
    private static final byte[] lock = new byte[0];
    public void registeServiceListeners(String token,PlayServiceListener listener){
        synchronized (lock){
             serviceListeners.put(token,listener);
        }
    }

    public void unRegisteServiceListeners(String token){
        synchronized (lock){
            serviceListeners.remove(token);
        }
    }


    public interface PlayServiceListener{
        public void onServiceConnected(ComponentName name, IBinder service);
        public void onServiceDisconnected(ComponentName name);
        public void onStatusMetaChange(Intent intent);
        public void onWindowsFocusChanged(boolean hasFocus);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Logger.i("LocalMusicActivity"," hasFocus:"+hasFocus);
        Iterator<String> itKey = serviceListeners.keySet().iterator();
        while(itKey.hasNext()){
            serviceListeners.get(itKey.next()).onWindowsFocusChanged(hasFocus);
        }
    }
}
