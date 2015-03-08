/**
 * douzifly @Aug 10, 2013
 * github.com/douzifly
 * douzifly@gmail.com
 */
package com.twp.music.ui.lrc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.os.Environment;
import android.util.Log;

import com.twp.music.util.Logger;

/** default lrc builder,convert raw lrc string to lrc rows */
public class DefaultLrcBuilder implements  ILrcBuilder{
    static final String TAG = "DefaultLrcBuilder";
    public List<LrcRow> getLrcRows(String rawLrc) {
        Log.d(TAG,"getLrcRows by rawString");
        if(rawLrc == null || rawLrc.length() == 0){
            Log.e(TAG,"getLrcRows rawLrc null or empty");
            return null;
        }
        StringReader reader = new StringReader(rawLrc);
        BufferedReader br = new BufferedReader(reader);
        String line = null;
        List<LrcRow> rows = new ArrayList<LrcRow>();
        try{
            do{
                line = br.readLine();
                Log.d(TAG,"lrc raw line:" + line);
                if(line != null && line.length() > 0){
                    List<LrcRow> lrcRows = LrcRow.createRows(line);
                    if(lrcRows != null && lrcRows.size() > 0){
                        for(LrcRow row : lrcRows){
                            rows.add(row);
                        }
                    }
                }
                
            }while(line != null);
            if( rows.size() > 0 ){
                // sort by time:
                Collections.sort(rows);
            }
            
        }catch(Exception e){
            Log.e(TAG,"parse exceptioned:" + e.getMessage());
            return null;
        }finally{
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            reader.close();
        }
        return rows;
    }

    public String getLrcForSdcard(String lrcName){
        if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            ///mnt/sdcard
            String path = Environment.getExternalStorageDirectory().getAbsolutePath();
            Logger.i("path:"+path);
            File lrcFile = new File(path+File.separator+"Music"+File.separator+"lrc"+ File.separator+lrcName+".lrc");
            Logger.i("lrcFile.path:"+lrcFile.getAbsolutePath());
            if(lrcFile.exists()){
                Logger.i(" lrcFile exit");
                try {
                    InputStreamReader inputReader = new InputStreamReader(new FileInputStream(lrcFile));
                    BufferedReader bufReader = new BufferedReader(inputReader);
                    String line="";
                    StringBuffer buffer=new StringBuffer();
                    while((line = bufReader.readLine()) != null){
                        if(line.trim().equals(""))
                            continue;
                        buffer.append(line + "\r\n");
                    }
                    return buffer.toString();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return "";
        }
        return "";
    }
}
