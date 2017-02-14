package com.yolo.beautycamera.camera;

import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Point;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RequiresApi;
import android.util.Size;
import android.view.Surface;

import com.yolo.beautycamera.util.Const;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import timber.log.Timber;

/**
 * Created by shuailongcheng on 16/11/2016.
 * package access
 * <p>
 * camera2 api start preview steps:
 * Step 1: Create GLSurfaceView for camera2 required EGL environment
 * Step 2: Create Surface from SurfaceTexture
 * Step 3: Request CameraManager service, find camera ID (1st only) and open camera device
 * Step 4: Create capture request builder for preview. Add Surface to target.
 * Step 5: Create capture session for preview
 * Step 6: Send preview request to start preview
 * Step 7: onFrameAvailable() inform texture update of preview
 * Step 8: SurfaceTexture update texture image in context of GLSurfaceView.Renderer.onDrawFrame()
 * Step 9: Fragment shader draw preview texture (samplerExternalOES)
 */

@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
class Camera2Engine extends CameraEngine {

    private String mFrontCameraId;
    private String mBackCameraId;

    private SurfaceTexture mSurfaceTexture;
    private Surface mSurface;
    private OnPreviewListener mOnPreviewListener;

    private int mCameraPreviewWidth;
    private int mCameraPreviewHeight;

    private CameraManager mCameraManager;
    private CameraDevice mCameraDevice;
    private CameraCaptureSession mCaptureSession;
    private ImageReader mImageReader;

    private boolean mPortrait;

    public void initCameraManager(Context context) {
        if (mCameraManager == null) {
            mCameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        }
    }


    private HandlerThread mCameraThread;
    private Handler mCamera2Handler;

    // TODO: 21/11/2016 这里应该加上两个参数，desireWidth和desireHeight ，camera1和camera2根据自己的api来判断能开多少分辨率
    @Override
    public void startPreview(SurfaceTexture surfaceTexture, OnPreviewListener listener, boolean portrait) {
        Timber.d("Camera2 startPreview");
        mPortrait = portrait;
        if (surfaceTexture == null) {
            throw new IllegalArgumentException("startPreview with null surfaceTexture");
        }

        try {
            initCameraIdIfNeeded();
        } catch (CameraAccessException e) {
            e.printStackTrace();
            notifyListenerErrorCode(ERROR_CAMERA_ACCESS_FAIL);
            return;
        }

        mFlashOn = false;

        mSurfaceTexture = surfaceTexture;

        // FIXME: 21/11/2016 add findOptSize，对camera2来说，这里就是确定宽高的时候
        // FIXME 而且必须在newSurface之前setDefaultBufferSize
        if (mPortrait || true) {
            mCameraPreviewWidth = Const.DEFAULT_CAMERA_PREVIEW_WIDTH;
            mCameraPreviewHeight = Const.DEFAULT_CAMERA_PREVIEW_HEIGHT;
        } else {
            mCameraPreviewWidth = Const.DEFAULT_CAMERA_PREVIEW_HEIGHT;
            mCameraPreviewHeight = Const.DEFAULT_CAMERA_PREVIEW_WIDTH;
        }

        // 这里如果写成 setDefaultBufferSize(480, 640),相机就不会选一个640*460的，而是选一个960x640之类的
        mSurfaceTexture.setDefaultBufferSize(mCameraPreviewWidth, mCameraPreviewHeight);

        mSurface = new Surface(surfaceTexture);
        mOnPreviewListener = listener;

        openCameraStartPreview();
    }


    private boolean initImageReader() {
        Size size;
        try {
            size = findOptSize(getCurrentCameraId());
        } catch (CameraAccessException e) {
            e.printStackTrace();
            notifyListenerErrorCode(ERROR_CAMERA_ACCESS_FAIL);
            return false;
        }
        if (size == null) {
            notifyListenerErrorCode(ERROR_CAMERA_ACCESS_FAIL);
            return false;
        }
        Timber.d("initImageReader, findOptSize size.width:%d, size:height:%d", size.getWidth(), size.getHeight());
        mImageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(),
                ImageFormat.YUV_420_888, 2);
        mImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
            boolean logged = false;

            @Override
            public void onImageAvailable(ImageReader reader) {
                Timber.d("initImageReader, mImageReader->onImageAvailable, reader.width:%d, reader:height:%d", reader.getWidth(), reader.getHeight());
                logged = true;
                Image image = reader.acquireLatestImage();
                image.close();
            }
        }, mCamera2Handler);

        return true;
    }

