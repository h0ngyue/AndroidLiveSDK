package com.utils;


import rx.functions.Action1;
import timber.log.Timber;

/**
 * Created by guyacong on 14/12/14.
 *
 * see https://git.yoloyolo.tv/Android/YOLO-Android/blob/dev/wiki/YOLONetworkLayer.md#%E9%94%99%E8%AF%AF%E5%A4%84%E7%90%86
 */
public final class RxUtils {

    private RxUtils() {
        //no instance
    }

    // CHECKSTYLE:OFF
    /**
     * used for Rx network error handling
     *
     * Usage: Observable.subscribe(onNext, RxUtils.NetErrorProcessor)
     *
     * run in the observeOn() thread
     *
     * onErrorReturn run in subscribeOn thread (retrofit run in background thread, not good for
     * error handling)
     *
     * Note: if you handle onError for the net request, than you should call it manually in other
     * case:
     *
     * if (manually) {
     *     // manually
     * } else {
     *     RxUtils.NetErrorProcessor.call(throwable);
     * }
     */
    public static final Action1<Throwable> NetErrorProcessor = throwable -> {
//        if (throwable != null && throwable instanceof YLApiError) {
//            YLApiErrorProcessor.process((YLApiError) throwable);
//        } else {
//            //ignore other errors, but log it
//            Timber.e(throwable, "RxUtils.NetErrorProcessor");
//        }
        throwable.printStackTrace();
    };

    public static final Action1<Throwable> IgnoreErrorProcessor = throwable -> {
        // ignore it
        Timber.e(throwable, "RxUtils.IgnoreErrorProcessor");
        throwable.printStackTrace();
    };

    @SuppressWarnings("unchecked")
    public static <T>Action1<T> idleAction() {
        return IdleAction;
    }

    private static final Action1 IdleAction = t -> {
        // do nothing
    };
    // CHECKSTYLE:ON
}
