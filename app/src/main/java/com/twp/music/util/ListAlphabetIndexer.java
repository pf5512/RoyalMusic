package com.twp.music.util;

import android.database.Cursor;
import android.provider.MediaStore;
import android.widget.AlphabetIndexer;

/**
 * Created by Administrator on 10/28 0028.
 */
public class ListAlphabetIndexer extends AlphabetIndexer {

    public ListAlphabetIndexer(Cursor cursor, int sortedColumnIndex, CharSequence alphabet) {
        super(cursor, sortedColumnIndex, alphabet);
    }

    @Override
    protected int compare(String word, String letter) {
        String wordKey = MediaStore.Audio.keyFor(word);
        String letterKey = MediaStore.Audio.keyFor(letter);
        if (wordKey.startsWith(letter)) {
            return 0;
        } else {
            return wordKey.compareTo(letterKey);
        }
    }
}
