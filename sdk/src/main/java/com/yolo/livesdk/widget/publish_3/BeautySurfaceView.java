package com.yolo.livesdk.widget.publish_3;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.widget.ImageView;

import com.utils.ScreenUtils;
import com.yolo.beautycamera.beauty_preview.gles.EglCore;
import com.yolo.beautycamera.beauty_preview.gles.WindowSurface;
import com.yolo.beautycamera.beauty_preview.utils.MyUtil;
import com.yolo.beautycamera.beauty_preview.utils.OpenGlUtils;
import com.yolo.beautycamera.beauty_preview.utils.Rotation;
import com.yolo.beautycamera.beauty_preview.utils.TextureRotationUtil;
import com.yolo.beautycamera.camera.CameraEngine;
import com.yolo.livesdk.YoloLiveNative;
import com.yolo.livesdk.widget.publish_3.filter.SimpleBeautyFilter;
import com.yolo.livesdk.widget.publish_3.filter.SimpleCameraInput;
import com.yolo.livesdk.widget.publish_controller.BeautyCameraPreviewOutput;
import com.yolo.livesdk.widget.publish_controller.BeautyPublishController;
import com.yolo.livesdk.widget.publish_controller.BeautyPublishControllerImpl;
import com.yolo.livesdk.widget.publish_controller.PublishTimeStamp;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnull;

import jp.co.cyberagent.android.gpuimage.GPUImageFilter;
import timber.log.Timber;

import static com.yolo.beautycamera.beauty_preview.utils.MyUtil.addDistance;
import static com.yolo.beautycamera.beauty_preview.utils.MyUtil.floor16BytesAlign;

/**
 * Created by shuailongcheng on 10/12/2016.
 * 必须一开始是设置成全屏的
 */
public class BeautySurfaceView extends SurfaceView implements BeautyPublishController.BeautyPublishControllee {
    private static final boolean VERBOSE = true;
    private static final boolean VERBOSE_DEBUG = false;
    public static final boolean USE_FPS_RECORDER_DEBUG = true;

    public static boolean PUSH_ON_START = true;
    private volatile boolean mDump2Image = false;
    private boolean mRenderResumed = true;
    private Object mRenderResumeLock = new Object();


    public static final int USE_FILTER_BEAUTY = 0;
    public static final int USE_FILTER_FILTER1 = 1;
    public static final int USE_FILTER_SMOOTH = 2;
    public static final int USE_FILTER_FILTER2 = 3;
    public static final int USE_FILTER_FILTER_NONE = 4;
    public static volatile int USE_FILTER_TYPE = USE_FILTER_SMOOTH;

    private volatile boolean mUseBeauty;
    private boolean mMirror;
    private boolean mPotrait;


    private volatile BeautyCameraPreviewOutput mBeautyCameraPreviewOutput;

    @Override
    public void initPrefs(boolean initFrontCamera, boolean initUseBeauty, boolean initMirror, boolean portrait) {
        CameraEngine.getInstance().initUseFrontCamera(initFrontCamera);
        mUseBeauty = initUseBeauty;
        mMirror = initMirror;
        mBeautyPublishOutput.setFrontCamera(initFrontCamera);
        mBeautyPublishOutput.setMirror(mMirror);
        mPotrait = portrait;
    }

    @Override
    public void startPreviewOutput(@Nonnull BeautyCameraPreviewOutput previewOutput) {
        mBeautyCameraPreviewOutput = previewOutput;
        mBeautyPublishOutput.setOutputListener(previewOutput);

        if (mOutputWidth != 0) {
            mBeautyCameraPreviewOutput.onPreviewOpened(mOutputWidth, mOutputHeight);
        }
    }

    @Override
    public void resetPreviewOutputListener() {
        mBeautyCameraPreviewOutput = null;
        mBeautyPublishOutput.setOutputListener(null);
    }

    @Override
    public void destroy() {
        resetPreviewOutputListener();
        msgDestroy();
    }

    @Override
    public void switchMirror() {
        mMirror = !mMirror;
        mBeautyPublishOutput.setMirror(mMirror);
    }

    @Override
    public boolean isMirror() {
        return mMirror;
    }

    @Override
    public void resumeRender() {
        if (mRenderResumed) {
            return;
        }

        synchronized (mRenderResumeLock) {
            mRenderResumed = true;
        }

        msgResumeRender();
    }

