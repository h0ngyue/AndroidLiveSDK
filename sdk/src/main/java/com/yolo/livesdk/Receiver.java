package com.yolo.livesdk;

import android.os.Looper;
import android.support.annotation.Keep;

import com.yolo.livesdk.rx.RxYoloLive;
import com.yolo.livesdk.rx.YoloLiveObs;

import java.util.concurrent.atomic.AtomicBoolean;

import rx.Emitter;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.subscriptions.Subscriptions;
import timber.log.Timber;

/**
 * Created by shuailongcheng on 28/12/2016.
 */

public class Receiver {
    private int mId;
    private AtomicBoolean mDestroyed = new AtomicBoolean(false);

    public Receiver(String url) {
        mId = nativeInit(url);
        if (mId <= 0) {
//            throw new IllegalStateException("nativeInit fail");
            Timber.e("nativeInit fail, return:%d", mId);
        }
    }

    public void start() {
        if (mDestroyed.get()) {
            Timber.e("start(),Receiver already Destroyed");
            return;
        }
        nativeStart(mId);
    }

    public void resume() {
        if (mDestroyed.get()) {
            Timber.e("resume(),Receiver already Destroyed");
            return;
        }
        nativeResume(mId);
    }

    public void restart() {
        if (mDestroyed.get()) {
            Timber.e("restart(),Receiver already Destroyed");
            return;
        }
        nativeRestart(mId);
    }

    public void prepare() {
        if (mDestroyed.get()) {
            Timber.e("prepare(),Receiver already Destroyed");
            return;
        }
        nativePrepare(mId);
    }

    public void destroy() {
        if (mDestroyed.getAndSet(true)) {
            Timber.e("destroy(),Receiver already Destroyed");
            return;
        }
        nativeDestroy(mId);
    }


    private Subscriber<? super RxYoloLive.NotifyEvent> eventSubscriber;

    public Observable<RxYoloLive.NotifyEvent> notifyEvent() {
        if (eventSubscriber != null) {
            throw new IllegalStateException("eventSubscriber already inited");
        }
        return Observable.create(new Observable.OnSubscribe<RxYoloLive.NotifyEvent>() {
            @Override
            public void call(final Subscriber<? super RxYoloLive.NotifyEvent> subscriber) {
                eventSubscriber = subscriber;
                subscriber.add(unsubscribeInUiThread(() -> eventSubscriber = null));
            }
        }).onBackpressureDrop(notifyEvent -> Timber.d("onBackpressureDrop notifyEvent"));
    }


    private Emitter<YoloLiveObs.VideoAudioData> videoDataEmitter;

    public Observable<YoloLiveObs.VideoAudioData> notifyVideoData() {
        if (videoDataEmitter != null) {
            throw new IllegalStateException("videoDataEmitter already inited");
        }
        return Observable.fromEmitter(emitter -> {
            videoDataEmitter = emitter;
            emitter.setCancellation(() -> videoDataEmitter = null);
        }, Emitter.BackpressureMode.DROP);
    }

    private Emitter<YoloLiveObs.WidthHeightInfo> whEmitter;

    public Observable<YoloLiveObs.WidthHeightInfo> notifyWidthHeight() {
        if (whEmitter != null) {
            throw new IllegalStateException("whEmitter already inited");
        }
        return Observable.fromEmitter(emitter -> {
            whEmitter = emitter;
            emitter.setCancellation(() -> whEmitter = null);
        }, Emitter.BackpressureMode.BUFFER);
    }

    private Emitter<YoloLiveObs.AudioSpecInfo> audioSpecEmitter;

    public Observable<YoloLiveObs.AudioSpecInfo> notifyAudioSpecInfo() {
        if (audioSpecEmitter != null) {
            throw new IllegalStateException("audioSpecEmitter already inited");
        }
        return Observable.fromEmitter(emitter -> {
            audioSpecEmitter = emitter;
            emitter.setCancellation(() -> audioSpecEmitter = null);
        }, Emitter.BackpressureMode.DROP);
    }

