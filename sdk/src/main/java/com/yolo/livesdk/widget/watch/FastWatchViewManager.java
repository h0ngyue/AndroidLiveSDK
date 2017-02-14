package com.yolo.livesdk.widget.watch;

import android.media.AudioFormat;
import android.opengl.GLSurfaceView;
import android.text.TextUtils;

import com.github.piasy.rxandroidaudio.StreamAudioPlayer;
import com.render_view.YUVRenderNew;
import com.utils.RxUtils;
import com.yolo.livesdk.Receiver;
import com.yolo.livesdk.rx.YoloLiveObs;

import java.util.ArrayList;
import java.util.List;

import rx.Scheduler;
import rx.Subscription;
import rx.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Created by shuailongcheng on 17/12/2016.
 */

public class FastWatchViewManager {
    int AUDIO_FRAME_BUFFER_SIZE = 4096;

    private String mUrl;

    private boolean mStarted = false;
    private boolean mPrepared = false;
    private boolean mResumed = false;

    private volatile boolean mHasInitAudio = false;

    private StreamAudioPlayer mStreamAudioPlayer;

    private Receiver mReceiver;
    /**
     * 视频实际的分辨率
     */
    private float mRenderPixelWidth, mRenderPixelHeight;

    private List<Subscription> mSubscriptionList = new ArrayList<>();

    public String getUrl() {
        return mUrl;
    }

    private YUVRenderNew mYuvRenderNew = new YUVRenderNew();

    private volatile boolean mGLInstalled = false;

    public synchronized void setupGLSurfaceView(GLSurfaceView glSurfaceView) {
        Timber.d("setupGLSurfaceView");
        glSurfaceView.setEGLContextClientVersion(2);
        glSurfaceView.getHolder().setKeepScreenOn(true);
        mYuvRenderNew.setGLSurfaceView(glSurfaceView);
        glSurfaceView.setRenderer(mYuvRenderNew);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        mGLInstalled = true;
    }

    public void setListener(FastWatchStatusListener listener) {
        synchronized (mStatusListenerSync) {
            if (listener == null) {
                return;
            }
            mStatusListener = listener;
            if (mStatus_StartWatchFailed) {
                listener.onReconnectFail();
                Timber.d("setListener, mStatus_StartWatchFailed = true, return");
                return;
            }
            if (mLastEventStatus == 0) {
                return;
            }
            notifyEventToListener(mLastEventStatus, true);
        }
    }

