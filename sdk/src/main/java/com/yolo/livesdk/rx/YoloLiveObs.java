package com.yolo.livesdk.rx;

import android.support.annotation.Keep;

import timber.log.Timber;

@Keep
public class YoloLiveObs {
    public static final int InnerError_StartPublisherFail = -2;

    public static final int InnerError_StartReceiverFail = -1;

    public static final int Publish_OK_Connect = 1;

    public static final int Publish_OK_SendingResume = 2;

    public static final int Publish_ERROR_NeedReCreate = 3;

    public static final int Publish_ERROR_NetworkBad = 4;

    /**
      * cpu资源紧张，上层能做的事情是：1.建议用户关闭美颜 2.app自动调整 采集率 fps到最低
      */
    public static final int Publish_STATUS_CPU_INTENCE = 5;

    /**
     * Watch_OK_Connect表示收到第一个数据包了（不仅仅是连接，而且表示有流），收流正式成功，上层可以进行一些ui处理了（比如隐藏"正在连接"的ui hint）
     */
    public static final int Watch_OK_Connect = 11;

    public static final int Watch_ERROR_NeedReCreate = 12;

    public static final int Watch_STATUS_StartBuffering = 13;

    public static final int Watch_STATUS_StopBuffering = 14;


    interface LiveCallback {

        void notifyEvent(int resultCode, String content);

        void notifyVideoData(int receiverId, byte[] data, int debugIndex);

        int notifyAudioData(int receiverId, byte[] data, int size);

        void onH264Video(byte[] data, int size, int type);

        int setWidthHeight(int receiverId, int width, int height);

        int setAudioSpec(int receiverId, int bitDepth, int nChannels, int sampleRate);
    }

    static class LiveCallbackHelper implements LiveCallback {

        @Override
        public void notifyEvent(int resultCode, String content) {

        }

        @Override
        public void notifyVideoData(int receiverId, byte[] data, int debugIndex) {

        }

        @Override
        public int notifyAudioData(int receiverId, byte[] data, int size) {
            return 0;
        }

        @Override
        public void onH264Video(byte[] data, int size, int type) {

        }

        @Override
        public int setWidthHeight(int receiverId, int width, int height) {
            return 0;
        }

        @Override
        public int setAudioSpec(int receiverId, int bitDepth, int nChannels, int sampleRate) {
            return 0;
        }
    }

    private static LiveCallback mObserver = null;

    static void setCallback(LiveCallback observer) {
        mObserver = observer;
    }

    static void onEvent(int type, String content) {
//        Timber.d("YoloLiveObs.onEvent, type:" + type + ", content:" + content);
        if (mObserver != null) {
            mObserver.notifyEvent(type, content);
        }
    }

    private static final String TAG = "YoloLiveObs";

    static int onWidthHeight(int id, int width, int height) {
        if (mObserver != null) {
            Timber.d("tag:transfer, YoloLiveObs.onWidthHeight()-> receiverId:" + id + ",width:"
                    + width + ", height:" + height);
            return mObserver.setWidthHeight(id, width, height);
        } else {
            Timber.e("tag:transfer, onWidthHeight observer == null!");
            return -1;
        }
    }

    static void onRawVideo(int id, byte[] data, int size, int debugIndex) {
        if (mObserver != null) {
            mObserver.notifyVideoData(id, data, debugIndex);
        } else {
            Timber.e("tag:transfer, onRawVideo observer == null!");
        }

    }

    static int onAudioSpec(int id, int bitDepth, int nChannels, int sampleRate) {
        if (mObserver != null) {
            return mObserver.setAudioSpec(id, bitDepth, nChannels, sampleRate);
        } else {
            return -1;
        }
    }

    static int onRawAudio(int id, byte[] data, int size) {
        if (mObserver != null) {
            return mObserver.notifyAudioData(id, data, size);
        } else {
            return -1;
        }
    }

    /**
     * Created by shuailongcheng on 7/18/16.
     */
    public static class VideoAudioData {

        public VideoAudioData(int id, byte[] data, int debugIndex) {
            this.id = id;
            this.data = data;
            this.debugIndex = debugIndex;
        }

        public final int id;

        public final byte[] data;
        public final int debugIndex;
    }

    public static class WidthHeightInfo {

        public WidthHeightInfo(int id, int width, int height) {
            this.id = id;
            this.width = width;
            this.height = height;
        }

        public final int id;

        public final int width;

        public final int height;

        @Override
        public String toString() {
            return String.format("id:%d, width:%d, height:%d", id, width, height);
        }
    }

    public static class AudioSpecInfo {

        public AudioSpecInfo(int id, int bitDepth, int nChannels, int sampleRate) {
            this.id = id;
            mBitDepth = bitDepth;
            mNChannels = nChannels;
            mSampleRate = sampleRate;
        }

        public final int id;

        public final int mBitDepth;

        public final int mNChannels;

        public final int mSampleRate;

        @Override
        public String toString() {
            return "id :" + id +
                    "\nmBitDepth :" + mBitDepth +
                    "\nmNChannels :" + mNChannels +
                    "\nmSampleRate :" + mSampleRate;
        }
    }
}