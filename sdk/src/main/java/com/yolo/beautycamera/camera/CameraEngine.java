package com.yolo.beautycamera.camera;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Build;
import android.support.annotation.IntDef;

import com.yolo.beautycamera.beauty_preview.BeautyCamera;

import timber.log.Timber;


/**
 * Created by shuailongcheng on 16/11/2016.
 */
public abstract class CameraEngine {
    public static final int ERROR_IOEXCEPTION = 1;
    public static final int ERROR_CAMERA_ACCESS_FAIL = 2;
    public static final int ERROR_CAMERA2_ILLEGAL_STATE = 3;
    public static final int ERROR_CAMERA2_CONFIGURE_FAIL = 4;

    @IntDef(value = {
            ERROR_IOEXCEPTION,
            ERROR_CAMERA_ACCESS_FAIL,
            ERROR_CAMERA2_ILLEGAL_STATE,
            ERROR_CAMERA2_CONFIGURE_FAIL,
    })
    public @interface ErrorCode {

    }

    public interface OnPreviewListener {
        void onPreviewInfo(int previewWidth, int previewHeight, boolean isCamera1, boolean isFront);

        void onError(@ErrorCode int error);
    }

    protected boolean mFrontCamera = true;
    protected boolean mFlashOn = false;

    public void initUseFrontCamera(boolean use) {
        mFrontCamera = use;
    }

    // TODO: 21/11/2016 这里应该加上两个参数，desireWidth和desireHeight ，camera1和camera2根据自己的api来判断能开多少分辨率
    // TODO 比如camera2 的步骤就是，先根据这两个宽高，来 findOptSize，然后surfaceTexture来setDefaultSize(width, height);
    // TODO 而且必须是在new Surface(surfaceTexture)之前来完成这件事
    // MARK: lifecycle
    public abstract void startPreview(SurfaceTexture surfaceTexture, OnPreviewListener listener, boolean portrait);

    // 不需要这两个了，因为切到最小化的时候会进入MagicCameraViewNew: surfaceDestroyed，在哪里已经releaseCamera了，，
    // 然后在界面resume的时候，会重新进入surfaceCreated
//    public abstract void pausePreview();
//
//    public abstract void resumePreview();

    /**
     * 如果完全需要使用cemera了，应该记得调用一下
     */
    public abstract void releaseCamera();

    // MARK: function during camera lifecycle
    public abstract void switchCamera();

    public abstract void switchFlash();

    public abstract int getOrientation();

    public boolean isFrontCamera() {
        return mFrontCamera;
    }

    public boolean isFlashOn() {
        return mFlashOn;
    }


    public static CameraEngine getInstance() {
        if (INSTANCE == null) {
            synchronized (CameraEngine.class) {
                if (INSTANCE == null) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && !BeautyCamera.sUseCamera1Only) {
                        Timber.d("use Camera2Engine");
                        INSTANCE = new Camera2Engine();
                        ((Camera2Engine) INSTANCE).initCameraManager(BeautyCamera.sContext);
                    } else {
                        Timber.d("use Camera1Engine");
                        INSTANCE = new Camera1Engine();
                    }
                }
            }
        }
        return INSTANCE;
    }

    private static volatile CameraEngine INSTANCE;
}
