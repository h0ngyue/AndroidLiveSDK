package com.yolo.livesdk.widget.publish_controller;

import android.content.Context;

import javax.annotation.Nonnull;

/**
 * Created by shuailongcheng on 30/11/2016.
 * <p>
 * 唯一注意的一点是，要在activity.onDestroy的时候调用destroy()
 */

public interface BeautyPublishController {
    boolean startPublish(@Nonnull Context context, @Nonnull String url, @Nonnull BeautyPublisherCallback lsn);

    boolean isPublishing();

    void stopPublish();

    void destroy();

    void switchUseBeauty();

    void switchMirror();

    void audioOn(boolean mute);

    void initPrefs(boolean initFrontCamera, boolean initUseBeauty, boolean initMirror, boolean portrait);

    /**
     * 这个只有前置摄像头的时候才有意义
     *
     * @return
     */
    boolean isMirror();

    boolean isBeautyOn();

    interface BeautyPublishControllee {
        void initPrefs(boolean initFrontCamera, boolean initUseBeauty, boolean initMirror, boolean portrait);

        void startPreviewOutput(@Nonnull BeautyCameraPreviewOutput previewOutput);

        void resetPreviewOutputListener();

        void destroy();

        void switchMirror();

        boolean isMirror();

        void switchBeautyOn();

        boolean isBeautyOn();

        void postUIThread(Runnable runnable);

        void switchCamera();

        void useFallbackFpsStrategy();

        void resumeRender();

        void pauseRender(boolean clear);
    }
}
