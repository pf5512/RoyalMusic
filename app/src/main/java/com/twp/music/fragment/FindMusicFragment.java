package com.twp.music.fragment;

import com.twp.music.R;

import android.content.ComponentName;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


/**
 * @author Royal
 * @Ctime 2014-10-21/����9:03:29
 * @DESC
 */
public class FindMusicFragment extends BaseFragment {


    @Override
    public View onCreateView(LayoutInflater inflater,
                             @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_find_music, null);
        return rootView;
    }

}
