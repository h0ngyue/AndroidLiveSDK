package com.yolo.livesdk.widget.publish_controller;

import android.content.Context;
import android.text.TextUtils;

import com.github.piasy.rxandroidaudio.StreamAudioRecorder;
import com.utils.RxUtils;
import com.yolo.beautycamera.camera.CameraEngine;
import com.yolo.beautycamera.util.Profiler;
import com.yolo.livesdk.YoloLiveNative;
import com.yolo.livesdk.audio.YoloLiveAudioRecorder;
import com.yolo.livesdk.rx.RxYoloLive;
import com.yolo.livesdk.rx.YoloLiveObs;
import com.yolo.livesdk.rx.YoloLivePublishParam;
import com.yolo.livesdk.widget.PublishConst;
import com.yolo.livesdk.widget.publish.SenderControlEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import rx.Observable;
import rx.Subscription;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;
import timber.log.Timber;

/**
 * Created by shuailongcheng on 30/11/2016.
 */

public class BeautyPublishControllerImpl implements BeautyPublishController {
    private static final boolean VERBOSE = false;
    private Boolean mIsPublishing = false;

    private AtomicBoolean mCanNatviePush = new AtomicBoolean(false);

    private WeakReference<Context> mContext;

    private BeautyPublisherCallback mBeautyPublisherCallback;

    private int mVideoWidth, mVideoHeight;

    private String mUrl;

    public static final int PUBLISH_FAIL_TIMEOUT_SECONDS = 60;

    private volatile boolean mHasInitAudio = false;

    private YoloLiveAudioRecorder mYLLiveAudioRecorder;

    private CompositeSubscription mCompositeSubscription;

    private boolean isStartNotified = false;

    private int mLiveReCreateTimes = 0;

    // sdk会有内部的重试，每次大概周期10~20秒左右，包括10次重发和3次重连再加10次重发
    private static final int MAX_LIVE_RECREATE_TIMES = 10; // 1 for test ,5 for formal

    private Subscription mPublishTimeoutSubscription;

    private volatile long mStartPublishTime = 0;

    private WeakReference<BeautyPublishControllee> mBeautyPublishControlleeWeak;

    private EventBus mBus = EventBus.getDefault();

    public BeautyPublishControllerImpl(BeautyPublishControllee beautyPublishControllee) {
        mYLLiveAudioRecorder = YoloLiveAudioRecorder.getInstance();
        mBeautyPublishControlleeWeak = new WeakReference<>(beautyPublishControllee);
        mBus.register(this);
    }

    public void initPrefs(boolean initFrontCamera, boolean initUseBeauty, boolean initMirror, boolean portrait) {
        BeautyPublishControllee beautyPublishControllee = mBeautyPublishControlleeWeak.get();
        if (beautyPublishControllee != null) {
            beautyPublishControllee.initPrefs(initFrontCamera, initUseBeauty, initMirror, portrait);
        }
    }

    @Subscribe
    public void changeCamera(SenderControlEvent.ChangeCamera event) {
        Timber.d("adjustSize changeCamera");
        BeautyPublishControllee beautyPublishControllee = mBeautyPublishControlleeWeak.get();
        if (beautyPublishControllee != null) {
            beautyPublishControllee.switchCamera();
        }
    }

    @Subscribe
    public void changeMirror(SenderControlEvent.ChangeMirror event) {
        Timber.d("adjustSize changeMirror");
        switchMirror();
    }

    @Subscribe
    public void switchCamerahLight(SenderControlEvent.ChangeLight event) {
        Timber.d("adjustSize switchCamerahLight");
        CameraEngine.getInstance().switchFlash();
    }

    @Subscribe
    public void switchUseBeauty(SenderControlEvent.SwitchUseBeauty event) {
        Timber.d("adjustSize switchUseBeauty");
        switchUseBeauty();
    }


    @Subscribe
    public void changeAudio(SenderControlEvent.ChangeAudio event) {
        boolean isAudioOn = event.isAudioOn();
        if (isAudioOn) {
            mYLLiveAudioRecorder.unMute();
        } else {
            mYLLiveAudioRecorder.mute();
        }
    }
    @Subscribe
    public void resumeRenderVideo(SenderControlEvent.ResumeRender event) {
        BeautyPublishControllee beautyPublishControllee = mBeautyPublishControlleeWeak.get();
        if (beautyPublishControllee != null) {
            beautyPublishControllee.resumeRender();
        }
    }


