package com.yolo.livesdk.widget.watch;

import android.media.AudioFormat;
import android.opengl.GLSurfaceView;
import android.text.TextUtils;
import android.util.Log;

import com.github.piasy.rxandroidaudio.StreamAudioPlayer;
import com.render_view.YUVRenderNew;
import com.utils.RxUtils;
import com.yolo.livesdk.YoloLiveNative;
import com.yolo.livesdk.rx.RxYoloLive;
import com.yolo.livesdk.rx.YoloLiveObs;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Created by shuailongcheng on 17/12/2016.
 */

public class WatchViewManager {
    int AUDIO_FRAME_BUFFER_SIZE = 4096;

    private String mUrl;

    private boolean mStarted = false;

    private int mStreamId;

    private volatile boolean mHasInitAudio = false;

    private StreamAudioPlayer mStreamAudioPlayer;

    /**
     * 视频实际的分辨率
     */
    private float mRenderPixelWidth, mRenderPixelHeight;

    private List<Subscription> mSubscriptionList = new ArrayList<>();

    public WatchViewManager() {
        mStreamAudioPlayer = StreamAudioPlayer.getInstance();
    }

    private GLSurfaceView mGlSurfaceView;
    private YUVRenderNew mYuvRenderNew;

    public void setupGLSurfaceView(GLSurfaceView glSurfaceView) {
        Timber.d("testRender setupGLSurfaceView");
        mGlSurfaceView = glSurfaceView;
        mGlSurfaceView.setEGLContextClientVersion(2);
        mGlSurfaceView.getHolder().setKeepScreenOn(true);
        mYuvRenderNew = new YUVRenderNew();
        mYuvRenderNew.setGLSurfaceView(glSurfaceView);
        mGlSurfaceView.setRenderer(mYuvRenderNew);
        mGlSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
    }

