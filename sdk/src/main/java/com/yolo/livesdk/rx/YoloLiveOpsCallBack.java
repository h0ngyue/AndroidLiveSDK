package com.yolo.livesdk.rx;


import android.os.Looper;

import com.yolo.livesdk.rx.YoloLiveObs.AudioSpecInfo;
import com.yolo.livesdk.rx.YoloLiveObs.WidthHeightInfo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import rx.Emitter;
import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action0;
import rx.subscriptions.Subscriptions;
import timber.log.Timber;

final class YoloLiveOpsCallBack {

    private static volatile YoloLiveOpsCallBack INSTANCE = null;

    private final CompositeOnLiveCallbackListener mCompositeOnLiveCallbackListener;

    public static YoloLiveOpsCallBack getInstance() {
        if (INSTANCE == null) {
            synchronized (YoloLiveOpsCallBack.class) {
                if (INSTANCE == null) {
                    INSTANCE = new YoloLiveOpsCallBack();
                }
            }
        }

        return INSTANCE;
    }

    private YoloLiveOpsCallBack() {
        mCompositeOnLiveCallbackListener = new CompositeOnLiveCallbackListener();
        YoloLiveObs.setCallback(mCompositeOnLiveCallbackListener);
    }

    public Observable<RxYoloLive.NotifyEvent> notifyEvent() {
//        return Observable.fromEmitter(emitter -> {
//            final YoloLiveObs.LiveCallback listener = new YoloLiveObs.LiveCallbackHelper() {
//                @Override
//                public void notifyEvent(int i, String s) {
//                    emitter.onNext(new RxYoloLive.NotifyEvent(i, s));
//                }
//            };
//
//            mCompositeOnLiveCallbackListener.addListener(listener);
//            emitter.setCancellation(() -> mCompositeOnLiveCallbackListener.removeListener(listener));
//        }, Emitter.BackpressureMode.BUFFER);
        return Observable.create(new Observable.OnSubscribe<RxYoloLive.NotifyEvent>() {
            @Override
            public void call(final Subscriber<? super RxYoloLive.NotifyEvent> subscriber) {
                final YoloLiveObs.LiveCallback listener = new YoloLiveObs.LiveCallbackHelper() {
                    @Override
                    public void notifyEvent(int i, String s) {
                        subscriber.onNext(new RxYoloLive.NotifyEvent(i, s));
                    }
                };

                mCompositeOnLiveCallbackListener.addListener(listener);
                subscriber.add(unsubscribeInUiThread(() -> mCompositeOnLiveCallbackListener.removeListener(listener)));
            }
        }).onBackpressureDrop(notifyEvent -> Timber.d("onBackpressureDrop notifyEvent"));
    }

    public Observable<YoloLiveObs.VideoAudioData> notifyVideoData() {
        return Observable.fromEmitter(emitter -> {
            final YoloLiveObs.LiveCallback listener = new YoloLiveObs.LiveCallbackHelper() {
                @Override
                public void notifyVideoData(int id, byte[] bytes, int debugIndex) {
                    emitter.onNext(new YoloLiveObs.VideoAudioData(id, bytes, debugIndex));
                }
            };

            mCompositeOnLiveCallbackListener.addListener(listener);
            emitter.setCancellation(() -> mCompositeOnLiveCallbackListener.removeListener(listener));
        }, Emitter.BackpressureMode.DROP);
    }

    public Observable<WidthHeightInfo> notifyWidthHeight() {
        return Observable.fromEmitter(emitter -> {
            final YoloLiveObs.LiveCallback listener = new YoloLiveObs.LiveCallbackHelper() {
                @Override
                public int setWidthHeight(int id, int width, int height) {
                    emitter.onNext(new WidthHeightInfo(id, width, height));
                    return 0;
                }
            };

            mCompositeOnLiveCallbackListener.addListener(listener);
            emitter.setCancellation(() -> mCompositeOnLiveCallbackListener.removeListener(listener));
        }, Emitter.BackpressureMode.DROP);
    }

    public Observable<AudioSpecInfo> notifyAudioSpecInfo() {
        return Observable.fromEmitter(emitter -> {
            final YoloLiveObs.LiveCallback listener = new YoloLiveObs.LiveCallbackHelper() {
                @Override
                public int setAudioSpec(int id, int bitDepth, int nChannels,
                                        int sampleRate) {
                    emitter.onNext(new AudioSpecInfo(id, bitDepth, nChannels, sampleRate));
                    return 0;
                }
            };

            mCompositeOnLiveCallbackListener.addListener(listener);
            emitter.setCancellation(() -> mCompositeOnLiveCallbackListener.removeListener(listener));
        }, Emitter.BackpressureMode.DROP);
    }

    public Observable<YoloLiveObs.VideoAudioData> notifyAudioData() {
        return Observable.fromEmitter(emitter -> {
            final YoloLiveObs.LiveCallback listener = new YoloLiveObs.LiveCallbackHelper() {
                @Override
                public int notifyAudioData(int receiverId, byte[] data, int size) {
                    emitter.onNext(new YoloLiveObs.VideoAudioData(receiverId, data, 0));
                    return 0;
                }
            };

            mCompositeOnLiveCallbackListener.addListener(listener);
            emitter.setCancellation(() -> mCompositeOnLiveCallbackListener.removeListener(listener));
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

    private static class CompositeOnLiveCallbackListener implements YoloLiveObs.LiveCallback {

        private final List<YoloLiveObs.LiveCallback> listeners = new CopyOnWriteArrayList<>();

        boolean addListener(final YoloLiveObs.LiveCallback listener) {
            return listeners.add(listener);
        }

        public int size() {
            return listeners.size();
        }

        boolean removeListener(final YoloLiveObs.LiveCallback listener) {
            return listeners.remove(listener);
        }

        public void reset() {
            listeners.clear();
        }

        @Override
        public void notifyEvent(int i, String s) {
            for (YoloLiveObs.LiveCallback listener : listeners) {
                listener.notifyEvent(i, s);
            }
        }

        @Override
        public void notifyVideoData(int id, byte[] bytes, int debugIndex) {
            for (YoloLiveObs.LiveCallback listener : listeners) {
                listener.notifyVideoData(id, bytes, debugIndex);
            }
        }

        @Override
        public int notifyAudioData(int id, byte[] bytes, int i) {
            for (YoloLiveObs.LiveCallback listener : listeners) {
                listener.notifyAudioData(id, bytes, i);
            }
            return 0;
        }

        @Override
        public void onH264Video(byte[] data, int size, int type) {

        }

        @Override
        public int setWidthHeight(int id, int width, int height) {
            for (YoloLiveObs.LiveCallback listener : listeners) {
                listener.setWidthHeight(id, width, height);
            }
            return 0;
        }

        @Override
        public int setAudioSpec(int receiverId, int bitDepth, int nChannels, int sampleRate) {
            for (YoloLiveObs.LiveCallback listener : listeners) {
                listener.setAudioSpec(receiverId, bitDepth, nChannels, sampleRate);
            }
            return 0;
        }
    }
}
