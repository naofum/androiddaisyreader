/**
 * 
 */
package org.androiddaisyreader.player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.androiddaisyreader.AudioCallbackListener;
import org.androiddaisyreader.AudioPlayer;
import org.androiddaisyreader.model.Audio;
import org.androiddaisyreader.model.AudioPlayerState;
import org.androiddaisyreader.model.BookContext;

import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.util.Log;

/**
 * @author Julian Harty
 * 
 */
public class AndroidAudioPlayer implements AudioPlayer, OnCompletionListener {
    private static final String TAG = "DAISYAndroidAudioPlayer";
    private MediaPlayer player;
    private Audio audioSegment;
    private BookContext context;
    private TempFileForAudioContentProvider tempFileCreator;
    private List<AudioCallbackListener> listeners = new ArrayList<AudioCallbackListener>();
    private float playbackSpeed = 1.0f;

    public AndroidAudioPlayer(BookContext context) {
        this.context = context;
        tempFileCreator = new TempFileForAudioContentProvider(context);
        player = new MediaPlayer();
        player.setOnCompletionListener(this);
    }

    /**
     * 再生速度を設定する。次回のplay()以降に適用される。
     */
    public void setPlaybackSpeed(float speed) {
        this.playbackSpeed = speed;
        applyPlaybackSpeed();
    }

    private void applyPlaybackSpeed() {
        try {
            android.media.PlaybackParams params = player.getPlaybackParams();
            params.setSpeed(playbackSpeed);
            player.setPlaybackParams(params);
        } catch (Exception e) {
            // 再生中でない場合等は無視
        }
    }

    public void increaseVolume() {
        // TODO Auto-generated method stub

    }

    public void decreaseVolume() {
        // TODO Auto-generated method stub

    }

    public void toggleMute() {
        // TODO Auto-generated method stub

    }

    public void addCallbackListener(AudioCallbackListener listener) {
        listeners.add(listener);
    }

    public Audio getCurrentSegment() {
        return audioSegment;
    }

    public AudioPlayerState getInternalPlayerState() {
        // TODO Auto-generated method stub
        return null;
    }

    public void play() {
        // TODO 20120514 (jharty): Do I want a play() method in addition to
        String requestedFilename = audioSegment.getAudioFilename();
        String filenameToPlay;
        boolean doesContentNeedUnzipping = tempFileCreator.doesContentNeedUnzipping();
        if (doesContentNeedUnzipping) {
            try {
                File f = tempFileCreator.getFileHandleToTempAudioFile(requestedFilename);
                if (f == null) {
                    Log.w(TAG, "Audio file not found in archive: " + requestedFilename);
                    // オーディオが見つからない場合はコールバックを呼んで次に進む
                    for (AudioCallbackListener acl : listeners) {
                        acl.endOfAudio();
                    }
                    return;
                }
                filenameToPlay = f.getAbsolutePath();
                Log.i(TAG, "Created temporary audio file, " + filenameToPlay);

            } catch (IOException ioe) {
                Log.e(TAG, "Problem obtaining a temporary audio file.", ioe);
                return;
            }
        } else {
            filenameToPlay = context.getBaseUri() + File.separator + requestedFilename;
        }
        Log.i(TAG, "play(): requested=" + filenameToPlay);
        Log.i(TAG, "play(): baseUri=" + context.getBaseUri());
        player.reset();
        try {
            player.setDataSource(filenameToPlay);
            player.prepare();
        } catch (Exception e) {
            // TODO 20120514 (jharty): Consider how to report exceptions. For
            // now this'll do.
            Log.e(TAG, "play(): setDataSource/prepare failed: " + e.getMessage(), e);
            return;
        }
        // TODO 20120514 (jharty): This starts from the start of the clip. Add
        // code to start later in the clip e.g. from a bookmark setting.
        player.seekTo(audioSegment.getClipBegin());
        player.start();
        applyPlaybackSpeed();

        // Seems we can delete the temporary file now.
        if (doesContentNeedUnzipping) {
            File deleteMe = new File(filenameToPlay);
            boolean result = deleteMe.delete();
            if (result) {
                Log.i(TAG, "Deleting temporary file, " + filenameToPlay);
            } else {
                Log.i(TAG, "Cannot Delete temporary file, " + filenameToPlay);
            }
        }
    }

    public MediaPlayer getCurrentPlayer() {
        return player;
    }

    public void seekTo(int newTimeInMilliseconds) {
        player.pause();
        player.seekTo(newTimeInMilliseconds);
//        try {
//            player.prepare();
//        } catch (IllegalStateException e) {
//            // TODO Auto-generated catch block
//            Log.e("TAG", e.getMessage(), e);
//        } catch (IOException e) {
//            // TODO Auto-generated catch block
//            Log.e("TAG", e.getMessage(), e);
//        }
    }

    public void setCurrentSegment(Audio audioSegment) {
        Log.i(TAG, "setCurrentSegment");
        this.audioSegment = audioSegment;

    }

    public void setInternalPlayerState(AudioPlayerState audioState) {
        // TODO Auto-generated method stub

    }

    public void onCompletion(MediaPlayer mp) {
        Log.i(TAG, "On Completion called. Resetting the state of the Media Player.");
        player.reset();
        for (AudioCallbackListener acl : listeners) {
            acl.endOfAudio();
        }
    }

    /**
     * MediaPlayer のリソースを解放する。
     * Activity の onDestroy 等で呼ぶこと。
     */
    public void release() {
        if (player != null) {
            try {
                if (player.isPlaying()) {
                    player.stop();
                }
            } catch (IllegalStateException e) {
                // ignore
            }
            player.release();
            player = null;
        }
    }

}