    @Override
    public void pauseRender(boolean clear) {
        if (!mRenderResumed) {
            return;
        }

        synchronized (mRenderResumeLock) {
            mRenderResumed = false;
        }

        msgPauseRender(clear);
    }


    @Override
    public void switchBeautyOn() {
        mUseBeauty = !mUseBeauty;
    }

    @Override
    public boolean isBeautyOn() {
        return mUseBeauty;
    }

    @Override
    public void postUIThread(Runnable runnable) {
        post(runnable);
    }


    private boolean mCameraValid;

    @Override
    public void switchCamera() {
        mCameraValid = false;
        CameraEngine.getInstance().switchCamera();
    }


    @Override
    public void useFallbackFpsStrategy() {
        mBeautyPublishOutput.useFallbackFpsController(true);
    }


    BeautyPublishController mController = new BeautyPublishControllerImpl(this);

    public BeautyPublishController getController() {
        return mController;
    }

    private volatile TestCallback mTestCallback;
    private FpsRecorder mFpsRecorder = new FpsRecorder();

    public interface TestCallback {

        ImageView getDumpImageView();

        void printPushFps(int fps);

        void setShowFps(int fps);
    }

    public void setTestCallback(TestCallback cb) {
        mTestCallback = cb;
    }


    private Object mReadyFence = new Object();      // guards ready/running
    private boolean mReady;
    private boolean mRunning;

    RenderHandler mHandler;

    private volatile boolean mSurfaceReady = false;

    private static final int MSG_START_RENDER = 0;
    private static final int MSG_STOP_RENDER = 1;

    private static final int MSG_FRAME_AVAILABLE = 2;
    private static final int MSG_CAMERA_VALID = 3;

    private static final int MSG_QUIT = 4;
    private static final int MSG_SURFACE_SIZE_CHANGED = 5;
    // 清屏，置为黑色
    private static final int MSG_PAUSE_RENDER = 6;
    private static final int MSG_RESUME_RENDER = 7;


    private EglCore mEglCore;
    private WindowSurface mDisplaySurface;
    private int mInputTextureId;
    private int mFrameNum = -1;

    private int mSurfaceWidth, mSurfaceHeight;

    private SimpleCameraInput mSimpleCameraInput;
    private GPUImageFilter mBeautyFilter;

    public volatile FloatBuffer gLCubeBuffer;
    public volatile FloatBuffer gLTextureBuffer;

    private boolean mIsCamera1;
    private boolean mIsFront;
    private int mCameraPreviewNaturalWidth;
    private int mCameraPreviewNaturalHeight;

    public static final boolean USE_OUTPUT_MANAGER = true;
    public BeautyPublishOutput mBeautyPublishOutput;
    /**
     * sufrace的fixedSize，会随着layout size的变化而变化,遵循两点
     * 1.至少有一个值等于cameraSize（center_crop，也就是从相机的画面中截取)
     * 2.比例永远等于当前的view layout size
     * ps:不用遵循16 bytes_alignment
     */
    private int mFixedWidth, mFixedHeight;

    /**
     * 会随着fixedSize的变化而变化，但是会遵守几点。
     * 1.至少一个值等于mFixedSize对应的值（center_crop, 从sufraceView显示的界面中截取)
     * 2.比例永远等于mFullScreenWHRatio
     * 3.都是16 byte alignment的
     */
    private int mOutputWidth, mOutputHeight;
    private int mReadPixelStartX, mReadPixelStartY;

    private float mFullScreenWHRatio = 0;

    public BeautySurfaceView(Context context) {
        super(context);
        init();
    }

    public BeautySurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BeautySurfaceView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    /**
     * may be called from any thread
     */
    public void onCameraOpened(boolean isCamera1, boolean isFront, int previewNaturalWidth, int previewNaturalHeight) {
        mIsCamera1 = isCamera1;
        mIsFront = isFront;
        mCameraPreviewNaturalWidth = previewNaturalWidth;
        mCameraPreviewNaturalHeight = previewNaturalHeight;

        mBeautyPublishOutput.setFrontCamera(mIsFront);
        onSizeInfoChanged();
        mHandler.sendMessage(mHandler.obtainMessage(MSG_CAMERA_VALID));
    }

    private boolean mFixedSizeInited = false;

    public static AtomicInteger test_extra_fixed_width = new AtomicInteger(0);

