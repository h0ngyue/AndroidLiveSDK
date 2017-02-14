package com.yolo.livesdk;

import android.content.Context;

import com.yolo.livesdk.rx.YoloLivePublishParam;

/**
 * Created by shuailongcheng on 7/27/16.
 */
public class YoloLiveNative {
    public static void init(Context context, boolean isDebug) {
        System.loadLibrary("gnustl_shared");
        System.loadLibrary("ffmpeg");
        System.loadLibrary("YoloLive");
        enableLog(isDebug);
    }

    // 收流
    /*
        * We use a class initializer to allow the native code to cache some
        * field offsets. This native function looks up and caches interesting
        * class/field/method IDs. Throws on failure.
        */
    public static native int startReceive(String url);

    public static native int stopReceive(int id);

    public static native int getVideoEncodeConsumeMs();

    // 推流
    public static native int initSender(YoloLivePublishParam param);

    public static native int pushVideoData(byte[] yuvimage, long timestamp);

    public static native int pushAudioData(byte[] data, int size, long timestamp);

    public static native int closeSender();


    public static native int rgba2yuvRotate180(int width, int height, byte[] rgbaIn, byte[] yuvOut);

    public static native int rgba2yuvRotate180Flip(int width, int height, byte[] rgbaIn, byte[] yuvOut);


    /**
     * 默认是开启的
     *
     * @param enable
     */
    public static native void enableLog(boolean enable);
}
