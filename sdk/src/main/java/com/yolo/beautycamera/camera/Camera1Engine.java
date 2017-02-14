package com.yolo.beautycamera.camera;

import android.app.Activity;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.view.Surface;

import com.yolo.beautycamera.util.Const;

import java.io.IOException;

import timber.log.Timber;

/**
 * Created by shuailongcheng on 16/11/2016.
 * package access
 */
class Camera1Engine extends CameraEngine {
    Camera mCamera1;
    private SurfaceTexture mSurfaceTexture;
    private OnPreviewListener mOnPreviewListener;

    private boolean mPortrait;

    @Override
    public void startPreview(SurfaceTexture surfaceTexture, OnPreviewListener listener, boolean portrait) {
        Timber.d("Camera1 startPreview");
        mPortrait = portrait;

        if (surfaceTexture == null) {
            throw new IllegalArgumentException("startPreview with null surfaceTexture");
        }

        synchronized (this) {
            mSurfaceTexture = surfaceTexture;
            mOnPreviewListener = listener;

            try {
                openCameraIfNeeded();
            } catch (Exception ignore) {
                listener.onError(ERROR_CAMERA_ACCESS_FAIL);
                return;
            }

            if (mCamera1 == null) {
                listener.onError(ERROR_CAMERA_ACCESS_FAIL);
                return;
            }

            mFlashOn = false;

//        camera1：
//        前置：adjustSize: rotation:270, flipHorizontal:true, flipVertical:false 或者  adjustSize: rotation:90, flipHorizontal:false, flipVertical:true,
//        后置：adjustSize: rotation:90, flipHorizontal:false, flipVertical:true, 或者  never mind
            try {
                int orientation;
                if (mPortrait) {
                    orientation = getOrientation();
                } else {
                    orientation = 180;
                }
                Camera.Size previewSize = mCamera1.getParameters().getPreviewSize();
                Point naturalWH = Const.getNaturalWH(orientation, previewSize.width, previewSize.height);
                Timber.d("adjustSize, in Camera1Engine, orientation:%d, naturalWH point:%s", orientation, naturalWH);
                listener.onPreviewInfo(naturalWH.x, naturalWH.y, true, mFrontCamera);
            } catch (Exception ex) {
                listener.onError(ERROR_CAMERA_ACCESS_FAIL);
                Timber.e(" mCamera1.getParameters().getPreviewSize() fail: %s", ex);
            }

            try {
                mCamera1.setPreviewTexture(surfaceTexture);
            } catch (IOException e) {
                e.printStackTrace();
                listener.onError(ERROR_IOEXCEPTION);
                return;
            }
            mCamera1.startPreview();
        }
    }

    private void openCameraIfNeeded() {
        if (mCamera1 == null) {
            mCamera1 = Camera.open(getCurrentCameraId());
            setDefaultParameters(mCamera1);
        }
    }

//    @Override
//    public void pausePreview() {
//        if (mCamera1 != null) {
//            mCamera1.stopPreview();
//        }
//    }
//
//    @Override
//    public void resumePreview() {
//        if (mCamera1 != null) {
//            mCamera1.startPreview();
//        }
//    }

    /**
     * 这个可能在surfview 的线程调用，也可能在 activity的生命周期中（主线程）调用
     */
    @Override
    public void releaseCamera() {
        synchronized (this) {
            if (mCamera1 != null) {
                mCamera1.setPreviewCallback(null);
                mCamera1.stopPreview();
                mCamera1.release();
                mCamera1 = null;
            }
            mSurfaceTexture = null;
            mOnPreviewListener = null;
            mFlashOn = false;
        }
    }

    @Override
    public void switchCamera() {
        SurfaceTexture surfacseTexture = mSurfaceTexture;
        OnPreviewListener onPreviewListener = mOnPreviewListener;
        releaseCamera();

        mFrontCamera = !mFrontCamera;
        if (mFrontCamera) {
            // 切刀前置摄像头，闪光灯会自动关闭
            mFlashOn = false;
        }

        startPreview(surfacseTexture, onPreviewListener, mPortrait);

    }

    @Override
    synchronized public void switchFlash() {
        if (mFrontCamera) {
            // 前置摄像头没闪光灯
            return;
        }
        Camera.Parameters parameters = mCamera1.getParameters();
        parameters.setFlashMode(mFlashOn ? Camera.Parameters.FLASH_MODE_OFF : Camera.Parameters.FLASH_MODE_TORCH);
        mCamera1.setParameters(parameters);
        mFlashOn = !mFlashOn;
    }

    @Override
    public int getOrientation() {
        Camera.CameraInfo ci = new Camera.CameraInfo();
        Camera.getCameraInfo(getCurrentCameraId(), ci);
        return ci.orientation;
    }

    private int getCurrentCameraId() {
        return mFrontCamera ? Camera.CameraInfo.CAMERA_FACING_FRONT : Camera.CameraInfo.CAMERA_FACING_BACK;
    }

    /**
     * 设置默认的宽高、以及rotation(90度）
     *
     * @param c
     */
    private void setDefaultParameters(Camera c) {
        Camera.Parameters parameters = this.mCamera1.getParameters();
        if (parameters.getSupportedFocusModes().contains(
                Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
        }
        parameters.setPreviewSize(Const.DEFAULT_CAMERA_PREVIEW_WIDTH, Const.DEFAULT_CAMERA_PREVIEW_HEIGHT);

        if (mPortrait) {
            // do nothing
        } else {
            c.setDisplayOrientation(180);
        }

        int orientation = getOrientation();
        Timber.d("setDefaultParameters, orientation:%d", orientation);
//        setCameraDisplayOrientation(forTestActivity, getCurrentCameraId(), c);

        c.setParameters(parameters);
    }

    public static void setCameraDisplayOrientation(Activity activity,
                                                   int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info =
                new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = activity.getWindowManager().getDefaultDisplay()
                .getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }

        Timber.d("setCameraDisplayOrientation, degree:%d", result);
        camera.setDisplayOrientation(result);
    }
}