    public void updateScreenAll(byte[] YUV) {
        if (!mGLInstalled) {
            return;
        }
        try {
            mYuvRenderNew.setYUVDataAll(YUV);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private volatile int mPixelWidth, mPixelHeight;

    private synchronized void initRenderer(int pixelWidth, int pixelHeight) {
        Timber.d("testRender initRenderer");
        mPixelWidth = pixelWidth;
        mPixelHeight = pixelHeight;

        mYuvRenderNew.initGLInitTask(pixelWidth, pixelHeight);
    }

    public synchronized void reInitRenderer() {
        mYuvRenderNew = new YUVRenderNew();
        if (mPixelWidth != 0) {
            mYuvRenderNew.initGLInitTask(mPixelWidth, mPixelHeight);
        }
    }

    private boolean mStatus_StartWatchFailed;
    // 底层的内部重试大概会有10~20秒，3次重连，3次重新获取包
    private static final int MAX_LIVE_RECREATE_TIMES = 25; // 1 for test, 25 for formal
    private int mLiveReCreateTimes = 0;
    private int mLastEventStatus;

    private FastWatchStatusListener mStatusListener;
    private Object mStatusListenerSync = new Object();

    /**
     * 多次调用有副作用,不要多次调用
     *
     * @param url
     */
    public void init(String url) {
        mUrl = url;

        mReceiver = new Receiver(mUrl);
        addSubscription(mReceiver.notifyWidthHeight()
//                .observeOn(schedulerAll)
                .subscribe(whInfo -> {
                    onVideoResolutionWH(whInfo.width, whInfo.height);
                }, RxUtils.IgnoreErrorProcessor));

        // 这里不要随便在subscribe的时候切到io()线程，否则不能保证帧的有序性
        addSubscription(mReceiver.notifyVideoData()
//                .observeOn(schedulerAll)
                .subscribe(videoData -> {
//                    if (videoData.debugIndex < mLastDebugIndex) {
//                        Timber.e("mPlayView.UpdateScreenAll, video Frame debugIndex:%d, but mLastDebugIndex:%d", videoData.debugIndex, mLastDebugIndex);
//                        // 权宜之计,目前确实是因为会有更小index的帧反复来几下,出现抖动,原因还没找到
//                        return;
//                    }

                    mLastDebugIndex = videoData.debugIndex;
                    updateScreenAll(videoData.data);
                }, RxUtils.IgnoreErrorProcessor));

        addSubscription(mReceiver.notifyAudioSpecInfo()
//                .observeOn(schedulerAll)
                .subscribe(audioInfo -> {
//                    Timber.w("notifyAudioSpecInfo, audioInfo:" + audioInfo);
                    initAudioPlayer(audioInfo);
                }, RxUtils.IgnoreErrorProcessor));

        // 这里不要随便在subscribe的时候切到io()线程，否则不能保证帧的有序性
        addSubscription(mReceiver.notifyAudioData()
//                .observeOn(schedulerAll)
                .subscribe(data -> {
                    if (mStreamAudioPlayer != null) {
                        mStreamAudioPlayer.play(data.data, data.data.length);
//                        Timber.d("notifyAudioData");
                    } else {
                        Timber.e("notifyAudioData, but mPlayView not ready");
                    }
                }));

        addSubscription(mReceiver.notifyEvent()
                .subscribe(notifyEvent -> {
                    synchronized (mStatusListenerSync) {
                        notifyEventToListener(notifyEvent.resultCode, false);
                        mLastEventStatus = notifyEvent.resultCode;
                    }
                }, RxUtils.IgnoreErrorProcessor));
    }

    private void notifyEventToListener(int statusCode, boolean justNotify) {
        switch (statusCode) {
            case YoloLiveObs.Watch_OK_Connect:
                if (!justNotify) {
                    Timber.d("YoloLiveObs.Watch_OK_Connect  >>>>>>>>>>>>>>>>>>>>>> ");
                }
                if (mStatusListener != null) {
                    mStatusListener.onStartWatchSuccess();
                }
                break;

            case YoloLiveObs.InnerError_StartReceiverFail:
                if (!justNotify) {
                    Timber.e("YoloLiveObs.InnerError_StartReceiverFail, start recv:InnerError_StartReceiverFail，stop recv");
                    mStatus_StartWatchFailed = true;
                    mReceiver.destroy();
                }
                if (mStatusListener != null) {
                    mStatusListener.onStartWatchFail();
                }
                break;

            case YoloLiveObs.Watch_ERROR_NeedReCreate:
                if (mLiveReCreateTimes++ < MAX_LIVE_RECREATE_TIMES) {
                    if (!justNotify) {
                        Timber.d("YoloLiveObs.Watch_ERROR_NeedReCreate,no reach MAX_LIVE_RECREATE_TIMES，mLiveReconnectTimes:%d", mLiveReCreateTimes);
                        mReceiver.restart();
                    }

                    if (mStatusListener != null && mLastEventStatus != YoloLiveObs.Watch_ERROR_NeedReCreate) {
                        mStatusListener.onReconnecting();
                    }
                } else {
                    Timber.d("YoloLiveObs.Watch_ERROR_NeedReCreate, already reach MAX_LIVE_RECREATE_TIMES，mLiveReconnectTimes:%d", mLiveReCreateTimes);
                    if (mStatusListener != null) {
                        mStatusListener.onReconnectFail();
                    }
                }
                break;

            case YoloLiveObs.Watch_STATUS_StartBuffering:
                Timber.d("YoloLiveObs.Watch_STATUS_StartBuffering");
                if (mStatusListener != null) {
                    mStatusListener.onBufferingStart();
                }
                break;

            case YoloLiveObs.Watch_STATUS_StopBuffering:
                if (mStatusListener != null) {
                    Timber.w("YoloLiveObs.Watch_STATUS_StopBuffering mStatusListener != null");
                    mStatusListener.onBufferingComplete();
                } else {
                    Timber.e("YoloLiveObs.Watch_STATUS_StopBuffering,  but mStatusListener = null");
                }
                break;

            default:
                throw new IllegalStateException("undealt event");
        }
    }


    /**
     * 可以多次调用,始终只会有一个收流实例,底层会把之前的冲掉
     * 所以一些场景下可以当成restart直接调用
     *
     * @return
     */
    public synchronized boolean start() {
        if (mStarted) {
            return true;
        }
        mStarted = true;

        if (TextUtils.isEmpty(mUrl)) {
            return false;
        }

        mReceiver.start();

        return true;
    }

    public synchronized boolean prepare() {
        if (mPrepared) {
            return true;
        }
        mPrepared = true;

        if (TextUtils.isEmpty(mUrl)) {
            return false;
        }

        mReceiver.prepare();

        return true;
    }

    public synchronized void resume() {
        if (mResumed) {
            return;
        }
        mResumed = true;
        mStreamAudioPlayer = StreamAudioPlayer.getInstance();
        mReceiver.resume();
    }

    /**
     * 多次调用无害
     */
    private void stop() {
        if (!mStarted && !mPrepared) {
            return;
        }
        mStarted = false;
        mPrepared = false;
        for (Subscription sc : mSubscriptionList) {
            if (!sc.isUnsubscribed()) {
                sc.unsubscribe();
            }
        }

        mReceiver.destroy();
        Timber.d("StreamAudioPlayer  mStreamAudioPlayer.release()");
    }

    /**
     * 1.保证在销毁的时候调用
     * 2.多次调用无害,但是如果多次调用的话,说明对这个接口的使用不是很对
     */
    public synchronized void destroy() {
        stop();
        mStreamAudioPlayer.release();
    }

    public boolean isStarted() {
        return mStarted;
    }


//    public Observable<RxYoloLive.NotifyEvent> getEventObservable() {
//        return RxYoloLive.notifyEvent();
//    }


    private void addSubscription(Subscription sc) {
        mSubscriptionList.add(sc);
    }

    private int mLastDebugIndex;


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
        Timber.d("onVideoResolutionWH: mVideoPixelWidth:" + mRenderPixelWidth
                + ", mVideoPixelHeight:" + mRenderPixelHeight
                + ", pixelWidth:" + pixelWidth
                + ", pixelHeight:" + pixelHeight);

        if (mRenderPixelWidth == pixelWidth
                && mRenderPixelHeight == pixelHeight && mStarted) {
            return;
        }

        mRenderPixelWidth = pixelWidth;
        mRenderPixelHeight = pixelHeight;

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


    public int getId() {
        return mReceiver.id();
    }
}
