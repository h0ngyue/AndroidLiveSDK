package com.yolo.livesdk.widget.publish;

import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.FragmentManager;
import android.view.View;

import com.github.piasy.cameracompat.CameraCompat;

import org.greenrobot.eventbus.EventBus;

import javax.annotation.Nonnull;

/**
 * Created by shuailongcheng on 04/11/2016.
 */

public interface IPreviewPublish {
    /**
     * 关闭采集，停止推流
     * 回收资源（比如相机、audioRecorder),
     * 取消一些订阅的事件
     */
    void finish();

    /**
     * 开始打开音频、视频采集并推流
     * 每次調用，音頻只會初始化一次
     * 但是相机采集会重新创建，所以不要滥用
     *
     * @param url
     */
    boolean startAvCollectPublish(@Nonnull String url);

    boolean retryPublish();

    /**
     * 停止推流，但是没停止采集
     */
    void stopPublish();

    boolean isCameraOpened();

    int getVideoHeight();

    int getVideoWidth();

    /**
     * 改变预览的大小，但是不会改变相机采集的分辨率
     *
     * @param w
     * @param h
     * @return
     */
    boolean resizePreview(int w, int h);

    /**
     * 更换相机采集的分辨率
     *
     * @param w
     * @param h
     * @return
     */
    boolean changePreivewResolution(int w, int h);

    /**
     * don't call build() on this builder by yourself, there will be no effect
     *
     * @return
     */
    CameraCompat.Builder config();

    /**
     * retrolambda并不完全支持java 8，比如接口静态函数
     */
    class Builder {
        public static IPreviewPublish build(@Nonnull Context context,
                                            Bundle savedInstanceState,
                                            EventBus bus,
                                            @Nonnull FragmentManager fragmentManager,
                                            @Nonnull View cameraPreviewRootView,
                                            int cameraRootLayoutResId,
                                            @Nonnull LiveSenderCallback callback) {
            return new YoloLiveStreamSender(context, savedInstanceState, bus,
                    fragmentManager, cameraPreviewRootView, cameraRootLayoutResId, callback);
        }
    }
}
