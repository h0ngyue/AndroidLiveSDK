package com.yolo.livesdk.widget.publish_controller;

import android.support.annotation.IntDef;

/**
 * Created by shuailongcheng on 15/11/2016.
 */

public interface BeautyCameraPreviewOutput {

    int START_CAMERA_FAIL = 1;
    int ACCESS_CAMERA_FAIL = 2;

    @IntDef(value = {START_CAMERA_FAIL, ACCESS_CAMERA_FAIL})
    @interface PREVIEW_ERROR_CODE {

    }

    void onFrameData(byte[] yuvData, int w, int h, long ts);

    /**
     * 通知相机启动失败之类的
     */
    void onError(@PREVIEW_ERROR_CODE int errorCode);

    void onPreviewOpened(int width, int height);
}
