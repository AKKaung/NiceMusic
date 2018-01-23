package com.lzx.musiclibrary.playback;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.net.Uri;
import android.support.v4.media.session.PlaybackStateCompat;
import android.text.TextUtils;

import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.ExoPlayerFactory;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.Timeline;
import com.google.android.exoplayer2.audio.AudioAttributes;
import com.google.android.exoplayer2.extractor.DefaultExtractorsFactory;
import com.google.android.exoplayer2.extractor.ExtractorsFactory;
import com.google.android.exoplayer2.source.ExtractorMediaSource;
import com.google.android.exoplayer2.source.MediaSource;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.TrackSelectionArray;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.util.Util;
import com.lzx.musiclibrary.MusicService;
import com.lzx.musiclibrary.bean.MusicInfo;
import com.lzx.musiclibrary.manager.FocusAndLockManager;
import com.lzx.musiclibrary.utils.LogUtil;

import static com.google.android.exoplayer2.C.CONTENT_TYPE_MUSIC;
import static com.google.android.exoplayer2.C.USAGE_MEDIA;
import static com.lzx.musiclibrary.manager.FocusAndLockManager.AUDIO_NO_FOCUS_CAN_DUCK;
import static com.lzx.musiclibrary.manager.FocusAndLockManager.AUDIO_NO_FOCUS_NO_DUCK;
import static com.lzx.musiclibrary.manager.FocusAndLockManager.VOLUME_DUCK;
import static com.lzx.musiclibrary.manager.FocusAndLockManager.VOLUME_NORMAL;

/**
 * Created by xian on 2018/1/20.
 */

public class ExoPlayback implements Playback, FocusAndLockManager.AudioFocusChangeListener {


    private boolean mPlayOnFocusGain;
    private boolean mAudioNoisyReceiverRegistered;
    private String mCurrentMediaId; //当前播放的媒体id

    private SimpleExoPlayer mExoPlayer;
    private final ExoPlayerEventListener mEventListener = new ExoPlayerEventListener();
    private boolean mExoPlayerNullIsStopped = false;


    private FocusAndLockManager mFocusAndLockManager;

    private Callback mCallback;

    private Context mContext;

    public ExoPlayback(Context context) {
        Context applicationContext = context.getApplicationContext();
        this.mContext = applicationContext;
        mFocusAndLockManager = new FocusAndLockManager(applicationContext, this);
    }