//    @Override
//    public void pausePreview() {
//        Timber.d("pausePreview");
//        SurfaceTexture surfacseTexture = mSurfaceTexture;
//        OnPreviewListener onPreviewListener = mOnPreviewListener;
//
//        releaseCamera();
//
//        initParams(surfacseTexture, onPreviewListener);
//    }
//
//    @Override
//    public void resumePreview() {
//        Timber.d("resumePreview");
//        openCameraStartPreview();
//    }

    @Override
    public void releaseCamera() {
        if (null != mCaptureSession) {
            mCaptureSession.close();
            mCaptureSession = null;
        }
        if (null != mCameraDevice) {
            mCameraDevice.close();
            mCameraDevice = null;
        }

        if (null != mCameraThread) {
            mCameraThread.quitSafely();
            try {
                mCameraThread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                Timber.e("releaseCamera, mCameraThread.join fail");
            } finally {
                mCamera2Handler = null;
            }
        }

        if (mImageReader != null) {
            mImageReader.setOnImageAvailableListener(null, null);
        }
        mSurfaceTexture = null;
        mSurface = null;
        mOnPreviewListener = null;
        mFlashOn = false;
        Timber.d("releaseCamera");
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
    public void switchFlash() {
        CameraCharacteristics characteristics =
                null;
        try {
            characteristics = mCameraManager.getCameraCharacteristics(mBackCameraId);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            notifyListenerErrorCode(ERROR_CAMERA_ACCESS_FAIL);
        }
        Boolean available = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
        boolean flashSupported = available == null ? false : available;
        if (!flashSupported) {
            return;
        }

        if (!mFrontCamera && turnOnFlash(!mFlashOn)) {
            mFlashOn = !mFlashOn;
        }
    }

    /**
     * 在打开摄像头之后才能调用这个，内部方法
     */
    private void createCameraPreviewSession() {
        try {
            final CaptureRequest.Builder captureRequestBuilder =
                    mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            captureRequestBuilder.addTarget(mSurface);
//            captureRequestBuilder.addTarget(mImageReader.getSurface());

            mCameraDevice.createCaptureSession(Arrays.asList(mSurface/*, mImageReader.getSurface()*/),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                            try {
                                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE,
                                        CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);

//                                captureRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
//                                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);

                                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(),
                                        null, mCamera2Handler);

                                mCaptureSession = cameraCaptureSession;

                                int orientation = getOrientation();
                                Timber.d("orientation= %d", orientation);
                                if (mOnPreviewListener != null) {
//                                    mOnPreviewListener.onPreviewInfo(Const.getNaturalViewWidth(orientation), Const.getNaturalViewHeight(orientation),
//                                            0, false, true);

//                                    camera2：
//                                    前置： adjustSize: rotation:0, flipHorizontal:false, flipVertical:true
//                                    后置：adjustSize: rotation:180, flipHorizontal:true, flipVertical:false,
                                    Point naturalWH = Const.getNaturalWH(orientation, mCameraPreviewWidth, mCameraPreviewHeight);

                                    Timber.d("adjustSize, in Camera2Engine, orientation:%d, naturalWH point:%s", orientation, naturalWH);
                                    mOnPreviewListener.onPreviewInfo(naturalWH.x, naturalWH.y, false, mFrontCamera);
                                }

                            } catch (CameraAccessException | SecurityException e) {
                                notifyListenerErrorCode(ERROR_CAMERA_ACCESS_FAIL);
                            } catch (IllegalStateException e) {
                                notifyListenerErrorCode(ERROR_CAMERA2_ILLEGAL_STATE);
                            }
                        }

                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                            notifyListenerErrorCode(ERROR_CAMERA2_CONFIGURE_FAIL);
                        }
                    }, mCamera2Handler);
        } catch (CameraAccessException | SecurityException e) {
            notifyListenerErrorCode(ERROR_CAMERA_ACCESS_FAIL);
        } catch (IllegalStateException e) {
            notifyListenerErrorCode(ERROR_CAMERA2_ILLEGAL_STATE);
        }
    }

    private void openCameraStartPreview() {
        synchronized (this) {
            if (!mInited) {
                return;
            }
        }

        mCameraThread = new HandlerThread("Camera2EngineThread");
        mCameraThread.start();
        mCamera2Handler = new Handler(mCameraThread.getLooper());

        if (!initImageReader()) {
            return;
        }

        CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                try {
                    mCameraDevice = camera;
                    createCameraPreviewSession();
                } catch (IllegalStateException e) {
                    notifyListenerErrorCode(ERROR_CAMERA2_ILLEGAL_STATE);
                }
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                camera.close();
                mCameraDevice = null;
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                camera.close();
                mCameraDevice = null;
            }
        };

        try {
            // TODO: 16/11/2016 在外部调用方来负责相机权限的询问
            //noinspection MissingPermission
            mCameraManager.openCamera(getCurrentCameraId(), stateCallback, mCamera2Handler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            notifyListenerErrorCode(ERROR_CAMERA_ACCESS_FAIL);
        }
    }

    private boolean turnOnFlash(boolean on) {
        if (mCameraDevice == null || mCaptureSession == null) {
            Timber.e("turnOnFlash : mCameraDevice == null || cameraCaptureSession == null");
            return false;
        }

        final CaptureRequest.Builder requestBuilder;
        try {
            requestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            requestBuilder.set(CaptureRequest.FLASH_MODE,
                    on ? CameraMetadata.FLASH_MODE_TORCH : CameraMetadata.FLASH_MODE_OFF);

            requestBuilder.addTarget(mSurface);

            mCaptureSession.setRepeatingRequest(requestBuilder.build(), null, mCamera2Handler);
        } catch (CameraAccessException e) {
            notifyListenerErrorCode(ERROR_CAMERA_ACCESS_FAIL);
            return false;
        }
        return true;
    }

    @Override
    public int getOrientation() {
        CameraCharacteristics characteristics;
        Integer orientation = null;
        try {
            characteristics = mCameraManager.getCameraCharacteristics(getCurrentCameraId());
            orientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        } catch (CameraAccessException | SecurityException e) {
            notifyListenerErrorCode(ERROR_CAMERA_ACCESS_FAIL);
        }
        return orientation == null ? 0 : orientation;
    }

    final int getRotation() {
        int activityDegree = 0;

        int sensorDegree = getOrientation();
        int degree;
        if (mFrontCamera) {
            degree = (sensorDegree + activityDegree) % 360;
        } else {
            degree = (sensorDegree - activityDegree + 360) % 360;
        }
        return degree;
    }

    private String getCurrentCameraId() {
        return mFrontCamera ? mFrontCameraId : mBackCameraId;
    }

    private boolean mInited;

    /**
     * 这个不能用RAII的方式做，这样用户才能多次重试
     *
     * @throws CameraAccessException
     */
    synchronized private void initCameraIdIfNeeded() throws CameraAccessException {
        if (mInited) {
            return;
        }
        for (String cameraId : mCameraManager.getCameraIdList()) {
            CameraCharacteristics characteristics =
                    mCameraManager.getCameraCharacteristics(cameraId);

            Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null) {
                if (facing == CameraCharacteristics.LENS_FACING_FRONT) {
                    mFrontCameraId = cameraId;
                } else if (facing == CameraCharacteristics.LENS_FACING_BACK) {
                    mBackCameraId = cameraId;
                }
            }
        }

        Timber.d("initCameraIdIfNeeded: mFrontCameraId:%s, mBackCameraId:%s", mFrontCameraId, mBackCameraId);
        mInited = true;
    }

    private void notifyListenerErrorCode(@ErrorCode int errCode) {
        if (null != mOnPreviewListener) {
            mOnPreviewListener.onError(errCode);
        }
    }

    private Size findOptSize(String cameraId) throws CameraAccessException {
        Size[] supporttedSizes = CameraUtil.getSupporttedSizes(mCameraManager, cameraId, SurfaceTexture.class);
        Size optSize = CameraUtil.getPreferredPreviewSize(supporttedSizes, Const.DEFAULT_CAMERA_PREVIEW_WIDTH, Const.DEFAULT_CAMERA_PREVIEW_HEIGHT);
        return optSize == null ? new Size(Const.DEFAULT_CAMERA_PREVIEW_WIDTH, Const.DEFAULT_CAMERA_PREVIEW_HEIGHT) : optSize;
    }

    @Nullable
    public static Size getPreferredPreviewSize(Size[] mapSizes, int width, int height) {
        if (mapSizes == null || mapSizes.length == 0) {
            return null;
        }
        List<Size> collectorSizes = new ArrayList<>();
        for (Size option : mapSizes) {
            if (width > height) {
                if (option.getWidth() >= width &&
                        option.getHeight() >= height) {
                    collectorSizes.add(option);
                }
            } else {
                if (option.getWidth() >= height &&
                        option.getHeight() >= width) {
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
}
