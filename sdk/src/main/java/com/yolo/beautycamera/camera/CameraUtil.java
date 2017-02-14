package com.yolo.beautycamera.camera;

import android.content.Context;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.opengl.GLES20;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Size;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import timber.log.Timber;

/**
 * Created by shuailongcheng on 08/11/2016.
 */

public class CameraUtil {
    /**
     * @param context
     * @param surfaceClass 一般用SurfaceTexture
     * @param front
     * @return
     */
    public static Size[] getSupporttedSizes(Context context, Class surfaceClass, boolean front) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        return getSupporttedSizes(cameraManager, surfaceClass, front);
    }

    public static Size[] getSupporttedSizes(CameraManager cameraManager, Class surfaceClass, boolean front) {
        try {
            for (String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if (front != (CameraCharacteristics.LENS_FACING_FRONT == cameraCharacteristics.get(CameraCharacteristics.LENS_FACING))) {
                    continue;
                }
                StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                return map.getOutputSizes(surfaceClass);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.e("CameraUtil", "CameraAccessException:" + e);
        }
        return null;
    }

    public static Size[] getSupporttedSizes(Context context, String cameraId, Class surfaceClass) {
        CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        return getSupporttedSizes(cameraManager, cameraId, surfaceClass);
    }

    public static Size[] getSupporttedSizes( CameraManager cameraManager, String cameraId, Class surfaceClass) {
        try {
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            return map == null ? null : map.getOutputSizes(surfaceClass);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            Log.e("CameraUtil", "CameraAccessException:" + e);
        }
        return null;
    }

    @Nullable
    public static Size getPreferredPreviewSize(Size[] mapSizes, int width, int height) {
        List<Size> collectorSizes = new ArrayList<>();
        for (Size option : mapSizes) {
            if (width > height) {
                if (option.getWidth() > width &&
                        option.getHeight() > height) {
                    collectorSizes.add(option);
                }
            } else {
                if (option.getWidth() > height &&
                        option.getHeight() > width) {
                    collectorSizes.add(option);
                }
            }
        }
        if (collectorSizes.size() > 0) {
            return Collections.min(collectorSizes, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        } else {
            return null;
        }
    }

    public static Camera openCameraGingerbread(boolean front) {
        int cameraCount = 0;
        Camera cam = null;
        Camera.CameraInfo cameraInfo = new Camera.CameraInfo();
        cameraCount = Camera.getNumberOfCameras();
        for (int camIdx = 0; camIdx < cameraCount; camIdx++) {
            Camera.getCameraInfo(camIdx, cameraInfo);
            if (front && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                try {
                    cam = Camera.open(camIdx);
                    break;
                } catch (RuntimeException e) {
                    Timber.e("Camera failed to open: " + e.getLocalizedMessage());
                }
            } else if (!front && cameraInfo.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
                try {
                    cam = Camera.open(camIdx);
                    break;
                } catch (RuntimeException e) {
                    Timber.e("Camera failed to open: " + e.getLocalizedMessage());
                }
            }
        }

        return cam;
    }

    public static void dumpGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Timber.d("CameraUtil, dumpGlError:%s -> code:%x", op, error);
        }
    }


}