    private void onSizeInfoChanged() {
        if (mCameraPreviewNaturalWidth == 0 || getWidth() == 0) {
            Timber.w("onSizeInfoChanged , size info is not complete, mCameraPreviewNaturalWidth:%d, getWidth:%d", mCameraPreviewNaturalWidth, getWidth());
            return;
        }

        // 现在只初始化一次fixedSize
        if (mFixedSizeInited) {
            return;
        }
        mFixedSizeInited = true;

        // to calculate mFixedSize
        float cameraRatio = mCameraPreviewNaturalWidth * 1.0f / mCameraPreviewNaturalHeight;

        int newFixedWidth, newFixedHeight;
        boolean fixedSizeChanged = false;
        if (mFullScreenWHRatio > cameraRatio) {
            // 要截断camera preview的高
            newFixedWidth = mCameraPreviewNaturalWidth;
            newFixedHeight = (int) (newFixedWidth / mFullScreenWHRatio);
        } else {
            // 截断preview的宽
            newFixedHeight = mCameraPreviewNaturalHeight;
            newFixedWidth = (int) (newFixedHeight * mFullScreenWHRatio);
        }
        // 如果 newFixedWidth 算出来是365之类的，就直接提升到368
        newFixedWidth = MyUtil.round16IfCan(newFixedWidth);
        newFixedHeight = MyUtil.round16IfCan(newFixedHeight);

        if (mFixedWidth != newFixedWidth || mFixedHeight != newFixedHeight) {
            fixedSizeChanged = true;
            mFixedWidth = newFixedWidth + test_extra_fixed_width.get();
            mFixedHeight = newFixedHeight;
        }

        mOutputWidth = floor16BytesAlign(mFixedWidth);
        mOutputHeight = floor16BytesAlign(mFixedHeight);
        mReadPixelStartX = (mFixedWidth - mOutputWidth) / 2;
        mReadPixelStartY = (mFixedHeight - mOutputHeight) / 2;
        BeautyCameraPreviewOutput output = mBeautyCameraPreviewOutput;
        if (output != null) {
            output.onPreviewOpened(mOutputWidth, mOutputHeight);
        }

        Timber.d("onSizeInfoChanged, test_extra_fixed_width:%d", test_extra_fixed_width.get());
        Timber.d("onSizeInfoChanged, mFixedWidth:%d, mFixedHeight:%d, mOutputWidth:%d, mOutputHeight:%d",
                mFixedWidth, mFixedHeight, mOutputWidth, mOutputHeight);

        if (fixedSizeChanged) {
            post(() -> getHolder().setFixedSize(mFixedWidth, mFixedHeight));
        }

        Timber.d("adjustToCameraInfo, mIsCamera1:%b", mIsCamera1);
        if (mPotrait) {
            if (mIsCamera1) {
                adjustImageScaling(270, true, false, gLTextureBuffer, gLCubeBuffer);
            } else {
                adjustImageScaling(180, true, false, gLTextureBuffer, gLCubeBuffer);
            }
        } else {
            if (mIsCamera1) {
                adjustImageScaling(0, true, false, gLTextureBuffer, gLCubeBuffer);
            } else {
//                adjustImageScaling(90, true, false, gLTextureBuffer, gLCubeBuffer);
            }
        }
    }

    private int debugCurRotation;
    boolean debugFlipHor, debugFlipVer;

    public void debugFlipHor() {
        debugFlipHor = !debugFlipHor;
        adjustImageScaling(debugCurRotation, debugFlipHor, debugFlipVer, gLTextureBuffer, gLCubeBuffer);
    }

    public void debugFlipVer() {
        debugFlipVer = !debugFlipVer;
        adjustImageScaling(debugCurRotation, debugFlipHor, debugFlipVer, gLTextureBuffer, gLCubeBuffer);
    }

    public void debugAddRotation() {
        debugCurRotation = (debugCurRotation + 90) % 360;
        adjustImageScaling(debugCurRotation, debugFlipHor, debugFlipVer, gLTextureBuffer, gLCubeBuffer);
    }