    @Subscribe
    public void pauseRenderVideo(SenderControlEvent.PauseRender event) {
        BeautyPublishControllee beautyPublishControllee = mBeautyPublishControlleeWeak.get();
        if (beautyPublishControllee != null) {
            beautyPublishControllee.pauseRender(event.shouldClear());
        }
    }


    @Override
    public boolean startPublish(@Nonnull Context context, @Nonnull String url, @Nonnull BeautyPublisherCallback lsn) {
        BeautyPublishControllee beautyPublishControllee = mBeautyPublishControlleeWeak.get();
        if (beautyPublishControllee == null) {
            throw new IllegalStateException("mPreviewRef.get() == null");
        }
        synchronized (mIsPublishing) {
            if (mIsPublishing) {
                Timber.d("publish is already started");
                return true;
            }
            mContext = new WeakReference<>(context);
            mUrl = url;
            mBeautyPublisherCallback = lsn;

            beautyPublishControllee.startPreviewOutput(mOutputListener);

            if (cameraOpened()) {
                mIsPublishing = doStartPublish();
                return mIsPublishing;
            }
        }
        return true;
    }


    public boolean isPublishing() {
        synchronized (mIsPublishing) {
            return mIsPublishing;
        }
    }

    private boolean cameraOpened() {
        return mVideoWidth != 0;
    }

    @Override
    public void stopPublish() {
        mCanNatviePush.set(false);
        synchronized (mIsPublishing) {
            stopAudioCapture();
            stopPublishNative();
            unlistenLiveObservable();

            BeautyPublishControllee beautyPublishControllee = mBeautyPublishControlleeWeak.get();
            if (beautyPublishControllee != null) {
                beautyPublishControllee.resetPreviewOutputListener();
            }
            mIsPublishing = false;
            mStartPublishTime = 0;
        }
    }

    @Override
    public void destroy() {
        stopPublish();
        BeautyPublishControllee beautyPublishControllee = mBeautyPublishControlleeWeak.get();
        if (beautyPublishControllee != null) {
            beautyPublishControllee.destroy();
        }

        if (mBus.isRegistered(this)) {
            mBus.unregister(this);
        }
        mBeautyPublisherCallback = null;
    }

    @Override
    public void switchUseBeauty() {
        BeautyPublishControllee beautyPublishControllee = mBeautyPublishControlleeWeak.get();
        if (beautyPublishControllee != null) {
            beautyPublishControllee.switchBeautyOn();
        }

    }

    @Override
    public void switchMirror() {
        BeautyPublishControllee beautyPublishControllee = mBeautyPublishControlleeWeak.get();
        if (beautyPublishControllee != null) {
            beautyPublishControllee.switchMirror();
        }
    }

    @Override
    public void audioOn(boolean setAudioOn) {
        if (setAudioOn) {
            mYLLiveAudioRecorder.unMute();
        } else {
            mYLLiveAudioRecorder.mute();
        }
    }

    @Override
    public boolean isMirror() {
        BeautyPublishControllee beautyPublishControllee = mBeautyPublishControlleeWeak.get();
        if (beautyPublishControllee != null) {
            return beautyPublishControllee.isMirror();
        }
        return false;
    }

    @Override
    public boolean isBeautyOn() {
        BeautyPublishControllee beautyPublishControllee = mBeautyPublishControlleeWeak.get();
        if (beautyPublishControllee != null) {
            return beautyPublishControllee.isBeautyOn();
        }
        return false;
    }

    Profiler p = new Profiler("BeautyCameraPreviewOutput");
    private final BeautyCameraPreviewOutput mOutputListener = new BeautyCameraPreviewOutput() {
        @Override
        public void onFrameData(byte[] yuvData, int w, int h, long ts) {
            Timber.v("onFrameData, timestamp:%d", ts);
            if (mCanNatviePush.get()) {

                p.reset();

                if (mStartPublishTime == 0) {
                    mStartPublishTime = ts;
                }

                long relativeTs = ts - mStartPublishTime;
//                Timber.d("pushVideoData, relativeTs:%d, curMs:%d, mStartPublishTime:%d", relativeTs, curMs, mStartPublishTime);
                YoloLiveNative.pushVideoData(yuvData, relativeTs);
//                p.tick("pushVideoData");

                BeautyPublisherCallback cb = BeautyPublishControllerImpl.this.mBeautyPublisherCallback;
                if (cb != null) {
                    cb.onFrame(yuvData, w, h, relativeTs);
//                    p.tick("cb.onFrame");
                }
                p.over("");
            }
        }

        @Override
        public void onError(@PREVIEW_ERROR_CODE int errorCode) {
            Timber.d("onPreviewOpened:onError:%d", errorCode);
            BeautyPublisherCallback cb = mBeautyPublisherCallback;
            if (cb == null) {
                return;
            }
            switch (errorCode) {
                case ACCESS_CAMERA_FAIL:
                    cb.onCameraAccessFail();
                    break;
                case START_CAMERA_FAIL:
                    cb.onOpenCameraFail();
                    break;

                default:
                    break;
            }
        }

        @Override
        public void onPreviewOpened(int width, int height) {
            Timber.d("onPreviewOpened:width:%d, height:%d", width, height);
            mVideoWidth = width;
            mVideoHeight = height;

            doStartPublish();
        }
    };