    public void updateScreenAll(byte[] YUV) {
        try {
            mYuvRenderNew.setYUVDataAll(YUV);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private void initRenderer(int pixelWidth, int pixelHeight) {
        Timber.d("testRender initRenderer");
        mYuvRenderNew.initGLInitTask(pixelWidth, pixelHeight);
    }

    /**
     * 多次调用有副作用,不要多次调用
     *
     * @param url
     */
    public void init(String url) {
        mUrl = url;

        addSubscription(RxYoloLive.notifyWidthHeight()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(whInfo -> {
                    if (mStreamId != whInfo.id) {
                        return;
                    }

                    Timber.e("onWidthHeight, whInfo:%s", whInfo.toString());
                    onVideoResolutionWH(whInfo.width, whInfo.height);

                }, RxUtils.IgnoreErrorProcessor));

        // 这里不要随便在subscribe的时候切到io()线程，否则不能保证帧的有序性
        addSubscription(RxYoloLive.notifyVideoData()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(videoData -> {
                    if (mStreamId != videoData.id) {
                        return;
                    }

                    if (videoData.debugIndex < mLastDebugIndex) {
//                        Timber.e("mPlayView.UpdateScreenAll, video Frame debugIndex:%d, but mLastDebugIndex:%d", videoData.debugIndex, mLastDebugIndex);
                        // 权宜之计,目前确实是因为会有更小index的帧反复来几下,出现抖动,原因还没找到
                        return;
                    } else {
//                        Timber.v("mPlayView.UpdateScreenAll, video Frame debugIndex:%d", videoData.debugIndex);
                    }
                    mLastDebugIndex = videoData.debugIndex;
                    updateScreenAll(videoData.data);
                }, RxUtils.IgnoreErrorProcessor));

        addSubscription(RxYoloLive.notifyAudioSpecInfo()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(audioInfo -> {
//                    Timber.d("RxYoloLive.notifyAudioSpecInfo:" + audioInfo);
                    if (mStreamId != audioInfo.id) {
                        return;
                    }
                    Log.e("notifyAudioSpecInfo", "audioInfo:" + audioInfo);
                    initAudioPlayer(audioInfo);
                }, RxUtils.IgnoreErrorProcessor));

        // 这里不要随便在subscribe的时候切到io()线程，否则不能保证帧的有序性
        addSubscription(RxYoloLive.notifyAudioData()
                .observeOn(Schedulers.computation())
                .subscribe(data -> {
//                    Timber.d("RxYoloLive.notifyAudioData , id:" + data.id + ", size:" + data.data.length);
                    if (mStreamId != data.id) {
                        return;
                    }

                    if (mStreamAudioPlayer != null) {
                        mStreamAudioPlayer.play(data.data, data.data.length);
                    } else {
                        Log.e("notifyAudioData",
                                "LiveStreamReceiver.notifyAudioData. but mPlayView not ready");
                    }
                }));
    }

    /**
     * 可以多次调用,始终只会有一个收流实例,底层会把之前的冲掉
     * 所以一些场景下可以当成restart直接调用
     *
     * @return
     */
    public synchronized boolean start() {
        mStarted = true;

        if (TextUtils.isEmpty(mUrl)) {
            return false;
        }

        startNative();

        return true;
    }

    /**
     * 多次调用无害
     */
    public synchronized void stop() {
        if (!mStarted) {
            return;
        }
        mStarted = false;
        for (Subscription sc : mSubscriptionList) {
            if (!sc.isUnsubscribed()) {
                sc.unsubscribe();
            }
        }

        YoloLiveNative.stopReceive(mStreamId);
        Timber.d("StreamAudioPlayer  mStreamAudioPlayer.release()");
    }

    /**
     * 1.保证在销毁的时候调用
     * 2.多次调用无害,但是如果多次调用的话,说明对这个接口的使用不是很对
     */
    public void destroy() {
        stop();
        mStreamAudioPlayer.release();
    }

    public boolean isStarted() {
        return mStarted;
    }


    public Observable<RxYoloLive.NotifyEvent> getEventObservable() {
        return RxYoloLive.notifyEvent();
    }


    private void addSubscription(Subscription sc) {
        mSubscriptionList.add(sc);
    }

    private int mLastDebugIndex;

    private synchronized void startNative() {
        mStreamId = YoloLiveNative.startReceive(mUrl);
        Timber.d("reRecv, startNative, mStreamId=" + mStreamId);
    }

    public float getRenderPixelWidth() {
        return mRenderPixelWidth;
    }

    public float getRenderPixelHeight() {
        return mRenderPixelHeight;
    }

    /**
     * // 不耗时
     */
    private synchronized void onVideoResolutionWH(int pixelWidth, int pixelHeight) {
        Timber.d("testRender onVideoResolutionWH: mVideoPixelWidth:" + mRenderPixelWidth
                + ", mVideoPixelHeight:" + mRenderPixelHeight
                + ", pixelWidth:" + pixelWidth
                + ", pixelHeight:" + pixelHeight);

        if (mRenderPixelWidth == pixelWidth
                && mRenderPixelHeight == pixelHeight && mStarted) {
            return;
        }

        mRenderPixelWidth = pixelWidth;
        mRenderPixelHeight = pixelHeight;

        Timber.d("testRender onVideoResolutionWH, not addGLSurfacViewAndInitRenderer");
        initRenderer(pixelWidth, pixelHeight);
    }


    private synchronized void initAudioPlayer(YoloLiveObs.AudioSpecInfo audioInfo) {
        if (mHasInitAudio) {
            return;
        }

        int channelConfig = audioInfo.mNChannels == 1 ? AudioFormat.CHANNEL_OUT_MONO
                : AudioFormat.CHANNEL_OUT_STEREO;
        int audioConfig = AudioFormat.ENCODING_PCM_16BIT;
        if (audioInfo.mBitDepth == 16) {

        } else if (audioInfo.mBitDepth == 8) {
            audioConfig = AudioFormat.ENCODING_PCM_8BIT;
        } else {
            throw new IllegalArgumentException(
                    "dont support bit depth of " + audioInfo.mBitDepth);
        }
        mStreamAudioPlayer.init(audioInfo.mSampleRate, channelConfig, audioConfig,
                AUDIO_FRAME_BUFFER_SIZE);

        mHasInitAudio = true;
    }

}
