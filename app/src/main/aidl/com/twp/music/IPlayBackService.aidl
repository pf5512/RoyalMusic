// IPlayBackService.aidl
package com.twp.music;

// Declare any non-default types here with import statements

interface IPlayBackService {
     void openFile(String path);
       void open(in long [] list, int position);
       int getQueuePosition();
       boolean isPlaying();
       void stop();
       void pause();
       void play();
       void prev();
       void next();
       long duration();
       long position();
       long seek(long pos);
       String getTrackName();
       String getAlbumName();
       long getAlbumId();
       String getArtistName();
       long getArtistId();
       void enqueue(in long [] list, int action);
       long [] getQueue();
       void moveQueueItem(int from, int to);
       void setQueuePosition(int index);
       String getPath();
       long getAudioId();
       void setPlayMode(int playModel);
       // shuffle is open ,[ 0=closed,1=nomal,2=auto]
       int getPlayMode();
       int removeTracks(int first, int last);
       int removeTrack(long id);
       int getMediaMountedCount();
       int getAudioSessionId();
}