    private boolean checkArguments() {
        return mVideoWidth > 0 && mVideoHeight > 0 && !TextUtils.isEmpty(mUrl);
    }

    private boolean doStartPublish() {
        if (!checkArguments()) {
            Timber.e("doStartPublish, checkArguments fails, maybe you not yet opened camera to fetch the video width and height before?");
            return false;
        } else {
            Timber.d("doStartPublish, checkArguments ok, going to start publish");
        }

        BeautyPublishControllee beautyPublishControllee = mBeautyPublishControlleeWeak.get();
        if (beautyPublishControllee != null) {
            beautyPublishControllee.postUIThread(this::startAudioCapture);
        }
        listenLiveObservable();
        return startNativePublish();
    }

    private synchronized void unlistenLiveObservable() {
        if (mCompositeSubscription != null) {
            if (!mCompositeSubscription.isUnsubscribed()) {
                mCompositeSubscription.unsubscribe();
                mCompositeSubscription = null;
            }
        }
    }

    /**
     * 底层开始实际成功发送包的时候
     * 表示的是当前网络状态良好，并且开始正常发包（或者从无网络状态恢复）
     */
    private void onSendingResume(@Nonnull BeautyPublisherCallback cb) {

        if (!isStartNotified) {
            // create connect success
            isStartNotified = true;
            cb.onLiveStarted();
        } else {
            // live reconnect success
            cb.onSendErrorResume();
        }

        mLiveReCreateTimes = 0;
        if (mPublishTimeoutSubscription != null && !mPublishTimeoutSubscription
                .isUnsubscribed()) {
            mPublishTimeoutSubscription.unsubscribe();
            mPublishTimeoutSubscription = null;
        }
    }

    private synchronized void listenLiveObservable() {
        mCompositeSubscription = new CompositeSubscription();
        //CHECKSTYLE:OFF
        // the first subscriber: OK_PublishConnect, ERROR_PublishConnect, ERROR_Send
        mCompositeSubscription.add(RxYoloLive.notifyEvent()
//                .filter(notifyEvent -> notifyEvent.resultCode != YoloLiveObs.INFO_Speed_Upload)
                .observeOn(Schedulers.io())
                .subscribe(notifyEvent -> {
                    BeautyPublisherCallback cb = mBeautyPublisherCallback;
                    if (cb == null) {
                        return;
                    }
                    switch (notifyEvent.resultCode) {
                        case YoloLiveObs.Publish_OK_Connect:
                            if (VERBOSE) Timber.d(" onEvent:YoloLiveObs.Publish_OK_Connect");
                            mCanNatviePush.set(true);
                            break;

                        case YoloLiveObs.Publish_OK_SendingResume:
                            if (VERBOSE) Timber.d(" onEvent:YoloLiveObs.Publish_OK_SendingResume");
                            onSendingResume(cb);
                            break;

                        // 界面对CODE_LIVE_PUBLISH_ON_ERROR和对这个错误码处理一样的，而且底层已经不再有ERROR_PublishConnect了
                        // 原来是 LiveObs.ERROR_PublishConnect 触发->LiveNetworkMonitorEvent.CODE_LIVE_PUBLISH_ON_ERROR
                        case YoloLiveObs.Publish_ERROR_NetworkBad:
                            if (VERBOSE) Timber.d(" onEvent:YoloLiveObs.onSendError");
                            cb.onSendError();
                            break;

                        case YoloLiveObs.Publish_ERROR_NeedReCreate:
                            Timber.d(" onEvent:YoloLiveObs.ERROR_PublishNeedReconnect");
                            if (mLiveReCreateTimes++ < MAX_LIVE_RECREATE_TIMES) {
                                if (VERBOSE) Timber.d("还未达到最大重试创建直播的次数，mLiveReconnectTimes :"
                                        + mLiveReCreateTimes);
                                cb.onSendError();
                                if (!startNativePublish()) {
                                    cb.onInitPublishFail();
                                }
                            } else {
                                if (VERBOSE)
                                    Timber.d("onEvent  Publish_ERROR_NeedReCreate 已达到最大重试创建直播的次数，mLiveReconnectTimes :"
                                            + mLiveReCreateTimes);
                                cb.onRePublishError();
                            }
                            break;

                        case YoloLiveObs.InnerError_StartPublisherFail:
                            Timber.e("发起直播失败：InnerError_StartPublisherFail，停止推流");
                            cb.onStartPublishFail();
                            break;

                        case YoloLiveObs.Publish_STATUS_CPU_INTENCE:
                            Timber.e("onEvent：Publish_STATUS_CPU_INTENCE");
                            BeautyPublishControllee beautyPublishControllee = mBeautyPublishControlleeWeak.get();
                            if (beautyPublishControllee!=null) {
                                beautyPublishControllee.useFallbackFpsStrategy();
                            }
                            cb.onPublishCpuIntence();
                            break;

                        default:
                            throw new IllegalStateException("not used event");
                    }
                }, RxUtils.IgnoreErrorProcessor));
        //CHECKSTYLE:ON
    }

