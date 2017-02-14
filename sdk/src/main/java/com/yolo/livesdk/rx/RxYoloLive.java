package com.yolo.livesdk.rx;


import rx.Observable;

public final class RxYoloLive {

    private RxYoloLive() {
        //no instance
    }

    public static Observable<NotifyEvent> notifyEvent() {
        return YoloLiveOpsCallBack.getInstance().notifyEvent();
    }

    public static Observable<YoloLiveObs.VideoAudioData> notifyVideoData() {
        return YoloLiveOpsCallBack.getInstance().notifyVideoData();
    }

    public static Observable<YoloLiveObs.VideoAudioData> notifyAudioData() {
        return YoloLiveOpsCallBack.getInstance()
                .notifyAudioData();
    }

    public static Observable<YoloLiveObs.WidthHeightInfo> notifyWidthHeight() {
        return YoloLiveOpsCallBack.getInstance()
                .notifyWidthHeight();
    }

    public static Observable<YoloLiveObs.AudioSpecInfo> notifyAudioSpecInfo() {
        return YoloLiveOpsCallBack.getInstance()
                .notifyAudioSpecInfo();
    }


    //CHECKSTYLE:OFF
    public static class NotifyEvent {
        public int id;

        public int resultCode;

        public String content;

        public NotifyEvent(int resultCode, String content) {
            this.resultCode = resultCode;
            this.content = content;
        }
    }
    //CHECKSTYLE:ON

}
