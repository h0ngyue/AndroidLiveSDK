package com.yolo.livesdk.widget.publish;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.github.piasy.cameracompat.CameraCompat;
import com.github.piasy.rxandroidaudio.StreamAudioRecorder;
import com.utils.RxUtils;
import com.yolo.livesdk.YoloLiveNative;
import com.yolo.livesdk.rx.YoloLiveObs;
import com.yolo.livesdk.audio.YoloLiveAudioRecorder;
import com.yolo.livesdk.rx.RxYoloLive;
import com.yolo.livesdk.rx.YoloLivePublishParam;
import com.yolo.livesdk.widget.PublishConst;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.EventBusException;
import org.greenrobot.eventbus.Subscribe;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;
import timber.log.Timber;


public final class YoloLiveStreamSender
        implements CameraCompat.ErrorHandler, CameraCompat.VideoCaptureCallback, IPreviewPublish {

    public static final int PUBLISH_FAIL_TIMEOUT_SECONDS = 60;

    // sdk会有内部的重试，每次大概周期10~20秒左右，包括10次重发和3次重连再加10次重发
    private static final int MAX_LIVE_RECREATE_TIMES = 10; // 1 for test ,5 for formal

    private final YoloLiveAudioRecorder mYLLiveAudioRecorder;
    private final CameraCompat.Builder mCameraBuilder;
    WeakReference<Context> mContext;
    private final FragmentManager mFragmentManager;
    private final View mPreviewRootView;
    private final Bundle mSavedInstanceState;
    private final int mRootLayoutResId;
    private EventBus mBus;

    private final LiveSenderCallback mLiveSenderCallback;

    private boolean isStartNotified = false;

    private String mUrl;

    private CompositeSubscription mCompositeSubscription = new CompositeSubscription();

    private AtomicBoolean mCanNatviePush = new AtomicBoolean(false);
    private volatile boolean mCameraOpened;
    private volatile boolean mHasInitAudio = false;

    private int mVideoWidth;

    private int mVideoHeight;

    private CameraCompat mCameraCompat;

    private int mLiveReCreateTimes = 0;

    private Subscription mPublishTimeoutSubscription;

    private long mStartPublishTime = 0;

    private static final int DEFAULT_CAPTURE_WIDTH = 480;

    private static final int DEFAULT_CAPTURE_HEIGHT = 640;


    YoloLiveStreamSender(@Nonnull Context context,
                         Bundle savedInstanceState,
                         EventBus bus,
                         @Nonnull FragmentManager fragmentManager,
                         @Nonnull View cameraPreviewRootView,
                         int cameraRootLayoutResId,
                         @Nonnull LiveSenderCallback callback) {
        mContext = new WeakReference<>(context);
        mSavedInstanceState = savedInstanceState;
        mBus = bus;
        mFragmentManager = fragmentManager;
        mPreviewRootView = cameraPreviewRootView;
        mRootLayoutResId = cameraRootLayoutResId;
        mLiveSenderCallback = callback;
        mYLLiveAudioRecorder = YoloLiveAudioRecorder.getInstance();
        mCameraBuilder = new CameraCompat.Builder(this, this);


        if (!mBus.isRegistered(this)) {
            try {
                mBus.register(this);
            } catch (EventBusException e) {
                //e.printStackTrace();
            }
        }
        listenLiveObservable();
    }

    @Override
    public boolean startAvCollectPublish(@Nonnull String publishUrl) {
        if (TextUtils.isEmpty(publishUrl)) {
            throw new IllegalArgumentException("url can not be empty");
        }
        mUrl = publishUrl;

        // 相机会重新创建的
        mCompositeSubscription.add(Observable
                .fromCallable(() -> {
                    mLiveSenderCallback.waitForCameraIdle();
                    return true;
                })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(b -> {
                    mCameraCompat = mCameraBuilder.build();
                    mCameraCompat.startPreview(mSavedInstanceState,
                            mFragmentManager,
                            mRootLayoutResId);
                }, e -> onError(CameraCompat.ERR_UNKNOWN)));

        if (!mHasInitAudio) {
            startAudioCapture();
        }
        return true;
    }

    @Override
    public boolean retryPublish() {
        if (!checkArguments()) {
            Timber.e("invalid arguments to retryPublish, have you yet opened camera to fetch the video width and height before?");
            return false;
        }
        startNativePublish();
        return true;
    }

    @Override
    public boolean isCameraOpened() {
        synchronized (this) {
            return mCameraOpened;
        }
    }

    @Override
    public CameraCompat.Builder config() {
        return mCameraBuilder;
    }

    public void finish() {
        if (mBus.isRegistered(this)) {
            try {
                mBus.unregister(this);
            } catch (EventBusException e) {
                //e.printStackTrace();
            }
        }

        stopNativePublish();
        mCompositeSubscription.unsubscribe();
        stopAudioCapture();
        releaseCamera();
    }

    @Override
    public void stopPublish() {
        stopNativePublish();
    }


    @Override
    public boolean resizePreview(int w, int h) {
        synchronized (this) {
            if (!isCameraOpened()) {
                Timber.d("Camera not opened yet, can not be resized");
                return false;
            }
            mCameraOpened = false;
        }

        ViewGroup.LayoutParams lp = mPreviewRootView.getLayoutParams();
        if (lp == null) {
            lp = new FrameLayout.LayoutParams(w, h);
        } else {
            lp.width = w;
            lp.height = h;
        }
        mPreviewRootView.setLayoutParams(lp);

        removeOldPreviewFragmentIfNeeded();

        mCameraCompat = mCameraBuilder.build();
        mCameraCompat.startPreview(mSavedInstanceState,
                mFragmentManager, mRootLayoutResId);
        return true;
    }

    @Override
    public boolean changePreivewResolution(int w, int h) {
        synchronized (this) {
            if (!isCameraOpened()) {
                Timber.d("Camera not opened yet, can not be change preivew size");
                return false;
            }
            mCameraOpened = false;
        }

        removeOldPreviewFragmentIfNeeded();
        mCameraCompat = mCameraBuilder.previewWidth(w).previewHeight(h).build();
        mCameraCompat.startPreview(mSavedInstanceState,
                mFragmentManager, mRootLayoutResId);
        return true;
    }

    private void removeOldPreviewFragmentIfNeeded() {
        Fragment old = mFragmentManager.findFragmentByTag(CameraCompat.CAMERA_PREVIEW_FRAGMENT);
        if (old != null) {
            mFragmentManager.beginTransaction().remove(old).commit();
        }
    }

    @Override
    public int getVideoHeight() {
        return mVideoHeight == 0 ? DEFAULT_CAPTURE_HEIGHT : mVideoHeight;
    }

    @Override
    public int getVideoWidth() {
        return mVideoWidth == 0 ? DEFAULT_CAPTURE_WIDTH : mVideoWidth;
    }


    @Override
    public void onError(@CameraCompat.ErrorCode int errCode) {
        mLiveSenderCallback.onError(errCode);
    }

    @Override
    public void onVideoSizeChanged(int w, int h) {
        Timber.d("onVideoSizeChanged, w:" + w + ", h:" + h);

        synchronized (this) {
            mCameraOpened = true;

            if (mVideoWidth != w || mVideoHeight != h || !mCanNatviePush.get()) {
                mVideoWidth = w;
                mVideoHeight = h;
            }

            if (startNativePublish()) {
                mStartPublishTime = System.currentTimeMillis();
            } else {
                mLiveSenderCallback.onInitPublishFail();
            }
        }
    }


    @Override
    public void onFrameData(byte[] data, int width, int height) {
        if (mCanNatviePush.get()) {
            long curMs = System.currentTimeMillis();
            long curNs = System.nanoTime();
            YoloLiveNative.pushVideoData(data, curMs);
            long afterNs = System.nanoTime();
//            Timber.d("YoloLiveNative.pushVideoData, consume:%d ms", (afterNs - curNs)/1000_000);
            mLiveSenderCallback.onFrame(data, width, height, curMs - mStartPublishTime);
        }
    }

    private boolean startNativePublish() {
        YoloLivePublishParam.Builder builder = new YoloLivePublishParam.Builder(mUrl, mVideoWidth,
                mVideoHeight,
                YoloLivePublishParam.DEFAULT_SAMPLERATE, YoloLivePublishParam.DEFAULT_CHANNEL_NUM);
        builder.autoCrf(mContext.get()).rotateType(YoloLivePublishParam.ROTATE_COUNTER_CLOCKWISE_90);
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
                        stopNativePublish();
                        mLiveSenderCallback.onRePublishError();
                    }, RxUtils.IgnoreErrorProcessor);
            return true;
        } else {
            return false;
        }
    }

    @Subscribe
    public void stopNativePublish() {
        YoloLiveNative.closeSender();
    }

    private boolean checkArguments() {
        if (TextUtils.isEmpty(mUrl)) {
            throw new IllegalArgumentException("url can not be empty");
        }
        return mVideoWidth > 0 && mVideoHeight > 0;
    }


    private void releaseCamera() {
        mLiveSenderCallback.onReleaseCamera();
    }


    @Subscribe
    public void changeCamera(SenderControlEvent.ChangeCamera event) {
        if (mCameraCompat == null) {
            return;
        }
        mCameraCompat.switchCamera();
    }

    @Subscribe
    public void changeMirror(SenderControlEvent.ChangeMirror event) {
        if (mCameraCompat == null) {
            return;
        }
        mCameraCompat.switchMirror();
    }

    @Subscribe
    public void changeLight(SenderControlEvent.ChangeLight event) {
        if (mCameraCompat == null) {
            return;
        }
        mCameraCompat.switchFlash();
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
    public void stopPublishEvent(SenderControlEvent.StopPublish event) {
        stopNativePublish();
    }

    private synchronized void startAudioCapture() {
        Timber.d("reSend, startAudioCapture");
        if (!mHasInitAudio) {
            Timber.d("reSend, startAudioCapture, mHasInitAudio = " + mHasInitAudio);
            boolean ret = mYLLiveAudioRecorder
                    .start(PublishConst.DEFAULT_SAMPLERATE, PublishConst.DEFAULT_CHANNEL_CONFIG, PublishConst.DEFAULT_BITDEPTH_CONFIG,
                            new StreamAudioRecorder.AudioDataCallback() {
                                @Override
                                public void onAudioData(byte[] data, int size) {
                                    if (mCanNatviePush.get() && mCameraOpened) {
                                        YoloLiveNative.pushAudioData(data, size,
                                                System.currentTimeMillis());
                                    }
                                }

                                @Override
                                public void onError() {
                                    Timber.e("reSend onAudioData  LiveStreamSenderYoloImpl.startAudioCapture() fail");
                                }
                            });

            if (!ret) {
                Timber.e("reSend LiveStreamSenderYoloImpl.startAudioCapture() return false!");
            }

            mHasInitAudio = true;
        }
    }

    private synchronized void stopAudioCapture() {
        Timber.d("reSend, stopAudioCapture");
        mYLLiveAudioRecorder.stop();
        mHasInitAudio = false;
    }

    private synchronized void listenLiveObservable() {
        //CHECKSTYLE:OFF
        // the first subscriber: OK_PublishConnect, ERROR_PublishConnect, ERROR_Send
        mCompositeSubscription.add(RxYoloLive.notifyEvent()
//                .filter(notifyEvent -> notifyEvent.resultCode != YoloLiveObs.INFO_Speed_Upload)
                .observeOn(Schedulers.io())
                .subscribe(notifyEvent -> {
                    switch (notifyEvent.resultCode) {
                        case YoloLiveObs.Publish_OK_Connect:
                            Timber.d("SenderYoloImpl -> onEvent:YoloLiveObs.OK_PublishConnect");
                            // connect to vhall server success
                            if (!isStartNotified) {
                                // create connect success
                                isStartNotified = true;
                                mLiveSenderCallback.onLiveStarted();
                            } else {
                                // live reconnect success
                                mLiveSenderCallback.onSendErrorResume();
                            }

                            mLiveReCreateTimes = 0;
                            mCanNatviePush.set(true);
                            if (mPublishTimeoutSubscription != null && !mPublishTimeoutSubscription
                                    .isUnsubscribed()) {
                                mPublishTimeoutSubscription.unsubscribe();
                                mPublishTimeoutSubscription = null;
                            }
                            break;

                        // 界面对CODE_LIVE_PUBLISH_ON_ERROR和对这个错误码处理一样的，而且底层已经不再有ERROR_PublishConnect了
                        // 原来是 LiveObs.ERROR_PublishConnect 触发->LiveNetworkMonitorEvent.CODE_LIVE_PUBLISH_ON_ERROR
                        case YoloLiveObs.Publish_ERROR_NetworkBad:
                            mLiveSenderCallback.onSendError();
                            break;

                        case YoloLiveObs.Publish_ERROR_NeedReCreate:
                            // fail to send frame to vhall server, need reconnect
                            Timber.d(
                                    "SenderYoloImpl -> onEvent:YoloLiveObs.ERROR_PublishNeedReconnect");
                            if (mLiveReCreateTimes++ < MAX_LIVE_RECREATE_TIMES) {
                                Timber.d("还未达到最大重试创建直播的次数，mLiveReconnectTimes :"
                                        + mLiveReCreateTimes);
                                mLiveSenderCallback.onSendError();
                                if (!retryPublish()) {
                                    mLiveSenderCallback.onInitPublishFail();
                                }
                            } else {
                                Timber.d(
                                        "LiveStreamSenderYoloImpl, 已达到最大重试创建直播的次数，mLiveReconnectTimes :"
                                                + mLiveReCreateTimes);
                                mLiveSenderCallback.onRePublishError();
                            }
                            break;

                        case YoloLiveObs.InnerError_StartPublisherFail:
                            Timber.e(
                                    "LiveReceiverYoloImpl, 发起直播失败：InnerError_StartPublisherFail，停止推流");
                            mLiveSenderCallback.onStartPublishFail();
                            break;

                        default:
                            break;
                    }
                }, RxUtils.IgnoreErrorProcessor));
        //CHECKSTYLE:ON
    }
}
