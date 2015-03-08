package com.twp.music.fragment;


import android.app.Activity;
import android.app.LoaderManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import com.twp.music.LocalMusicActivity;
import com.twp.music.MusicListsActivity;
import com.twp.music.R;
import com.twp.music.util.Logger;
import com.twp.music.util.MusicUtils;

/**
 * A simple {@link android.app.Fragment} subclass.
 * Use the {@link com.twp.music.fragment.LocalFileFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class LocalFileFragment extends BaseFragment implements LocalMusicActivity.PlayServiceListener, LoaderManager.LoaderCallbacks<Cursor> ,AdapterView.OnItemClickListener{
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String TAG = "LocalFileFragment";

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment LocalFileFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static LocalFileFragment newInstance(String... params) {
        LocalFileFragment fragment = new LocalFileFragment();
        return fragment;
    }

    public LocalFileFragment() {
        // Required empty public constructor
    }


    private GridView gridView;
    private AblumAdapter adapter;
    private LocalMusicActivity mRootActivity;

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mRootActivity = (LocalMusicActivity) activity;
        mRootActivity.registeServiceListeners(TAG, this);

    }

    @Override
    public void onDetach() {
        super.onDetach();
        if (null != mRootActivity) {
            mRootActivity.unRegisteServiceListeners(TAG);
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getLoaderManager().initLoader(0, null, this);
        adapter = new AblumAdapter(mRootActivity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_local_file, container, false);
        gridView = (GridView) view.findViewById(R.id.gridView);
        gridView.setAdapter(adapter);
        gridView.setOnItemClickListener(this);
        updateGridView();
        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {

    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    @Override
    public void onStatusMetaChange(Intent intent) {
        updateGridView();
    }

    @Override
    public void onWindowsFocusChanged(boolean hasFocus) {

    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Intent intent = new Intent(mRootActivity, MusicListsActivity.class);
        long ablumId = adapter.getItemId(position);
        //查看cursor ablum 的index
        String title = adapter.getAblumName(position,2);
        Logger.i(TAG,"artistId:"+ablumId+" ,id:"+id);
        intent.putExtra("id",ablumId);
        intent.putExtra("showType",MusicListsActivity.ShowType.ABLUM.intValue());
        intent.putExtra("title",title);
        startActivity(intent);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri uri = MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Audio.Albums._ID, MediaStore.Audio.Albums.ALBUM_ART, MediaStore.Audio.Albums.ALBUM, MediaStore.Audio.Albums.ARTIST};
        CursorLoader cursorLoader = new CursorLoader(mRootActivity, uri, projection, null, null, MediaStore.Audio.Albums.ALBUM_KEY);
        return cursorLoader;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        if (data != null) {
            adapter.swapCursor(data);
            ((View) gridView.getParent()).setVisibility(View.VISIBLE);
            updateGridView();
        } else {
            ((View) gridView.getParent()).setVisibility(View.INVISIBLE);
        }
    }

    //3处地方调用，1.oncreateView ,2. cursorChange 3. statusChange
    private void updateGridView() {
        if (null != gridView) {
            if (gridView.getVisibility() == View.VISIBLE) {
                if (MusicUtils.sService != null) {
                    try {
                        long play = MusicUtils.sService.getAlbumId();
                         if(play == -1){
                             Logger.i(TAG,"play is not start!");
                             return;
                         }
                        final int index = adapter.getPositionWithAblumId(play);
                        if(-1 != index){
                            Logger.i(TAG, "move to index:" + index);


                            gridView.post(new Runnable() {
                                @Override
                                public void run() {
                                    gridView.smoothScrollToPositionFromTop(index, 10);

                                }
                            });
                        }else{
                            Logger.i(TAG,"not has this ablumsId:"+play);
                        }

                    } catch (RemoteException e) {
                        return;
                    }


                }
            }
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> loader) {
        adapter.swapCursor(null);
    }

    class AblumAdapter extends CursorAdapter {

        private AblumAdapter(Context context) {
            super(context, null);
        }

        public String getAblumName(int position,int artistIndex){
            Cursor cursor = (Cursor)getItem(position);
            return cursor.getString(artistIndex);
        }

        public int getPositionWithAblumId(long ablumId) {

            int count = getCount();
            Logger.i(TAG,"count" + count);
            for (int i = 0; i < count; i++) {
                Cursor mCursor = (Cursor) getItem(i);
                long id = mCursor.getLong(mCursor.getColumnIndexOrThrow(MediaStore.Audio.Albums._ID));
                Logger.i(TAG, " id:" + id + " playablumId:" + ablumId + " , position:" + i);
                if (id == ablumId) {
                    return mCursor.getPosition();
                }
            }
            return -1;
        }
        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = mRootActivity.getLayoutInflater().inflate(R.layout.item_ablum_grid, null);
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.imgAblumIcon = (ImageView) view.findViewById(R.id.imgAblumIcon);
            viewHolder.tvAblumName = (TextView) view.findViewById(R.id.tvAblumName);
            viewHolder.tvArtistName = (TextView) view.findViewById(R.id.tvArtist);
            viewHolder.imgPlayorPause = (ImageView) view.findViewById(R.id.imgPlayorPause);
            view.setTag(viewHolder);
            return view;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final ViewHolder viewHolder = (ViewHolder) view.getTag();
            String name = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM));
            viewHolder.tvAblumName.setText(name);
            String artist = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ARTIST));
            viewHolder.tvArtistName.setText(artist);

            boolean unknown = name == null || name.equals(MediaStore.UNKNOWN_STRING);
            Logger.i(TAG, "name:" + name + " , artist:" + artist);
            String art = cursor.getString(cursor.getColumnIndexOrThrow(
                    MediaStore.Audio.Albums.ALBUM_ART));
            final long ids = cursor.getLong(0);

            if (MusicUtils.sService != null) {
                try {
                    long playAlbumId = MusicUtils.sService.getAlbumId();
                    if (playAlbumId == ids) {
                        viewHolder.imgPlayorPause.setImageResource(R.drawable.ic_grid_front_pause);
                        viewHolder.imgPlayorPause.setBackgroundResource(R.drawable.selector_l_grid_pause_or_play_ping);
                    } else {
                        viewHolder.imgPlayorPause.setImageResource(R.drawable.ic_grid_front_play);
                        viewHolder.imgPlayorPause.setBackgroundResource(R.drawable.selector_l_grid_pause_or_play);
                    }
                } catch (RemoteException e) {
                    e.printStackTrace();
                }
            }
            if (unknown || art == null || art.length() == 0) {
                viewHolder.imgAblumIcon.setImageResource(R.drawable.default_music_pre);
            } else {
                viewHolder.imgAblumIcon.post(new Runnable() {
                    @Override
                    public void run() {
                        MusicUtils.getAblumImg(mRootActivity, viewHolder.imgAblumIcon, ids);
                    }
                });

            }

        }

        class ViewHolder {
            public ImageView imgAblumIcon;
            public TextView tvAblumName;
            public TextView tvArtistName;
            public ImageView imgPlayorPause;
        }

    }


}
