
package com.twp.music.fragment;

import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.twp.music.LocalMusicActivity;
import com.twp.music.R;
import com.twp.music.util.Logger;

/**
 * @author Royal
 * @Ctime 2014-10-21/����9:04:02
 * @DESC
 */
public class MyMusicFragment extends BaseFragment implements View.OnClickListener {

    private ItemInfo localMusic;
    private ItemInfo downLoadedMusic;
    private ItemInfo cloudLikedMusic;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        localMusic = new ItemInfo(R.drawable.ic_m_local_music_icon, getResources().getString(R.string.localMusic), 0L);
        downLoadedMusic = new ItemInfo(R.drawable.ic_m_local_download_icon, getResources().getString(R.string.downLoadedMusic), 0L);
        cloudLikedMusic = new ItemInfo(R.drawable.ic_m_local_hearts_icon, getResources().getString(R.string.cloudLikeMusic), 0L);

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String selections = MediaStore.Audio.Media.SIZE + " > ? and " + MediaStore.Audio.Media.DURATION + " > ? ";
                String[] selectionArg = {"1048576", "200000"};
                Cursor query = getActivity().getContentResolver().query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[]{MediaStore.Audio.AudioColumns._ID}, selections, selectionArg, null);
                if (null != query) {
                    query.moveToFirst();
                    while (query.moveToNext()) {
                        int id = query.getInt(query.getColumnIndex(MediaStore.Audio.AudioColumns._ID));
                    }
                    Logger.i("Audio count = " + query.getCount());
                    localMusic.setMusicNum(query.getCount());
                }
            }
        });


    }

    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_my_music, null);
        initView(rootView);
        return rootView;
    }

    public void initView(View rootView) {
        View containerLocalMusic = rootView.findViewById(R.id.contentLocalMusic);
        containerLocalMusic.setOnClickListener(this);
        updateInfo(containerLocalMusic, localMusic);
        View containerDownMusic = rootView.findViewById(R.id.contentDownMusic);
        updateInfo(containerDownMusic, downLoadedMusic);
        View containerCloudMusic = rootView.findViewById(R.id.contentCloudMusic);
        updateInfo(containerCloudMusic, cloudLikedMusic);
    }

    public void updateInfo(View view, ItemInfo itemInfo) {
        ((ImageView) view.findViewById(R.id.imgPlayListIcon)).setImageResource(itemInfo.getIconResId());
        ((TextView) view.findViewById(R.id.tvPlayListTitle)).setText(itemInfo.getTitle());
        ((TextView) view.findViewById(R.id.tvPlayListMusicNum)).setText(getResources().getString(R.string.playListNum, itemInfo.getMusicNum()));
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.contentLocalMusic:
                jumpLocalMusic();
                break;
            default:
                break;
        }
    }

    private void jumpLocalMusic() {
        Intent intent = new Intent(getActivity(), LocalMusicActivity.class);
        intent.putExtra("MusicCount",(int)localMusic.getMusicNum());
        startActivity(intent);
    }


    private class ItemInfo {
        private int iconResId;
        private String title;
        private long musicNum;

        public ItemInfo(int iconResId, String title, long musicNum) {
            setIconResId(iconResId);
            setTitle(title);
            setMusicNum(musicNum);
        }

        public int getIconResId() {
            return iconResId;
        }

        public void setIconResId(int iconResId) {
            this.iconResId = iconResId;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public long getMusicNum() {
            return musicNum;
        }

        public void setMusicNum(long musicNum) {
            this.musicNum = musicNum;
        }
    }
}