    private void adjustImageScaling(int rotation, boolean flipHorizontal, boolean flipVertical, FloatBuffer texBuffer, FloatBuffer cubeBuffer) {
        debugCurRotation = rotation;
        debugFlipHor = flipHorizontal;
        debugFlipVer = flipVertical;
        Timber.d("adjustImageScaling, rotation:%d, flipHorizontal:%b, flipVertical:%b", rotation, flipHorizontal, flipVertical);


        float[] textureCords = TextureRotationUtil.getRotation(Rotation.fromInt(rotation),
                flipHorizontal, flipVertical);

        // make textureBuffer to CENTER_CROP
        float ratioWidth = mCameraPreviewNaturalWidth * 1.0f / mFixedWidth;
        float ratioHeight = mCameraPreviewNaturalHeight * 1.0f / mFixedHeight;
        float distHorizontal = (1 - 1 / ratioWidth) / 2;
        float distVertical = (1 - 1 / ratioHeight) / 2;
        if (rotation == 90 || rotation == 270) {
            textureCords = new float[]{
                    addDistance(textureCords[0], distVertical), addDistance(textureCords[1], distHorizontal),
                    addDistance(textureCords[2], distVertical), addDistance(textureCords[3], distHorizontal),
                    addDistance(textureCords[4], distVertical), addDistance(textureCords[5], distHorizontal),
                    addDistance(textureCords[6], distVertical), addDistance(textureCords[7], distHorizontal),
            };
        } else {
            textureCords = new float[]{
                    addDistance(textureCords[0], distHorizontal), addDistance(textureCords[1], distVertical),
                    addDistance(textureCords[2], distHorizontal), addDistance(textureCords[3], distVertical),
                    addDistance(textureCords[4], distHorizontal), addDistance(textureCords[5], distVertical),
                    addDistance(textureCords[6], distHorizontal), addDistance(textureCords[7], distVertical),
            };
        }

        texBuffer.clear();
        texBuffer.put(textureCords).position(0);


        float[] cube = TextureRotationUtil.CUBE;
        cubeBuffer.clear();
        cubeBuffer.put(cube).position(0);
    }


