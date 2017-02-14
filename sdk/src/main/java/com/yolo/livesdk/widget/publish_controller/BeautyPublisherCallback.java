package com.yolo.livesdk.widget.publish_controller;

/**
 * Created by shuailongcheng on 03/12/2016.
 */

public interface BeautyPublisherCallback {
    void onOpenCameraFail();

    void onCameraAccessFail();

    void onLiveStarted();

    void onInitPublishFail();

    void onStartPublishFail();

    void onSendError();

    void onSendErrorResume();

    void onPublishCpuIntence();

    void onRePublishError();

    void onFrame(byte[] yuvData, int width, int height, long timestampMs);
}
