package com.twp.music.fragment;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.TextView;

import com.twp.music.LocalMusicActivity;
import com.twp.music.R;
import com.twp.music.ui.layoutswipe.CursorSwipeAdapter;
import com.twp.music.util.Logger;
import com.twp.music.util.MusicUtils;

/**
 * A simple {@link android.app.Fragment} subclass.
 * Activities that contain this fragment must implement the
 * to handle interaction events.
 * Use the {@link com.twp.music.fragment.LocalAllMusicFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class LocalAllMusicFragment extends BaseFragment implements AdapterView.OnItemClickListener, LocalMusicActivity.PlayServiceListener {
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String TAG = "LocalAllMusicFragment";
    private static final String MUSIC_NUM = "MusicNum";
    private static final String ARG_PARAM2 = "param2";
    private static final int TOKEN_QUERY = 1;
    private static final int TOKEN_DELETE = 2;
    // TODO: Rename and change types of parameters
    private int musicNum;
    private String mParam2;
    private ListView listViewLocalMusic;
    private MusicApdater adapter;
    private BackgroundQueryHandler queryHandler;
    private TextView tvTotalMusicNum;
    private ImageButton btnPlayAll;
    private LocalMusicActivity mRootActivity;

    //作为类变量，在程序运行时可用

    public LocalAllMusicFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment LocalAllMusicFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static LocalAllMusicFragment newInstance(int count) {
        LocalAllMusicFragment fragment = new LocalAllMusicFragment();
        Bundle args = new Bundle();
        args.putInt(MUSIC_NUM, count);
        fragment.setArguments(args);
        return fragment;
    }

    /**
     * 最先被调用，在 onCreate()之前
     *
     * @param activity
     */
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            Logger.i("LocalAllMusicFragment Attach to LocalMusicActivity");
            mRootActivity = (LocalMusicActivity) activity;
            mRootActivity.registeServiceListeners(TAG, this);
        } catch (ClassCastException e) {
            Logger.i("LocalAllMusicFragment Attach to LocalMusicActivity has exception!");
        }
    }

    @Override
    public void onDetach() {
        if (mRootActivity != null) {
            mRootActivity.unRegisteServiceListeners(TAG);
        }
        Logger.i("LocalAllMusicFragment onDetach to LocalMusicActivity");
        //if has play music record this position
        super.onDetach();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Logger.i(TAG, "onCreate");
        if (getArguments() != null) {
            musicNum = getArguments().getInt(MUSIC_NUM);
        }
        //init
        adapter = new MusicApdater(mRootActivity);
        queryHandler = new BackgroundQueryHandler(mRootActivity.getContentResolver());
        //音乐查询条件， size >1M ,时长>100s,
        String selections = MediaStore.Audio.Media.SIZE + " > ? and " + MediaStore.Audio.Media.DURATION + " > ? ";
        String[] selectionArg = {"1048576", "200000"};
        queryHandler.startQuery(TOKEN_QUERY, null, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, new String[]{MediaStore.Audio.AudioColumns._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DURATION}, selections, selectionArg, null);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Logger.i(TAG, "onCreateView");
        View view = inflater.inflate(R.layout.fragment_local_all, container, false);
        listViewLocalMusic = (ListView) view.findViewById(R.id.listViewLocalMusic);
        tvTotalMusicNum = (TextView) view.findViewById(R.id.tvTotalMusicNum);
        btnPlayAll = (ImageButton) view.findViewById(R.id.btnPlayAll);
        btnPlayAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (null != adapter && MusicUtils.sService != null) {

                    try {
                        if (MusicUtils.sService.isPlaying() || MusicUtils.sService.getAudioId() != -1) {
                            int position = MusicUtils.sService.getQueuePosition();
                            MusicUtils.playAll(mRootActivity, adapter.getCursor(), position);
                        } else {
                            Logger.i(TAG, "goto play all");
                            MusicUtils.playAll(mRootActivity, adapter.getCursor(), 0);
                        }
                    } catch (RemoteException e) {
                        Logger.i(TAG, "fial to connect service");
                    }

                }
            }
        });
        listViewLocalMusic.setAdapter(adapter);
        listViewLocalMusic.setOnItemClickListener(this);
        updateTotalNum(musicNum);
        updatePlayAll();
        updateListView();
        return view;
    }

    private AlertDialog.Builder builderDeleteDailog(String musicName, final int id) {
        AlertDialog.Builder builder = new AlertDialog.Builder(mRootActivity);
        Resources rs = mRootActivity.getResources();
        builder.setTitle(rs.getString(R.string.deleteTip));
        builder.setMessage(rs.getString(R.string.deleteConfirm, musicName));
        builder.setPositiveButton(rs.getString(R.string.yesDelete), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deleteMusic(id);
            }
        });
        builder.setNegativeButton(rs.getString(R.string.noBack), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                dialog.cancel();
            }
        });
        return builder;
    }

    private void deleteMusic(int id) {
        String selection = MediaStore.Audio.Media._ID + " = ?";
        String[] selectionArgs = {String.valueOf(id)};
        queryHandler.startDelete(TOKEN_DELETE, null, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, selection, selectionArgs);
    }

    private void updateTotalNum(int sum) {
        if (null != tvTotalMusicNum) {
            String strCount = mRootActivity.getResources().getString(R.string.allMusicStr, sum);
            tvTotalMusicNum.setText(strCount);
        } else {
            Logger.w("MusicNum TextView not init");
        }
    }


    //flag = true 表示列表中有歌曲在播放
    private void updatePlayAll() {
        if (null != btnPlayAll && MusicUtils.sService!=null) {
            try {
                boolean flag = MusicUtils.sService.isPlaying();
                Logger.i(TAG, "MusicUtils.sService is playing " + " flag:" + flag);
                if (flag) {
                    btnPlayAll.setImageResource(R.drawable.ic_h_list_pause);
                } else {
                    btnPlayAll.setImageResource(R.drawable.ic_h_list_play);
                }
            } catch (RemoteException e) {
                btnPlayAll.setImageResource(R.drawable.ic_h_list_play);
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Logger.i("onItemClick position:" + position);
        if (adapter.getCursor() != null) {
            MusicUtils.playAll(mRootActivity, adapter.getCursor(), position);
        } else {
            Logger.i("CursorApdater is null .");
        }
    }

    class MusicApdater extends CursorSwipeAdapter {

        public int getPlayIndexOfAudioId(long playAudioId) {
            int count = getCount();
            Logger.i(TAG, "count" + count);
            for (int i = 0; i < count; i++) {
                Cursor mCursor = (Cursor) getItem(i);
                long id = mCursor.getLong(mCursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID));
                //Logger.i(TAG, " id:" + id + " playAudioId:" + playAudioId + " , position:" + i);
                if (id == playAudioId) {
                    return mCursor.getPosition();
                }
            }
            return -1;
        }

        MusicApdater(Context context) {
            super(context, null, true);

        }

        @Override
        public void onTrashOnclick(String name, int id) {
            builderDeleteDailog(name, id).create().show();
        }

        @Override
        public void onPlayingPosition(int position, long audioId) {
            //Logger.i(TAG, "onPlaying position:" + position + " , audioId:" + audioId);
            updatePlayAll();
        }

        @Override
        public int getSwipeLayoutResourceId(int position) {
            return R.id.swipe;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = mRootActivity.getLayoutInflater().inflate(R.layout.item_list_title_and_author, null);
            return view;
        }

    }


    class BackgroundQueryHandler extends AsyncQueryHandler {
        BackgroundQueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            super.onQueryComplete(token, cookie, cursor);
            if (null != cursor) {
                cursor.moveToFirst();
               /* while (cursor.moveToNext()) {
                    String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
                    //bytes
                    long size = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.SIZE));
                    //ms
                    long duration = cursor.getLong(cursor.getColumnIndex(MediaStore.Audio.Media.DURATION));
                    Logger.i(TAG,"Title:" + title + " , size:" + size + " , duration:" + duration);
                }*/

                //过滤文件，直接使用sql语句
                adapter.changeCursor(cursor);
                updateTotalNum(cursor.getCount());
                if (null != listViewLocalMusic) {
                    ((View) listViewLocalMusic.getParent()).setVisibility(View.VISIBLE);
                }
                updateListView();
            } else {
                if (null != listViewLocalMusic) {
                    ((View) listViewLocalMusic.getParent()).setVisibility(View.INVISIBLE);
                }
            }
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            super.onDeleteComplete(token, cookie, result);
            adapter.notifyDataSetChanged();
            updateTotalNum(adapter.getCount());
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {

        Logger.i(TAG, "AllMusic serviceConnected");
        // updatePlayAll();
        //updateListView();

    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    @Override
    public void onStatusMetaChange(Intent intent) {
        updatePlayAll();
        updateListView();
    }

    @Override
    public void onWindowsFocusChanged(boolean hasFocus) {
        if (hasFocus) {
            Logger.i(TAG, "rootActivity hasFocus ");
        }
    }

    private void updateListView() {
        if (listViewLocalMusic != null ||  MusicUtils.sService!=null) {
            listViewLocalMusic.invalidateViews();
            try {
                final long playAudioId = MusicUtils.sService.getAudioId();
                Logger.i(TAG, "current Queue Position index:" + playAudioId + " adapter.getCount():" + adapter.getCount());

                if (playAudioId != -1) {
                    final int index = adapter.getPlayIndexOfAudioId(playAudioId);
                    if (-1 != index) {
                        Logger.i(TAG, "current playList move to index:" + index);
                        listViewLocalMusic.post(new Runnable() {
                            @Override
                            public void run() {
                                listViewLocalMusic.smoothScrollToPositionFromTop(index,0,index*20);
                            }
                        });

                    }
                }


            } catch (RemoteException e) {

            }
        }

    }
}
