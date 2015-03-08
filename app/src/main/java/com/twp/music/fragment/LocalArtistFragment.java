package com.twp.music.fragment;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CursorAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.twp.music.LocalMusicActivity;
import com.twp.music.MusicListsActivity;
import com.twp.music.R;
import com.twp.music.util.Logger;
import com.twp.music.util.MusicUtils;

/**
 * A simple {@link android.app.Fragment} subclass.
 * Use the {@link com.twp.music.fragment.LocalArtistFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class LocalArtistFragment extends BaseFragment implements LoaderManager.LoaderCallbacks<Cursor>, LocalMusicActivity.PlayServiceListener ,AdapterView.OnItemClickListener{
    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String TAG = "LocalArtistFragment";


    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment LocalArtistFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static LocalArtistFragment newInstance(String... params) {
        LocalArtistFragment fragment = new LocalArtistFragment();
        return fragment;
    }

    public LocalArtistFragment() {
    }

    private String[] cols = new String[]{
            MediaStore.Audio.Artists._ID,
            MediaStore.Audio.Artists.ARTIST,
            MediaStore.Audio.Artists.NUMBER_OF_TRACKS
    };

    private LocalMusicActivity mRootActivity;
    private ListView mListView;
    private ArtisAdapter adapter;

    // BackgroundQueryHandler queryHandler;
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mRootActivity = (LocalMusicActivity) activity;
        mRootActivity.registeServiceListeners(TAG, this);
        getLoaderManager().initLoader(0, null, this);
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
        Logger.i(TAG, "onCreate");
        adapter = new ArtisAdapter(getActivity());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        Logger.i(TAG, "onCreateView");
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_local_artist, container, false);
        mListView = (ListView) view.findViewById(R.id.listViewLocalMusic);
        mListView.setAdapter(adapter);
        mListView.setOnItemClickListener(this);
        updateListView();
        return view;
    }

    //getLoaderManager().initLoader() 调用之后系统回调过来，我们需要在这里返回一个 AsyncTaskLoader实现类。
    //有数据库的话直接使用 CursorLoader. 组合查询条件
    @Override
    public Loader<Cursor> onCreateLoader(int i, Bundle bundle) {
        Uri uri = MediaStore.Audio.Artists.EXTERNAL_CONTENT_URI;
        CursorLoader cursorLoader = new CursorLoader(getActivity(), uri,
                cols, null, null, MediaStore.Audio.Artists.ARTIST_KEY);
        return cursorLoader;
    }


    //异步查询完成后，更新Cursor
    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, Cursor cursor) {
        if (null == cursor) {

            if (null != mListView) {
                ((View) mListView.getParent()).setVisibility(View.INVISIBLE);
            }
            return;
        }
        if (null != mListView) {
            ((View) mListView.getParent()).setVisibility(View.VISIBLE);
        }
        adapter.swapCursor(cursor);
        updateListView();
    }

    //getLoaderManager().restartLoader(0, null, this) 调用后回调这里
    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        adapter.swapCursor(null);
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        Intent intent = new Intent(mRootActivity, MusicListsActivity.class);
        long artistId = adapter.getItemId(position);
        //查看cursor artist 的index
        String title = adapter.getArtistName(position,1);
        Logger.i(TAG,"artistId:"+artistId+" ,id:"+id);
        intent.putExtra("id",artistId);
        intent.putExtra("showType",MusicListsActivity.ShowType.ARTIST.intValue());
        intent.putExtra("title",title);
        startActivity(intent);
    }

    private void updateListView() {
        if (null != mListView) {
            if (mListView.getVisibility() == View.VISIBLE) {
                if (MusicUtils.sService != null) {
                    try {
                        String artist = MusicUtils.sService.getArtistName();

                        if (TextUtils.isEmpty(artist)) {
                            return;
                        }
                        final int index = adapter.getPositionwithAtristName(artist);
                        Logger.i(TAG, "move to index:" + index);

                        if (index != -1) {

                            mListView.post(new Runnable() {
                                @Override
                                public void run() {
                                    mListView.smoothScrollToPositionFromTop(index, 0);

                                }
                            });

                        }

                    } catch (RemoteException e) {
                        return;
                    }


                }
            }
        }
    }

    class ArtisAdapter extends CursorAdapter {
        ArtisAdapter(Context context) {
            super(context, null);
        }

        public String getArtistName(int position,int artsIndex){
            Cursor cursor = (Cursor)getItem(position);
            return cursor.getString(artsIndex);
        }

        public int getPositionwithAtristName(String atristName) {

            int count = getCount();
            Logger.i("count" + count);
            for (int i = 0; i < count; i++) {
                Cursor mCursor = (Cursor) getItem(i);
                String name = mCursor.getString(mCursor.getColumnIndexOrThrow(MediaStore.Audio.Artists.ARTIST));
                Logger.i(TAG, " atristName:" + atristName + " name:" + name + " , position:" + i);
                if (atristName.equals(name)) {
                    return mCursor.getPosition();
                }
            }
            return -1;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View v = mRootActivity.getLayoutInflater().inflate(R.layout.item_list_title_and_author_no_swiper, null);
            ViewHolder viewHolder = new ViewHolder();
            viewHolder.tvMusicTitle = (TextView) v.findViewById(R.id.tvMusicTitle);
            viewHolder.tvMusicAuthor = (TextView) v.findViewById(R.id.tvMusicAuthor);
            viewHolder.imgPlayingIcon = (ImageView) v.findViewById(R.id.imgPlayingIcon);
            v.setTag(viewHolder);
            return v;
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            //super.bindView(view, context, cursor);
            ViewHolder viewHolder = (ViewHolder) view.getTag();
            final String title = cursor.getString(cursor.getColumnIndex(MediaStore.Audio.Artists.ARTIST));
            viewHolder.tvMusicTitle.setText(title);
            int num = cursor.getInt(cursor.getColumnIndex(MediaStore.Audio.Artists.NUMBER_OF_TRACKS));
            String strCount = mRootActivity.getResources().getString(R.string.allMusicStr, num);
            viewHolder.tvMusicAuthor.setText(strCount);
            if (MusicUtils.sService != null) {
                try {
                    String plauArtist = MusicUtils.sService.getArtistName();
                    if (!TextUtils.isEmpty(plauArtist) && plauArtist.equals(title)) {
                        viewHolder.imgPlayingIcon.setVisibility(View.VISIBLE);
                    } else {
                        viewHolder.imgPlayingIcon.setVisibility(View.GONE);
                    }
                } catch (RemoteException e) {
                    viewHolder.imgPlayingIcon.setVisibility(View.GONE);
                }
            }
        }

        class ViewHolder {
            public TextView tvMusicTitle;
            public TextView tvMusicAuthor;
            public ImageView imgPlayingIcon;
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {

    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    @Override
    public void onStatusMetaChange(Intent intent) {
        //媒体文件变更，需要重新更新list
        updateListView();
    }

    @Override
    public void onWindowsFocusChanged(boolean hasFocus) {

    }
}
