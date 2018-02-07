// IPlayControl.aidl
package com.lzx.musiclibrary.aidl.listener;

import com.lzx.musiclibrary.aidl.model.MusicInfo;
import com.lzx.musiclibrary.aidl.listener.IOnPlayerEventListener;

interface IPlayControl {


    //播放，并设置播放列表
     void playMusic(in List<MusicInfo> list, int index ,boolean isJustPlay);

    //根据音乐信息播放
     void playMusicByInfo(in MusicInfo info,boolean isJustPlay);

    //根据索引播放
    void playMusicByIndex(int index,boolean isJustPlay);

    //定时播放
    void playMusicAutoStopWhen(in List<MusicInfo> list, int index, int time);

    //定时播放
    void playMusicByInfoAutoStopWhen(in MusicInfo info, int time);

    //定时播放
    void playMusicByIndexAutoStopWhen(int index, int time);

    //设置定时时间
    void setAutoStopTime(int time);

    //得到当前播放索引
    int getCurrPlayingIndex();

    //暂停
    void pauseMusic();

    //继续
    void resumeMusic();

    //停止音乐
    void stopMusic();

    //设置播放列表
    void setPlayList(in List<MusicInfo> list);

    //设置播放列表
    void setPlayListWithIndex(in List<MusicInfo> list,int index);

    //得到播放列表
    List<MusicInfo> getPlayList();

    //获取播放状态
    int getStatus();

    //播放下一首
    void playNext();

    //播放上一首
    void playPre();

    //是否有上一首
    boolean hasPre();

    //是否有下一首
    boolean hasNext();

    //得到上一首信息
    MusicInfo getPreMusic();

    //得到下一首信息
    MusicInfo getNextMusic();

    //得到当前播放信息
    MusicInfo getCurrPlayingMusic();

    //设置当前音乐信息
    void setCurrMusic(int index);

    //设置播放模式
    void setPlayMode(int mode);

    //得到播放模式
    int getPlayMode();

    //获取当前进度
    long getProgress();

    //定位到指定位置
    void seekTo(int position);

    //初始化
    void reset();

    void registerPlayerEventListener(IOnPlayerEventListener listener);

    void unregisterPlayerEventListener(IOnPlayerEventListener listener);
}
