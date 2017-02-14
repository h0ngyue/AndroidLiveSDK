package com.yolo.livesdk.widget.publish;

import com.github.piasy.cameracompat.CameraCompat;

/**
 * Created by shuailongcheng on 04/11/2016.
 */
public interface LiveSenderCallback extends CameraCompat.ErrorHandler {

    void waitForCameraIdle() throws InterruptedException;

    void onReleaseCamera();

    void onLiveStarted();

    void onFrame(byte[] data, int w, int h, long timestampMs);

    void onInitPublishFail();

    void onStartPublishFail();

    void onSendError();

    void onSendErrorResume();

    void onRePublishError();
}
