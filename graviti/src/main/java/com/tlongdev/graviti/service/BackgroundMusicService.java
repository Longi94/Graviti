package com.tlongdev.graviti.service;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.IBinder;

import com.tlongdev.graviti.R;

import java.io.IOException;

public class BackgroundMusicService extends Service{

    private final IBinder mBinder = new LocalBinder();

    private boolean mPepared;

    private MediaPlayer mediaPlayer;

    @Override
    public void onCreate() {
        super.onCreate();
        mediaPlayer = MediaPlayer.create(this, R.raw.rekslamation);
        mediaPlayer.setLooping(true);
        mPepared = true;
    }

    @Override
    public void onDestroy() {
        mPepared = false;
        mediaPlayer.stop();
        mediaPlayer.release();
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (!mediaPlayer.isPlaying() && getSharedPreferences(getString(R.string.preference_name), MODE_PRIVATE).
                getBoolean(getString(R.string.pref_music_key), true)){
            mediaPlayer.start();
        }
        return mBinder;
    }

    public void startMusic() {
        if (!mediaPlayer.isPlaying()){
            try {
                if (!mPepared)
                    mediaPlayer.prepare();
                mediaPlayer.start();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void stopMusic() {
        if (mediaPlayer.isPlaying()){
            mediaPlayer.stop();
            mPepared = false;
        }
    }

    public class LocalBinder extends Binder {
        public BackgroundMusicService getService() {
            return BackgroundMusicService.this;
        }
    }

    public void setVolume(float volume) {
        mediaPlayer.setVolume(volume, volume);
    }

}
