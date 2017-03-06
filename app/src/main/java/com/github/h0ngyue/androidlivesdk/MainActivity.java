package com.github.h0ngyue.androidlivesdk;

import com.yolo.livesdk.widget.publish_3.BeautySurfaceView;
import com.yolo.livesdk.widget.publish_controller.BeautyPublishController;
import com.yolo.livesdk.widget.publish_controller.BeautyPublisherCallback;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;

public class MainActivity extends AppCompatActivity {

    BeautySurfaceView mBeautyPreview;

    private BeautyPublishController mBeautyPublishController;

    String mRtmpUrl = YOLO_PUBLISH_URL_PILI;

    /**
     * 播放地址
     */
    static final String YOLO_PUBLISH_URL_PILI
            = "rtmp://pili-publish.yoloyolo.tv/yolo/a99a74e81bc8edd88bcc86c5d3b2a537d35510c6?key=91435df3714c3e3c93705cf0cc6f88d95091f323";

    /**
     * 观看地址，在http://www.ossrs.net/players/srs_player.html可以观看
     */
    static final String YOLO_PLAY_URL_PILI
            = "rtmp://pili-live-rtmp.yoloyolo.tv/yolo/a99a74e81bc8edd88bcc86c5d3b2a537d35510c6";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initPreviewPublisher();
    }

    private void initPreviewPublisher() {
        // 现在这个没存pref,就是每场都默认开启麦克的
        mBeautyPreview = (BeautySurfaceView) findViewById(R.id.mBeautySurfaceView);
        mBeautyPublishController = mBeautyPreview.getController();
        mBeautyPublishController.audioOn(true);

        boolean initFrontCamera = true;
        boolean initUseBeauty = true;
        boolean initMirror = true;
        boolean portrait = true;
        mBeautyPublishController
                .initPrefs(initFrontCamera, initUseBeauty, initMirror,
                        portrait);

        findViewById(R.id.mBtnBeauty).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                mBeautyPublishController.switchUseBeauty();
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        startPublish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopPublish();
    }

    private void stopPublish() {
        mBeautyPublishController.stopPublish();
    }

    private void startPublish() {
        mBeautyPublishController
                .startPublish(MainActivity.this, mRtmpUrl, mBeautyPublisherCallback);
    }

    BeautyPublisherCallback mBeautyPublisherCallback = new BeautyPublisherCallback() {
        @Override
        public void onOpenCameraFail() {

        }

        @Override
        public void onCameraAccessFail() {

        }

        @Override
        public void onLiveStarted() {

        }

        @Override
        public void onInitPublishFail() {

        }

        @Override
        public void onStartPublishFail() {

        }

        @Override
        public void onSendError() {

        }

        @Override
        public void onSendErrorResume() {

        }

        @Override
        public void onPublishCpuIntence() {

        }

        @Override
        public void onRePublishError() {

        }

        @Override
        public void onFrame(byte[] yuvData, int width, int height, long timestampMs) {

        }
    };
}