    private void init() {
        startRenderThread();

        mBeautyPublishOutput = new BeautyPublishOutput();
        mBeautyPublishOutput.startThread();

        Timber.d("lifecycle init");

        int screenHeight = ScreenUtils.getScreenHeight();
        int screenWidth = ScreenUtils.getScreenWidth();
        mFullScreenWHRatio = screenWidth * 1.0f / (screenHeight - ScreenUtils.getStatusBarHeight());
        Timber.d("init mFullScreenWHRatio:%f, ScreenUtils.getStatusBarHeight():%d, screenWidth:%d, screenHeight:%d", mFullScreenWHRatio, ScreenUtils.getStatusBarHeight(), screenWidth, screenHeight);

        initAdjustBuffers();
//        getHolder().setFormat(PixelFormat.RGB_565);
        getHolder().setKeepScreenOn(true);

        getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Timber.d("lifecycle surfaceCreated, mCameraPreviewNaturalWidth:%d", mCameraPreviewNaturalWidth);
                mIsSurfaceValid = true;
                msgStartRender(new RenderConfig(holder.getSurface()));
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Timber.d("lifecycle surfaceChanged, format:%d, width:%d, height:%d", format, width, height);

                if (mSurfaceWidth == width && mFixedHeight == height) {
                    return;
                }

                mSurfaceWidth = width;
                mSurfaceHeight = height;

                if (mFixedWidth == mSurfaceWidth && mFixedHeight == mSurfaceHeight) {
                    // 有的手机，比如红米NOTE 1S，setFixedSize的时候，会调用两次surfaceChanged，一次传入旧值，一次传入新值
                    // 所以在这里做一次防御，多余的一次，就没必要msgSurfaceChanged了，因为每一次msgSurfaceChanged都会调用
                    // beautyFilter的onOutputSizeChanged，其中会重新创建Framebuffers
                    BeautySurfaceView.this.msgSurfaceChanged();
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Timber.d("lifecycle surfaceDestroyed");
                mIsSurfaceValid = false;
                mSurfaceWidth = 0; // 表示界面销毁过（一个activity生命周期可能会有好几个surface的生命周期）这个保证了从别的界面跳转回来的时候，surfaceChanged会得到重新执行
                msgStopRender();
            }
        });
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        Timber.d("onSizeChanged, w:%d, h:%d, oldw:%d, oldh:%d", w, h, oldw, oldh);
        onSizeInfoChanged();
    }

    private void initAdjustBuffers() {
        gLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLCubeBuffer.put(TextureRotationUtil.CUBE).position(0);

        gLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);
    }

    private final SurfaceTexture.OnFrameAvailableListener mOnFrameAvailableListener = surfaceTexture -> {
        if (!mRenderResumed) {
            return;
        }

        synchronized (mRenderResumeLock) {
            if (mRenderResumed) {
                msgFrameAvailable(surfaceTexture);
            }
        }
    };

    private final CameraEngine.OnPreviewListener mOnPreviewListener = new CameraEngine.OnPreviewListener() {
        @Override
        public void onPreviewInfo(int previewWidth, int previewHeight, boolean isCamera1, boolean isFront) {
            Timber.d("onPreviewInfo, previewWidth:%d, previewHeight:%d, isCamera1:%b, isFront:%b", previewWidth, previewHeight, isCamera1, isFront);
            onCameraOpened(isCamera1, isFront, previewWidth, previewHeight);
        }

        @Override
        public void onError(@CameraEngine.ErrorCode int error) {
            Timber.e("CameraEngine onError:%d", error);

            BeautyCameraPreviewOutput previewOutput = mBeautyCameraPreviewOutput;
            if (previewOutput == null) {
                return;
            }
            switch (error) {
                case CameraEngine.ERROR_IOEXCEPTION:
                case CameraEngine.ERROR_CAMERA2_ILLEGAL_STATE:
                case CameraEngine.ERROR_CAMERA2_CONFIGURE_FAIL:
                    previewOutput.onError(BeautyCameraPreviewOutput.START_CAMERA_FAIL);
                    break;
                case CameraEngine.ERROR_CAMERA_ACCESS_FAIL:
                    previewOutput.onError(BeautyCameraPreviewOutput.ACCESS_CAMERA_FAIL);
                    break;
            }
        }
    };

    private int mCameraTextureId;
    private volatile SurfaceTexture mCameraTexture;  // receives the output from the camera preview

    private void startCamera() {
        mCameraTextureId = OpenGlUtils.getExternalOESTextureID();
        mCameraTexture = new SurfaceTexture(mCameraTextureId);
        mCameraTexture.setOnFrameAvailableListener(mOnFrameAvailableListener);
        mInputTextureId = mCameraTextureId;
        CameraEngine.getInstance().releaseCamera();
        CameraEngine.getInstance().startPreview(mCameraTexture, mOnPreviewListener, mPotrait);
    }

    private void msgStartRender(RenderConfig config) {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_START_RENDER, config));
    }

    public void msgDestroy() {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_QUIT));

        if (mBeautyPublishOutput != null) {
            mBeautyPublishOutput.quit();
        }
    }

    private void msgStopRender() {
        mHandler.removeMessages(MSG_FRAME_AVAILABLE);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_RENDER));
    }

    private void msgSurfaceChanged() {
        mHandler.removeMessages(MSG_FRAME_AVAILABLE);
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SURFACE_SIZE_CHANGED));
    }

    private void msgResumeRender() {
        synchronized (mReadyFence) {
            if (!mReady) {
                Timber.e("frameAvailable but not ready");
                return;
            }
        }

        Message message = mHandler.obtainMessage(MSG_RESUME_RENDER);
        mHandler.sendMessage(message);
    }

    private void msgPauseRender(boolean clear) {
        synchronized (mReadyFence) {
            if (!mReady) {
                Timber.e("frameAvailable but not ready");
                return;
            }
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_PAUSE_RENDER, clear));
    }

    private float[] transform = new float[16];

    private void msgFrameAvailable(SurfaceTexture st) {
        synchronized (mReadyFence) {
            if (!mReady) {
                Timber.e("frameAvailable but not ready");
                return;
            }
        }
        Timber.v("frameAvailable and is ready");

        st.getTransformMatrix(transform);

        long timestamp = st.getTimestamp();
//        if (timestamp == 0) {
//            // Seeing this after device is toggled off/on with power button.  The
//            // first frame back has a zero timestamp.
//            //
//            // MPEG4Writer thinks this is cause to abort() in native code, so it's very
//            // important that we just ignore the frame.
//            Timber.e("frameAvailable HEY: got SurfaceTexture with timestamp of zero");
//            return;
//        }

        Message message = mHandler.obtainMessage(MSG_FRAME_AVAILABLE, (int) (timestamp >> 32), (int) timestamp, transform);
        mHandler.sendMessage(message);
    }

    private void startRenderThread() {
        Timber.d("startRenderThread");
        synchronized (mReadyFence) {
            if (mRunning) {
                Timber.w("Encoder thread already running");
                return;
            }
            mRunning = true;

            new Thread("BeautyRender") {
                @Override
                public void run() {
                    Looper.prepare();

                    synchronized (mReadyFence) {
                        mHandler = new RenderHandler(BeautySurfaceView.this);
                        mReady = true;
                        mReadyFence.notify();
                    }

                    Timber.d("startRenderThread ready");

                    Looper.loop();

                    Timber.d("SurfaceRenderThread exiting");
                    synchronized (mReadyFence) {
                        mReady = mRunning = false;
                        mHandler = null;
                    }
                }
            }.start();

            while (!mReady) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }
    }

    private static class RenderHandler extends Handler {
        private WeakReference<BeautySurfaceView> mWeakRenderer;

        public RenderHandler(BeautySurfaceView renderer) {
            mWeakRenderer = new WeakReference<BeautySurfaceView>(renderer);
        }

        @Override
        public void handleMessage(Message msg) {
            Object obj = msg.obj;

            BeautySurfaceView outputer = mWeakRenderer.get();
            if (outputer == null) {
                Timber.d("mWeakRenderer.get() outputer = null  ");
                return;
            }

            switch (msg.what) {
                case MSG_START_RENDER:
                    outputer.handleStartRender((RenderConfig) obj);
                    break;
                case MSG_STOP_RENDER:
                    outputer.handleStopRender();
                    break;
                case MSG_FRAME_AVAILABLE:
                    long timestamp = (((long) msg.arg1) << 32) |
                            (((long) msg.arg2) & 0xffffffffL);
                    outputer.handleDrawFrame((float[]) obj, timestamp);
                    break;

                case MSG_CAMERA_VALID:
                    outputer.handleCameraValid();
                    break;

                case MSG_QUIT:
                    Looper.myLooper().quit();
                    break;

                case MSG_SURFACE_SIZE_CHANGED:
                    outputer.handleSurfaceChanged();
                    break;

                case MSG_PAUSE_RENDER:
                    outputer.handlePauseRender((boolean) obj);
                    break;

                case MSG_RESUME_RENDER:
                    outputer.handleResumeRender();
                    break;

                default:
                    throw new RuntimeException("Unknown message " + msg.what);
            }
        }
    }


    /**
     * 现在采用fixedSize不变的方案，暂时不用这个方案了
     */
