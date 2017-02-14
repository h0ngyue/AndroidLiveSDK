package com.yolo.livesdk.widget.watch;

/**
 * Created by shuailongcheng on 30/12/2016.
 */

public interface FastWatchStatusListener {
    void onStartWatchFail();

    /**
     * 这个回调表示 收到第一个数据包了（不仅仅是连接，而且表示有流），收流正式成功，上层可以进行一些ui处理了（比如隐藏"正在连接"的ui hint）
     */
    void onStartWatchSuccess();

    void onBufferingStart();

    void onBufferingComplete();

    void onReconnecting();

    void onReconnectFail();
}