    private final IntentFilter mAudioNoisyIntentFilter =
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    private final BroadcastReceiver mAudioNoisyReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                        if (isPlaying()) {
                            Intent i = new Intent(context, MusicService.class);
                            //   i.setAction(MusicService.ACTION_CMD);
                            //  i.putExtra(MusicService.CMD_NAME, MusicService.CMD_PAUSE);
                            mContext.startService(i);
                        }
                    }
                }
            };

    /**
     * 释放服务使用的资源进行播放，这主要只是WiFi锁本地播放。 如果请求，ExoPlayer实例也被释放。
     *
     * @param releasePlayer 指示播放器是否也应该被释放
     */
    private void releaseResources(boolean releasePlayer) {
        // Stops and releases player (if requested and available).
        if (releasePlayer && mExoPlayer != null) {
            mExoPlayer.release();
            mExoPlayer.removeListener(mEventListener);
            mExoPlayer = null;
            mExoPlayerNullIsStopped = true;
            mPlayOnFocusGain = false;
        }

        mFocusAndLockManager.releaseWifiLock();
    }

    @Override
    public void start() {

    }

    @Override
    public void stop(boolean notifyListeners) {
        mFocusAndLockManager.giveUpAudioFocus();
        unregisterAudioNoisyReceiver();
        releaseResources(true);
    }

    @Override
    public void setState(int state) {

    }

    @Override
    public int getState() {
        //STATE_IDLE      没有任何媒体播放。
        //STATE_BUFFERING 无法立即从当前位置进行播放
        //STATE_READY     可以从当前位置立即进行播放。 如果  {@link #getPlayWhenReady（）}为true，立即播放，否则暂停。
        //STATE_ENDED     已经完成播放媒体。
        if (mExoPlayer == null) {
            return mExoPlayerNullIsStopped ? PlaybackStateCompat.STATE_STOPPED : PlaybackStateCompat.STATE_NONE;
        }
        switch (mExoPlayer.getPlaybackState()) {
            case Player.STATE_IDLE:
                return PlaybackStateCompat.STATE_PAUSED;
            case Player.STATE_BUFFERING:
                return PlaybackStateCompat.STATE_BUFFERING;
            case Player.STATE_READY:
                return mExoPlayer.getPlayWhenReady() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
            case Player.STATE_ENDED:
                return PlaybackStateCompat.STATE_PAUSED;
            default:
                return PlaybackStateCompat.STATE_NONE;
        }
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public boolean isPlaying() {
        return mPlayOnFocusGain || (mExoPlayer != null && mExoPlayer.getPlayWhenReady());
    }

    @Override
    public long getCurrentStreamPosition() {
        return mExoPlayer != null ? mExoPlayer.getCurrentPosition() : 0;
    }

    @Override
    public void updateLastKnownStreamPosition() {

    }

    @Override
    public void play(MusicInfo info) {
        mPlayOnFocusGain = true;
        mFocusAndLockManager.tryToGetAudioFocus();
        registerAudioNoisyReceiver();
        String mediaId = info.musicId;
        boolean mediaHasChanged = !TextUtils.equals(mediaId, mCurrentMediaId);
        if (mediaHasChanged) {
            mCurrentMediaId = mediaId;
        }

        if (mediaHasChanged || mExoPlayer == null) {
            releaseResources(false); // release everything except the player

            String source = info.musicUrl;
            if (source != null) {
                source = source.replaceAll(" ", "%20"); // Escape spaces for URLs
            }
            LogUtil.i("source = " + source);
            if (mExoPlayer == null) {
                mExoPlayer = ExoPlayerFactory.newSimpleInstance(mContext, new DefaultTrackSelector(), new DefaultLoadControl());
                mExoPlayer.addListener(mEventListener);
            }

            final AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(CONTENT_TYPE_MUSIC)
                    .setUsage(USAGE_MEDIA)
                    .build();
            mExoPlayer.setAudioAttributes(audioAttributes);

            // Produces DataSource instances through which media data is loaded.
            DataSource.Factory dataSourceFactory = new DefaultDataSourceFactory(mContext, Util.getUserAgent(mContext, "musiclibrary"), null);
            // Produces Extractor instances for parsing the media data.
            ExtractorsFactory extractorsFactory = new DefaultExtractorsFactory();
            // The MediaSource represents the media to be played.
            MediaSource mediaSource = new ExtractorMediaSource(Uri.parse(source), dataSourceFactory, extractorsFactory, null, null);

            // Prepares media to play (happens on background thread) and triggers
            // {@code onPlayerStateChanged} callback when the stream is ready to play.
            mExoPlayer.prepare(mediaSource);

            // If we are streaming from the internet, we want to hold a
            // Wifi lock, which prevents the Wifi radio from going to
            // sleep while the song is playing.
            mFocusAndLockManager.acquireWifiLock();
        }

        configurePlayerState();
    }

    @Override
    public void pause() {
        // Pause player and cancel the 'foreground service' state.
        if (mExoPlayer != null) {
            mExoPlayer.setPlayWhenReady(false);
        }
        // While paused, retain the player instance, but give up audio focus.
        releaseResources(false);
        unregisterAudioNoisyReceiver();
    }

    @Override
    public void seekTo(long position) {
        if (mExoPlayer != null) {
            registerAudioNoisyReceiver();
            mExoPlayer.seekTo(position);
        }
    }

    @Override
    public void setCurrentMediaId(String mediaId) {
        this.mCurrentMediaId = mediaId;
    }

    @Override
    public String getCurrentMediaId() {
        return mCurrentMediaId;
    }

    @Override
    public void setCallback(Callback callback) {
        this.mCallback = callback;
    }

    /**
     *       *根据音频焦点设置重新配置播放器并启动/重新启动。 这种方法
     *       *启动/重新启动ExoPlayer的实例尊重当前的音频焦点状态。 所以，如果我们
     *       *有焦点，会正常播放; 如果我们没有重点，它会离开玩家
     *       *暂停或将其设置为低音量，具体取决于当前焦点允许的内容
     *       *设置。
     *      
     */
    private void configurePlayerState() {
        if (mFocusAndLockManager.getCurrentAudioFocusState() == AUDIO_NO_FOCUS_NO_DUCK) {
            // We don't have audio focus and can't duck, so we have to pause
            pause();
        } else {
            registerAudioNoisyReceiver();

            if (mFocusAndLockManager.getCurrentAudioFocusState() == AUDIO_NO_FOCUS_CAN_DUCK) {
                // We're permitted to play, but only if we 'duck', ie: play softly
                mExoPlayer.setVolume(VOLUME_DUCK);
            } else {
                mExoPlayer.setVolume(VOLUME_NORMAL);
            }

            // If we were playing when we lost focus, we need to resume playing.
            if (mPlayOnFocusGain) {
                mExoPlayer.setPlayWhenReady(true);
                mPlayOnFocusGain = false;
            }
        }
    }

    private void registerAudioNoisyReceiver() {
        if (!mAudioNoisyReceiverRegistered) {
            mContext.registerReceiver(mAudioNoisyReceiver, mAudioNoisyIntentFilter);
            mAudioNoisyReceiverRegistered = true;
        }
    }

    private void unregisterAudioNoisyReceiver() {
        if (mAudioNoisyReceiverRegistered) {
            mContext.unregisterReceiver(mAudioNoisyReceiver);
            mAudioNoisyReceiverRegistered = false;
        }
    }

    @Override
    public void onAudioFocusLossTransient() {
        mPlayOnFocusGain = mExoPlayer != null && mExoPlayer.getPlayWhenReady();
    }

    @Override
    public void onAudioFocusChange() {
        if (mExoPlayer != null) {
            // Update the player state based on the change
            configurePlayerState();
        }
    }

    /**
     * ExoPlayer事件监听器
     */
    private final class ExoPlayerEventListener implements Player.EventListener {

        @Override
        public void onTimelineChanged(Timeline timeline, Object manifest) {

        }

        @Override
        public void onTracksChanged(TrackGroupArray trackGroups, TrackSelectionArray trackSelections) {

        }

        @Override
        public void onLoadingChanged(boolean isLoading) {

        }

        @Override
        public void onPlayerStateChanged(boolean playWhenReady, int playbackState) {
            switch (playbackState) {
                case Player.STATE_IDLE:
                case Player.STATE_BUFFERING:
                case Player.STATE_READY:
                    if (mCallback != null) {
                        mCallback.onPlaybackStatusChanged(getState());
                    }
                    break;
                case Player.STATE_ENDED:
                    if (mCallback != null) {
                        mCallback.onPlayCompletion();
                    }
                    break;
            }
        }

        @Override
        public void onRepeatModeChanged(int repeatMode) {

        }

        @Override
        public void onPlayerError(ExoPlaybackException error) {
            final String what;
            switch (error.type) {
                case ExoPlaybackException.TYPE_SOURCE:
                    what = error.getSourceException().getMessage();
                    break;
                case ExoPlaybackException.TYPE_RENDERER:
                    what = error.getRendererException().getMessage();
                    break;
                case ExoPlaybackException.TYPE_UNEXPECTED:
                    what = error.getUnexpectedException().getMessage();
                    break;
                default:
                    what = "Unknown: " + error;
            }
            if (mCallback != null) {
                mCallback.onError("ExoPlayer error " + what);
            }
        }

        @Override
        public void onPositionDiscontinuity() {

        }

        @Override
        public void onPlaybackParametersChanged(PlaybackParameters playbackParameters) {

        }
    }


}