    private Emitter<YoloLiveObs.VideoAudioData> audioDataEmitter;

    public Observable<YoloLiveObs.VideoAudioData> notifyAudioData() {

        if (audioDataEmitter != null) {
            throw new IllegalStateException("audioDataEmitter already inited");
        }
        return Observable.fromEmitter(emitter -> {
            audioDataEmitter = emitter;
            emitter.setCancellation(() -> audioDataEmitter = null);
        }, Emitter.BackpressureMode.DROP);
    }

    private static Subscription unsubscribeInUiThread(final Action0 unsubscribe) {
        return Subscriptions.create(() -> {
            if (Looper.getMainLooper() == Looper.myLooper()) {
                unsubscribe.call();
            } else {
                final Scheduler.Worker inner = AndroidSchedulers.mainThread().createWorker();
                inner.schedule(() -> {
                    unsubscribe.call();
                    inner.unsubscribe();
                });
            }
        });
    }

    private ReceiverCallBack mObserver = new ReceiverCallBack() {
        @Override
        public void notifyEvent(int resultCode, String content) {
            if (eventSubscriber != null) {
                eventSubscriber.onNext(new RxYoloLive.NotifyEvent(resultCode, content));
            }
        }

        @Override
        public void notifyVideoData(int receiverId, byte[] data, int debugIndex) {
            if (videoDataEmitter != null) {
                videoDataEmitter.onNext(new YoloLiveObs.VideoAudioData(0, data, debugIndex));
            }
        }

        @Override
        public void notifyAudioData(int receiverId, byte[] data, int size) {
            if (audioDataEmitter != null) {
                audioDataEmitter.onNext(new YoloLiveObs.VideoAudioData(0, data, 0));
            }
        }

        @Override
        public void setWidthHeight(int receiverId, int width, int height) {
            if (whEmitter != null) {
                whEmitter.onNext(new YoloLiveObs.WidthHeightInfo(0, width, height));
            }
        }

        @Override
        public void setAudioSpec(int receiverId, int bitDepth, int nChannels, int sampleRate) {
            if (audioSpecEmitter != null) {
                audioSpecEmitter.onNext(new YoloLiveObs.AudioSpecInfo(0, bitDepth, nChannels, sampleRate));
            }
        }

        @Override
        public void onH264Video(byte[] data, int size, int type) {

        }

    };

    @Keep
    void onEvent(int type, String content) {
        if (mObserver != null) {
            mObserver.notifyEvent(type, content);
        }
    }

    @Keep
    void onWidthHeight(int width, int height) {
        mObserver.setWidthHeight(0, width, height);
    }

    @Keep
    void onRawVideo(byte[] data, int size, int debugIndex) {
        mObserver.notifyVideoData(0, data, debugIndex);

    }

    @Keep
    void onAudioSpec(int bitDepth, int nChannels, int sampleRate) {
        mObserver.setAudioSpec(0, bitDepth, nChannels, sampleRate);
    }

    @Keep
    void onRawAudio(byte[] data, int size) {
        mObserver.notifyAudioData(0, data, size);
    }

    private native int nativeInit(String url);

    private native void nativeDestroy(int id);

    private native void nativeStart(int id);

    private native void nativePrepare(int id);

    private native void nativeResume(int id);

    private native void nativeRestart(int id);

    public int id() {
        return mId;
    }


    public interface ReceiverCallBack {

        void notifyEvent(int resultCode, String content);

        void notifyVideoData(int receiverId, byte[] data, int debugIndex);

        void notifyAudioData(int receiverId, byte[] data, int size);

        void onH264Video(byte[] data, int size, int type);

        void setWidthHeight(int receiverId, int width, int height);

        void setAudioSpec(int receiverId, int bitDepth, int nChannels, int sampleRate);
    }
}