//    private int mNeedDropCnt = 0;
    private void handleSurfaceChanged() {
        mSimpleCameraInput.initCameraFrameBuffer(mCameraPreviewNaturalWidth, mCameraPreviewNaturalHeight);
        mSimpleCameraInput.onOutputSizeChanged(mSurfaceWidth, mSurfaceHeight);
        mBeautyFilter.onOutputSizeChanged(mSurfaceWidth, mSurfaceHeight);
        GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);
        // 这个4是测出来的，临界值是4，如果少于4，比如3张，则还是在切屏的时候有黑屏一闪而过，这是在推流的时候fps达到30帧左右的
        // 小米NOTE4上测出来的，其他的理论上fps约高可能需要drop的帧数越多。
//        mNeedDropCnt = 4; // yes, it's a magic number
    }

    private static final Object sSingletonLock = new Object();
    private static boolean sIsClean = true;
    private volatile boolean mIsSurfaceValid = false;
    private volatile boolean mIsCameraStartedByMe = false;

    private void handleStartRender(RenderConfig config) {
        synchronized (sSingletonLock) {
            while (!sIsClean) {
                Timber.d("lifecycle sIsClean is false, wait");
                try {
                    sSingletonLock.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            Timber.d("lifecycle sIsClean is false now, wait pass");
            sIsClean = false;
        }

        if (!mIsSurfaceValid) {
            return;
        }

        Timber.d("handleStartRender,  mCameraPreviewNaturalWidth:%d, mCameraPreviewNaturalHeight:%d", mCameraPreviewNaturalWidth, mCameraPreviewNaturalHeight);
        mEglCore = new EglCore(null, EglCore.FLAG_TRY_GLES3);
        mDisplaySurface = new WindowSurface(mEglCore, config.mSurface, false);
        mDisplaySurface.makeCurrent();

        mSimpleCameraInput = new SimpleCameraInput();
        mSimpleCameraInput.init();
        mBeautyFilter = new SimpleBeautyFilter();
        mBeautyFilter.init();

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        startCamera();
        mIsCameraStartedByMe = true;
    }

    private void handleStopRender() {
        if (mIsCameraStartedByMe) {
            CameraEngine.getInstance().releaseCamera();
        }

        if (mCameraTexture != null) {
            mCameraTexture.setOnFrameAvailableListener(null);
            mCameraTexture.release();
            mCameraTextureId = OpenGlUtils.NO_TEXTURE;
            mInputTextureId = OpenGlUtils.NO_TEXTURE;
            mCameraTexture = null;
        }

        if (mSimpleCameraInput != null) {
            mSimpleCameraInput.destroyFramebuffers();
            mSimpleCameraInput.destroy();
            mSimpleCameraInput = null;
        }

        if (mBeautyFilter != null) {
            mBeautyFilter.destroy();
            mBeautyFilter = null;
        }

        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }


        synchronized (sSingletonLock) {
            sIsClean = true;
            Timber.d("lifecycle set sIsClean = true, and to notify");
            sSingletonLock.notify();
        }
    }


    // 测试发现，确实preRead会在glFinish的时候更省时间
    private volatile boolean mPreReadPixel = true;

    public void setDebugOutput2Image(boolean mOutput2Image) {
        this.mDump2Image = mOutput2Image;
    }

    public void setDebugUseBeauty(boolean mUseBeauty) {
        this.mUseBeauty = mUseBeauty;
    }

    public boolean isDebugUseBeauty() {
        return mUseBeauty;
    }

    public boolean isDebugOutput2Image() {
        return mDump2Image;
    }

    public boolean isPreReadPixel() {
        return mPreReadPixel;
    }

    public void setPreReadPixel(boolean mPreReadPixel) {
        this.mPreReadPixel = mPreReadPixel;
    }

    // 这个不是debug变量！
    private boolean mIsFirstFrame = true;

    public int getResizeWidthBy(int height) {
        if (height <= 0) {
            throw new IllegalArgumentException("resizeByHeight, height must be positive");
        }

        return (int) (mFullScreenWHRatio * height);
    }

    public int getResizeHeigtBy(int width) {
        if (width <= 0) {
            throw new IllegalArgumentException("resizeByHeight, height must be positive");
        }

        return (int) (width / mFullScreenWHRatio);
    }

    private int mNeedDropOutputFrameCnt = 3;

    private void handleCameraValid() {
        mCameraValid = true;
        mNeedDropOutputFrameCnt = 1;
    }

    private void handleResumeRender() {
        if (mEglCore == null) {
            Timber.d("Skipping drawFrame after shutdown");
            return;
        }

        mDisplaySurface.makeCurrent();
        mDisplaySurface.swapBuffers();
        mCameraTexture.updateTexImage();
        mNeedDropOutputFrameCnt = 1;
    }

    private void handlePauseRender(boolean clear) {
        if (mEglCore == null) {
            Timber.d("Skipping drawFrame after shutdown");
            return;
        }

        if (clear) {
            mDisplaySurface.makeCurrent();
            GLES20.glClearColor(0, 0, 0, 0);
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
            mDisplaySurface.swapBuffers();
        }
    }

    private void handleDrawFrame(float[] trans, long timestamp) {
        if (mEglCore == null) {
            Timber.d("Skipping drawFrame after shutdown");
            return;
        }

        // Latch the next frame from the camera.
        mDisplaySurface.makeCurrent();

        mCameraTexture.updateTexImage();

        if (mIsFirstFrame) {
            mIsFirstFrame = false;
        } else {
            if (mDump2Image) {
                MyUtil.tryReadPixels(mReadPixelStartX, mReadPixelStartY, mOutputWidth, mOutputHeight,
                        mTestCallback != null ? mTestCallback.getDumpImageView() : null);
            } else {
                outputFrame();
            }
        }


        if (timestamp == 0) {
            Timber.d("handleDrawFrame timestamp == 0");
            return;
        }

        mCameraTexture.getTransformMatrix(trans);

        if (mUseBeauty) {
            int id;
            mSimpleCameraInput.setTextureTransformMatrix(trans);
            id = mSimpleCameraInput.onDrawToTexture(mInputTextureId);

            mBeautyFilter.onDraw(id, gLCubeBuffer, gLTextureBuffer);
        } else {
            mSimpleCameraInput.setTextureTransformMatrix(trans);
            mSimpleCameraInput.onDraw(mInputTextureId, gLCubeBuffer, gLTextureBuffer);
        }

        mDisplaySurface.swapBuffers();

        // for debug
        int fps = mFpsRecorder.recordOne();
        if (fps != 0) {
            if (fps < 18) {
                //  小于18帧则认为是比较卡顿了
                mFpsBenchBadSituationCount++;
                if (VERBOSE)
                    Timber.w("FpsBenchBadSituationCount++, mFpsBenchBadSituationCount:%d, fps:%d", mFpsBenchBadSituationCount, fps);
            } else {
                mFpsBenchBadSituationCount = 0;
                if (VERBOSE)
                    Timber.w("not FpsBenchBadSituation, mFpsBenchBadSituationCount:%d, fps:%d", mFpsBenchBadSituationCount, fps);
            }

            if (mFpsBenchBadSituationCount > 4) {
                // 连续4次统计都是不好的fps，则恢复坏的情况， 则认为要用fallback controller了
                mBeautyPublishOutput.useFallbackFpsController(true);
            }

            if (mTestCallback != null) {
                mTestCallback.setShowFps(fps);
            }
        }
    }

    // 这两个是正式变量
    private int mFpsBenchBadSituationCount;
//    Profiler mOutputProfiler = new Profiler("outputFrame");
//    private BenchUtil.Average mDebugReadPixelAveMs = new BenchUtil.Average("glFinish&&glReadPixels", 20);

    private void outputFrame() {
        BeautyCameraPreviewOutput outputListener = mBeautyCameraPreviewOutput;
        if (outputListener == null || !mCameraValid || !mRenderResumed) {
            return;
        }

//        mOutputProfiler.reset();

        if (USE_OUTPUT_MANAGER) {
            long curMs = PublishTimeStamp.currentMs();
            if (!mBeautyPublishOutput.canOutput(curMs)) {
                return;
            }

            if (mNeedDropOutputFrameCnt > 0) {
                mNeedDropOutputFrameCnt--;
                return;
            }

            FrameMessage frameMessage = mBeautyPublishOutput.obtain(mOutputWidth, mOutputHeight, curMs);

            GLES20.glFinish();
            GLES20.glReadPixels(mReadPixelStartX, mReadPixelStartY, mOutputWidth, mOutputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, frameMessage.mRgbBuffer);
//            mOutputProfiler.silent();
//            long dur = mOutputProfiler.tick("glFinish&&glReadPixels");
            // for log
//            mDebugReadPixelAveMs.tick(dur);

            mBeautyPublishOutput.newFrame(frameMessage);
//            mOutputProfiler.tick(" mOutputManager.newFrame");
            if (USE_FPS_RECORDER_DEBUG) {
                if (mTestCallback != null) {
                    int fps = mBeautyPublishOutput.getCurPushFps();
                    if (fps != 0) {
                        mTestCallback.printPushFps(fps);
                    }
                }
            }
        } else {
            initBufferIfNeeded(mOutputWidth, mOutputHeight);

            GLES20.glFinish();
//            mOutputProfiler.tick("glFinish");

            mRgbaByteBuffer.position(0);
            GLES20.glReadPixels(mReadPixelStartX, mReadPixelStartY, mOutputWidth, mOutputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mRgbaByteBuffer);
//            mOutputProfiler.tick("glReadPixels");
            ByteBuffer tmpYuvBuf = ByteBuffer.allocateDirect(mOutputWidth * mOutputHeight * 3 / 2).order(ByteOrder.nativeOrder());
//            mOutputProfiler.tick("allocc YuvBuffer");
            YoloLiveNative.rgba2yuvRotate180Flip(mOutputWidth, mOutputHeight, mRgbaByteBuffer.array(), tmpYuvBuf.array());
//            mOutputProfiler.tick("rgba2yuv");
            outputListener.onFrameData(tmpYuvBuf.array(), mOutputWidth, mOutputHeight, System.currentTimeMillis());
//            mOutputProfiler.tick("onFrameData");
        }
//        mOutputProfiler.over("OVER");

    }

    private ByteBuffer mRgbaByteBuffer;

    private void initBufferIfNeeded(int width, int height) {
        if (mRgbaByteBuffer == null || mRgbaByteBuffer.capacity() < width * height * 4) {
            mRgbaByteBuffer = ByteBuffer.allocateDirect(width * height * 4);
        }
    }

    private static class RenderConfig {
        public final Surface mSurface;

        private RenderConfig(Surface mSurface) {
            this.mSurface = mSurface;
        }
    }
}