    private synchronized void startAudioCapture() {
        if (VERBOSE) Timber.d("reSend, startAudioCapture");
        if (!mHasInitAudio) {
            if (VERBOSE) Timber.d("reSend, startAudioCapture, mHasInitAudio = " + mHasInitAudio);
            boolean ret = mYLLiveAudioRecorder
                    .start(PublishConst.DEFAULT_SAMPLERATE, PublishConst.DEFAULT_CHANNEL_CONFIG, PublishConst.DEFAULT_BITDEPTH_CONFIG,
                            new StreamAudioRecorder.AudioDataCallback() {
                                @Override
                                public void onAudioData(byte[] data, int size) {
//                                    Timber.d("push Audio: mCanNatviePush:" + mCanNatviePush.get());
                                    if (mCanNatviePush.get()) {

                                        if (mStartPublishTime == 0) {
                                            Timber.d("pushAudioData, but mStartPublishTime ==0, reutrn");
                                            return;
                                        }

                                        long curMs = PublishTimeStamp.currentMs();
                                        long relativeTs = curMs - mStartPublishTime;
                                        YoloLiveNative.pushAudioData(data, size,
                                                relativeTs);
                                        long afterMs = System.nanoTime() / 1000_000;

//                                        Timber.d("pushAudioData, relativeTs:%d, curMs:%d, mStartPublishTime:%d", relativeTs, curMs, mStartPublishTime);
                                    }
                                }

                                @Override
                                public void onError() {
                                    Timber.e("reSend onAudioData  LiveStreamBeautyPublisherView.startAudioCapture() fail");
                                }
                            });

            if (!ret) {
                Timber.e("reSend LiveStreamBeautyPublisherView.startAudioCapture() return false!");
            }

            mHasInitAudio = true;
        }
    }


    private synchronized void stopAudioCapture() {
        if (VERBOSE) Timber.d("reSend, stopAudioCapture");
        mYLLiveAudioRecorder.stop();
        mHasInitAudio = false;
    }

    private boolean startNativePublish() {
        YoloLivePublishParam.Builder builder = new YoloLivePublishParam.Builder(mUrl, mVideoWidth,
                mVideoHeight,
                YoloLivePublishParam.DEFAULT_SAMPLERATE, YoloLivePublishParam.DEFAULT_CHANNEL_NUM);
        builder.autoCrf(mContext.get()).rotateType(YoloLivePublishParam.ROTATE_NONE);
        int ret = YoloLiveNative
                .initSender(builder.build());

        if (mPublishTimeoutSubscription != null && !mPublishTimeoutSubscription.isUnsubscribed()) {
            mPublishTimeoutSubscription.unsubscribe();
        }

        if (ret == 0) {
            mPublishTimeoutSubscription = Observable.just(true)
                    .delay(PUBLISH_FAIL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .subscribeOn(Schedulers.computation())
                    .subscribe(aBoolean -> {
                        stopPublishNative();
                        BeautyPublisherCallback cb = mBeautyPublisherCallback;
                        if (cb != null) {
                            mBeautyPublisherCallback.onRePublishError();
                        }
                    }, RxUtils.IgnoreErrorProcessor);
            return true;
        } else {
            return false;
        }
    }

    private void stopPublishNative() {
        if (VERBOSE) Timber.d("stopPublishNative");
        YoloLiveNative.closeSender();
        stopAudioCapture();
    }

}
