package com.yolo.livesdk.widget.publish2;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.Pair;
import android.view.SurfaceHolder;
import android.view.ViewGroup;

import com.yolo.beautycamera.beauty_preview.filter.base.MagicCameraInputFilter;
import com.yolo.beautycamera.beauty_preview.filter.base.MyBeautyFilter;
import com.yolo.beautycamera.beauty_preview.utils.OpenGlUtils;
import com.yolo.beautycamera.beauty_preview.utils.Rotation;
import com.yolo.beautycamera.beauty_preview.utils.TextureRotationUtil;
import com.yolo.beautycamera.camera.CameraEngine;
import com.yolo.livesdk.widget.publish2.BeautyCameraPreivewDebugger.DebugDumpGuide;
import com.yolo.livesdk.widget.publish_controller.BeautyCameraPreviewOutput;
import com.yolo.livesdk.widget.publish_controller.BeautyPublishController;
import com.yolo.livesdk.widget.publish_controller.BeautyPublishControllerImpl;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.annotation.Nonnull;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import timber.log.Timber;

import static com.yolo.beautycamera.util.Const.OUTPUT_RATOTE_90;

/**
 * Created by shuailongcheng on 21/11/2016.
 * 这里面有三个很关键的比例：
 * 1.相机比例（一般是480x640，不过多少都没关系)
 * 2.显示比例 (YoloCameraPreview不断resize的时候，这个比例是动态变化的）
 * 3.采集比例 (实际输出给推流端的帧高宽比）
 * <p>
 * 也就是
 * a.显示的画面是相机的子画面
 * b.采集输出的是显示的自画面（这一点是glsurfaceview决定的，亲测）
 * <p>
 * 关于这三个值的获取：
 * 1.相机比例，这个是相机告诉我们的
 * 2.显示比例, 这个在onSizeChanged时候会获取到
 * 3.采集比例, 这个现在不支持自定义，就直接等于屏幕宽高比
 * <p>
 * 比例确定了，那绝对值是多少呢？
 * 这里定位参考相机的宽高，比如相机宽高是480*640
 * var surfaceWH (显示的宽高，GLSurfaceView的宽高(setFixedSize））
 * var outputWH
 * <p>
 * 推算关系是 相机尺寸（480，640) 算出 -> outputWH ,比如 (368,640) 然后再决定-> output的fixedSize是多少，比如（380,640)或者 (368, 800);
 * 所以虽说 显示的画面是相机采集的子画面，但是算出来的 surfaceWH 大于 cameraWH还是可能的，这没关系，glsurfaceView是自己做了映射的
 * 对应的函数关系是  （得到camera的宽高后）initOutputWH -> calculateNewFixedSize （然后setFixSize）-> （setFixedSize触发onSurfaceChanged后)adjustViewportAndOutputStartXY
 * <p>
 * 简单来说就是  cameraSize 算出 outputSize(只初始化一次，因为cameraSize也不会变了）
 * 然后结合显示窗口的surfaceRatio(窗口resize的时候会变化）和outputSize 来 算出 实时的mFixedSize
 */
public class BeautyCameraPreview extends GLSurfaceView implements GLSurfaceView.Renderer, TextureBeautyOutputer.OnBeatuyOutputListener, BeautyPublishController.BeautyPublishControllee {

    private boolean mPotrait;
    @Override
    public void initPrefs(boolean initFrontCamera, boolean initUseBeauty, boolean initMirror, boolean portrait) {
        CameraEngine.getInstance().initUseFrontCamera(initFrontCamera);
        mBeautyOn = initUseBeauty;
        mMirror = initMirror;
        if (mBeautyOutputer.isMirror() != mMirror) {
            mBeautyOutputer.switchMirror();
        }
        mPotrait = portrait;
    }

    @Override
    public synchronized void startPreviewOutput(@Nonnull BeautyCameraPreviewOutput previewOutput) {
        mBeautyOutputer.setBeautyOutputListener(this);
        this.mPreviewOutputListener = previewOutput;

        if (mCameraStarted) {
            notifyPreviewOutputWH(previewOutput);
        }
    }

    @Override
    public synchronized void resetPreviewOutputListener() {
        mBeautyOutputer.setBeautyOutputListener(null);
        mPreviewOutputListener = null;
    }

    @Override
    public void destroy() {
        CameraEngine.getInstance().releaseCamera();

        queueEvent(() -> {
            releaseFilterIfNeeded();
            releaseCameraInputerIfNeeded();
        });


        if (mBeautyOutputer.isRunning()) {
            mBeautyOutputer.stopOutput();
        }

        resetPreviewOutputListener();
    }

    @Override
    public void switchMirror() {
        mMirror = !mMirror;
        mBeautyOutputer.switchMirror();
    }

    @Override
    public boolean isMirror() {
        return mMirror;
    }


    @Override
    public boolean isBeautyOn() {
        return mBeautyOn;
    }

    @Override
    public void postUIThread(Runnable runnable) {
        post(runnable);
    }

    @Override
    public void switchCamera() {

    }

    @Override
    public void useFallbackFpsStrategy() {
        // i can do nothing
    }

    @Override
    public void resumeRender() {

    }

    @Override
    public void pauseRender(boolean b) {

    }

    @Override
    public void switchBeautyOn() {
        mBeautyOn = !mBeautyOn;
        mBeautyOutputer.beautyOn(mBeautyOn);
    }

    private final BeautyPublishController mController = new BeautyPublishControllerImpl(this);

    public BeautyPublishController getController() {
        return mController;
    }

    BeautyCameraPreivewDebugger mDebugger = new BeautyCameraPreivewDebugger(this);

    SurfaceTexture mSurfaceTexture;

    int mTextureId = OpenGlUtils.NO_TEXTURE;

    // 1.camera 相关
    int mCameraNaturalWidth, mCameraNaturalHeight;

    // 2.surface 相关
    float mSurfaceRatio; // 同时也是 displayRatio
    int mFixedWidth, mFixedHeight;
    int mCurSurfaceWidth, mCurSurfaceHeight;
    // 相机目前用的采集一般是480*640，跟预览的比例肯定不一样，所以通过调整viewport的方法来裁剪，这个来控制显示自画面

    // 3.output 相关
    final float mOutputWHRatio;
    int mOutputWidth, mOutputHeight;
    int mOutputStartX, mOutputStartY;
    volatile boolean mOutputValid = false; // 有时候在强行resize的时候，期间的过渡期，中间可能有两帧不太对，于是不要输出


    int mViewportStartX, mViewportStartY;
    int mCurViewportWidth, mCurViewportHeight;

    volatile boolean mCameraStarted = false;

    /**
     * 所选择的滤镜，类型为MagicBaseGroupFilter
     * 1.mCameraInputFilter将SurfaceTexture中YUV数据绘制到FrameBuffer
     * 2.filter将FrameBuffer中的纹理绘制到屏幕中
     */
    public volatile MyBeautyFilter filter;
    protected volatile MagicCameraInputFilter cameraInputFilter;

    public TextureBeautyOutputer mBeautyOutputer = new TextureBeautyOutputer();

    private volatile BeautyCameraPreviewOutput mPreviewOutputListener;

    private volatile boolean mBeautyOn;
    private boolean mMirror;

    public BeautyCameraPreivewDebugger getDebugger() {
        return mDebugger;
    }

    public BeautyCameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);

        gLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLCubeBuffer.put(TextureRotationUtil.CUBE).position(0);

        gLTextureBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLTextureBuffer.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);


        DisplayMetrics displayMetrics = Resources.getSystem().getDisplayMetrics();
        mOutputWHRatio = displayMetrics.widthPixels * 1.0f / displayMetrics.heightPixels;

        setPreserveEGLContextOnPause(true);
        setEGLContextClientVersion(2);
        setRenderer(this);
        setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

//        getHolder().setFormat(PixelFormat.RGBA_8888);

        // 调试代码，勿删
        getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                Timber.d("lifecycle surfaceCreated");
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Timber.d("lifecycle surfaceChanged, w:%d, h:%d, format:%d", width, height, format);

            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                Timber.d("lifecycle surfaceDestroyed");
            }
        });
//
        mBeautyOutputer.setDebugger(mDebugger);
    }


    @Override
    public void onBeautyFrameData(byte[] yuvData, int w, int h, long ts) {
        BeautyCameraPreviewOutput lsn = mPreviewOutputListener;
        if (lsn != null) {
            lsn.onFrameData(yuvData, w, h, ts);
        }
    }


    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        Timber.d("onSizeChanged: w:%d,h:%d,oldw:%d,oldh:%d", w, h, oldw, oldh);

        mSurfaceRatio = w * 1.0f / h;

        mOutputValid = false;

        Pair<Integer, Integer> newFixedSize = calculateNewFixedSize();
        hackSetFixedSize(newFixedSize.first, newFixedSize.second);
    }

    private void hackSetFixedSize(int w, int h) {
        Timber.d("hackSetFixedSize: w:%d,h:%d", w, h);
        if (!mIsSurfaceCreated) {
            return;
        }

        // 亲测，forceLayout()没用，forceLayout+requestLayout也没用;
        if (mFixedWidth == w && mFixedHeight == h) {
            post(new Runnable() {
                @Override
                public void run() {
                    // 好奇怪，必须第一个不post，第二个post才生效
                    getHolder().setFixedSize(mFixedWidth - 1, mFixedHeight);
                    getHolder().setFixedSize(mFixedWidth, mFixedHeight);
                }
            });
        } else {
            mFixedWidth = w;
            mFixedHeight = h;

            post(() -> getHolder().setFixedSize(mFixedWidth, mFixedHeight));
        }
    }


    int bytesAlign16(int i) {
        return (i + 15) / 16 * 16;
    }


    SurfaceTexture.OnFrameAvailableListener onFrameAvailableListener = surfaceTexture -> BeautyCameraPreview.this.requestRender();

    /**
     * 顶点坐标
     */
    public final FloatBuffer gLCubeBuffer;

    /**
     * 纹理坐标
     */
    public final FloatBuffer gLTextureBuffer;

    volatile boolean mIsSurfaceCreated = false;

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        mIsSurfaceCreated = true;
        Timber.d("onSurfaceCreated");

        GLES20.glDisable(GL10.GL_DITHER);
        GLES20.glClearColor(0, 0, 0, 0);
//        GLES20.glEnable(GL10.GL_CULL_FACE);
//        GLES20.glEnable(GL10.GL_DEPTH_TEST);

        if (mTextureId == OpenGlUtils.NO_TEXTURE) {
            mTextureId = OpenGlUtils.getExternalOESTextureID();
            if (mTextureId != OpenGlUtils.NO_TEXTURE) {
                mSurfaceTexture = new SurfaceTexture(mTextureId);
                mSurfaceTexture.setOnFrameAvailableListener(onFrameAvailableListener);
            }
        }

        cameraInputFilter = new MagicCameraInputFilter();
        cameraInputFilter.init();
        filter = new MyBeautyFilter();
        filter.init();

        filter.onOutputSizeChanged(mCurSurfaceWidth, mCurSurfaceHeight);
        filter.onInputSizeChanged(mCameraNaturalWidth, mCameraNaturalHeight);
        filter.setViewportParam(mViewportStartX, mViewportStartY, mCurViewportWidth, mCurViewportHeight);

        if (!mCameraStarted) {
            mCameraStarted = true;
            startCameraPreview();
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Timber.d("onSurfaceChanged, width:%d, height:%d", width, height);
        mCurSurfaceWidth = width;
        mCurSurfaceHeight = height;

        adjustViewportAndOutputStartXY();

        if (isCurViewStateValid()) {
            tellFilterScreenChanged();

            startOutputerIfNeeded();
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
//        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClearColor(0, 0, 1, 0.5f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (mSurfaceTexture == null) {
            return;
        }

        mSurfaceTexture.updateTexImage();

        float[] mtx = new float[16];
        mSurfaceTexture.getTransformMatrix(mtx);
        cameraInputFilter.setTextureTransformMatrix(mtx);

        int id;
        int idForOutput = mTextureId;

        if (!mBeautyOn) {
            cameraInputFilter.onDraw(mTextureId, gLCubeBuffer, gLTextureBuffer);
        } else {
            id = cameraInputFilter.onDrawToTexture(mTextureId,
                    mViewportStartX,
                    mViewportStartY,
                    mCurViewportWidth,
                    mCurViewportHeight);
            filter.onDraw(id, gLCubeBuffer, gLTextureBuffer);

//            idForOutput = id;
        }


        mBeautyOutputer.setTextureId(idForOutput);
        mBeautyOutputer.frameAvailable(mSurfaceTexture);
    }


    /**
     * to adjust viewport that glsurfaceview can preview the correct w/h ratio,
     * and pin preview to center part of the whole camera preview frame
     */
    private void adjustViewportAndOutputStartXY() {
        Timber.d("adjustViewportAndOutputSize ");
        // 计算 outputWH
        int viewportWidth = mFixedWidth;
        int viewportHeight = mFixedHeight;
        int viewportStartX = 0;
        int viewportStartY = 0;

        if (false && isCurViewStateValid()) {
            // to adjus viewport to display wh ratio
            float cameraWHRatio = mCameraNaturalWidth * 1.0f / mCameraNaturalHeight;

            if (mSurfaceRatio > cameraWHRatio) {
                // 屏幕太宽，应该让surface的部分高度显示在屏幕之外
                // 假设dispW = surfW = 1, 则 dispH/surfH = (surfW/surfH)/(dispW/dispH) = surfaceWHRatio/mScreenWHRatio
                float heightShowOutRatio = cameraWHRatio / mSurfaceRatio;
                viewportHeight = (int) (mFixedHeight / heightShowOutRatio);
                viewportStartY = -((viewportHeight - mFixedHeight) / 2);
            } else {
                // 屏幕太高，应该让surface的部分宽度显示在屏幕之外
                // 假设dispH = surfH = 1, 则 dispW/surfW = (dispw/dispH)/(surfW/surfH) = mScreenWHRatio/surfaceWHRatio
                float widthShowOutRatio = mSurfaceRatio / cameraWHRatio;
                viewportWidth = (int) (mFixedWidth / widthShowOutRatio);
                viewportStartX = -((viewportWidth - mFixedWidth) / 2);
            }

            mOutputValid = true;
            mOutputStartX = (mCurSurfaceWidth - mOutputWidth) / 2;
            mOutputStartY = (mCurSurfaceHeight - mOutputHeight) / 2;

            Timber.d("adjustViewportAndOutputSize, valid 1-> mCurSurfaceWidth:%d, mCurSurfaceHeight:%d, viewportWidth:%d, viewportHeight:%d, viewportStartX:%d, viewportStartY:%d",
                    mCurSurfaceWidth, mCurSurfaceHeight, viewportWidth, viewportHeight, viewportStartX, viewportStartY);
            Timber.d("adjustViewportAndOutputSize, valid 2->mOutputStartX:%d, mOutputStartY:%d, mOutputWidth:%d, mOutputHeight:%d",
                    mOutputStartX, mOutputStartY, mOutputWidth, mOutputHeight);

            mDebugger.mDumpGuide = new DebugDumpGuide(mOutputStartX, mOutputStartY, mOutputWidth, mOutputHeight);
        } else {
            Timber.e("adjustViewportAndOutputSize, mFixedWidth != 0 && mCurSurfaceWidth == mFixedWidth ->mOutputValid = false, mFixedWidth:%d, mFixedHeight:%d, mCurSurfaceWidth:%d, mCurSurfaceHeight:%d",
                    mFixedWidth, mFixedHeight, mCurSurfaceWidth, mCurSurfaceHeight);

            viewportWidth = mCurSurfaceWidth;
            viewportHeight = mCurSurfaceHeight;
            viewportStartX = 0;
            viewportStartY = 0;
            mOutputValid = false;
        }

        GLES20.glViewport(viewportStartX, viewportStartY, viewportWidth, viewportHeight);

        mViewportStartX = viewportStartX;
        mViewportStartY = viewportStartY;
        mCurViewportWidth = viewportWidth;
        mCurViewportHeight = viewportHeight;

        mDebugger.setViewportParam(viewportStartX, viewportStartY, viewportWidth, viewportHeight);
    }

    /**
     * view在一些hack resize或者还没初始化好的时候，是属于invalide state的，这些时候建议不要做一些关键性的事情，比如
     * 推流或者设置一些 filter的相关参数
     *
     * @return
     */
    private boolean isCurViewStateValid() {
        return mFixedWidth != 0 && mCurSurfaceWidth == mFixedWidth;
    }

    /**
     * 根据相机的宽高计算出输出frame的宽高
     */
    private void initOutputWH() {
        if (!mIsSurfaceCreated) {
            return;
        }

        float cameraRatio = mCameraNaturalWidth * 1.0f / mCameraNaturalHeight;

        // outputSize must be 16 bytes alignment
        mOutputWidth = mCameraNaturalWidth;
        mOutputHeight = mCameraNaturalHeight;
        if (mOutputWHRatio > cameraRatio) {
            // 预览比camera提供的画面宽， 所以要裁掉相机的高度，与相机同宽
            mOutputHeight = (int) (mOutputWidth / mOutputWHRatio);
            mOutputHeight = bytesAlign16(mOutputHeight);
        } else {
            // 预览比camera提供的画面高， 所以要裁掉相机的宽度，与相机同高
            mOutputWidth = (int) (mOutputHeight * mOutputWHRatio);
            mOutputWidth = bytesAlign16(mOutputWidth);
        }

        Timber.d("initOutputWHAndFixedWH, mOutputWidth:%d, mOutputHeight:%d, mSurfaceRatio:%f", mOutputWidth, mOutputHeight, mSurfaceRatio);
        BeautyCameraPreviewOutput previewOutput = mPreviewOutputListener;
        if (previewOutput != null) {
            notifyPreviewOutputWH(previewOutput);
        }
    }

    private void notifyPreviewOutputWH(BeautyCameraPreviewOutput previewOutput) {
        if (OUTPUT_RATOTE_90) {
            previewOutput.onPreviewOpened(mOutputHeight, mOutputWidth);
        } else {
            previewOutput.onPreviewOpened(mOutputWidth, mOutputHeight);
        }
    }

    AtomicBoolean mOutputThreadStarted = new AtomicBoolean(false);

    /**
     * 可以反复调用
     */
    private void startOutputerIfNeeded() {
        if (mOutputWidth == 0 || mOutputWidth == 0) {
            throw new IllegalStateException("startOutputThreadIfNot is not timing");
        }
        if (mOutputThreadStarted.getAndSet(true)) {
            return;
        }

        mBeautyOutputer.startOutput((ViewGroup) getParent(),
                new TextureBeautyOutputer.OutputConfig(
                        mCameraNaturalWidth, mCameraNaturalHeight,
                        mOutputWidth, mOutputHeight,
                        EGL14.eglGetCurrentContext()));
    }

    /**
     * 根据outputSize来算出新的FixedSize
     * outputSize是有16字节对其要求，
     * 而为了显示的画面不拉升，会算出一个最接近outoutSize的值，设置给fixedSize
     *
     * @return
     */
    private Pair<Integer, Integer> calculateNewFixedSize() {
        int retW, retH;
        if (mSurfaceRatio > mOutputWHRatio) {
            // 裁掉 surface的 宽度, 高度相等
            retH = mOutputHeight;
            retW = (int) (retH * mSurfaceRatio);
        } else {
            // 裁掉 实际输出的 高度, 宽度相等
            retW = mOutputWidth;
            retH = (int) (retW / mSurfaceRatio);
        }
        return new Pair<>(retW, retH);
    }


    private void startCameraPreview() {
        CameraEngine.getInstance().startPreview(mSurfaceTexture,
                new CameraEngine.OnPreviewListener() {
                    @Override
                    public void onPreviewInfo(int previewWidth, int previewHeight, boolean isCamera1, boolean isFront) {

                        mCameraNaturalWidth = previewWidth;
                        mCameraNaturalHeight = previewHeight;
                        mCameraStarted = true;

                        Timber.d("mCameraNaturalWidth:%d, mCameraNaturalHeight:%d, isCamera1:%b, isFront:%b",
                                mCameraNaturalWidth, mCameraNaturalHeight, isCamera1, isFront);

                        // 因为要显示镜面，所以 前置摄像头的时候，不需要vertical翻转（摄像头的vertical是natural的左右）
                        if (isCamera1) {
                            adjustSize(270, true, false);
                            if (isFront) {
                                mBeautyOutputer.adjustSize(isFront, 270, false, true, false, false);
                            } else {
                                mBeautyOutputer.adjustSize(isFront, 270, false, false, false, false);
                            }
                        } else {
                            adjustSize(180, true, false);
                            if (isFront) {
                                mBeautyOutputer.adjustSize(isFront, 180, false, true, true, true);
                            } else {
                                mBeautyOutputer.adjustSize(isFront, 180, true, true, true, true);
                            }
                        }

                        initOutputWH();

                        Pair<Integer, Integer> newFixedSize = calculateNewFixedSize();
                        hackSetFixedSize(newFixedSize.first, newFixedSize.second);

                        cameraInputFilter.onInputSizeChanged(mCameraNaturalWidth, mCameraNaturalHeight);
                    }

                    @Override
                    public void onError(@CameraEngine.ErrorCode int error) {
                        Timber.e("startPreview onError:%d", error);
                        BeautyCameraPreviewOutput previewOutput = mPreviewOutputListener;
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
                }, mPotrait);
    }


    public boolean mDebugFilpHorizontal;
    public boolean mDebugFilpVertical;
    public int mDebugCurRotation;

    public void debugIncreRoation(int diff) {
        mDebugCurRotation += (diff + 360);
        mDebugCurRotation %= 360;
        adjustSize(mDebugCurRotation, mDebugFilpHorizontal, mDebugFilpVertical);
    }

    public void debugWwitchFlipHori() {
        mDebugFilpHorizontal = !mDebugFilpHorizontal;
        adjustSize(mDebugCurRotation, mDebugFilpHorizontal, mDebugFilpVertical);
    }

    public void debugSwitchFlipVert() {
        mDebugFilpVertical = !mDebugFilpVertical;
        adjustSize(mDebugCurRotation, mDebugFilpHorizontal, mDebugFilpVertical);
    }

    void adjustSize(int rotation, boolean flipHorizontal, boolean flipVertical) {
        Timber.d("adjustSize, rotation:%d, flipHorizontal:%b, flipVertical:%b", rotation, flipHorizontal, flipVertical);
        mDebugFilpHorizontal = flipHorizontal;
        mDebugFilpVertical = flipVertical;
        mDebugCurRotation = rotation;

        float[] textureCords = TextureRotationUtil.getRotation(Rotation.fromInt(rotation),
                flipHorizontal, flipVertical);
        float[] cube = TextureRotationUtil.CUBE;
//        float ratio1 = (float) mSurfaceWidth / mCameraNaturalWidth;
//        float ratio2 = (float) mSurfaceHeight / mCameraNaturalHeight;
//        float ratioMax = Math.max(ratio1, ratio2);
//        int imageWidthNew = Math.round(mCameraNaturalWidth * ratioMax);
//        int imageHeightNew = Math.round(mCameraNaturalHeight * ratioMax);

//        float ratioWidth = imageWidthNew / (float) mSurfaceWidth;
//        float ratioHeight = imageHeightNew / (float) mSurfaceHeight;
//
//        Timber.d("adjustSize, scaleType:" + scaleType);
//        if (scaleType == MagicBaseView.ScaleType.CENTER_INSIDE) {
//            cube = new float[]{
//                    TextureRotationUtil.CUBE[0] / ratioHeight, TextureRotationUtil.CUBE[1] / ratioWidth,
//                    TextureRotationUtil.CUBE[2] / ratioHeight, TextureRotationUtil.CUBE[3] / ratioWidth,
//                    TextureRotationUtil.CUBE[4] / ratioHeight, TextureRotationUtil.CUBE[5] / ratioWidth,
//                    TextureRotationUtil.CUBE[6] / ratioHeight, TextureRotationUtil.CUBE[7] / ratioWidth,
//            };
//
//        } else if (scaleType == MagicBaseView.ScaleType.FIT_XY) {
//
//        } else if (scaleType == MagicBaseView.ScaleType.CENTER_CROP) {
//            float distHorizontal = (1 - 1 / ratioWidth) / 2;
//            float distVertical = (1 - 1 / ratioHeight) / 2;
//            textureCords = new float[]{
//                    addDistance(textureCords[0], distVertical), addDistance(textureCords[1], distHorizontal),
//                    addDistance(textureCords[2], distVertical), addDistance(textureCords[3], distHorizontal),
//                    addDistance(textureCords[4], distVertical), addDistance(textureCords[5], distHorizontal),
//                    addDistance(textureCords[6], distVertical), addDistance(textureCords[7], distHorizontal),
//            };
//        }

        // 负责旋转和边距
        gLTextureBuffer.clear();
        gLTextureBuffer.put(textureCords).position(0);

        // 负责 stretch伸展
        gLCubeBuffer.clear();
        gLCubeBuffer.put(cube).position(0);
    }


    /**
     * 一些视频的输入尺寸变化或者是filter本身重新创建了都应该调用这个
     */
    private void tellFilterScreenChanged() {
        cameraInputFilter.initCameraFrameBuffer(mCameraNaturalWidth, mCameraNaturalHeight);

        cameraInputFilter.onInputSizeChanged(mCurSurfaceWidth, mCurSurfaceHeight);
//        cameraInputFilter.onDisplaySizeChanged(mCurSurfaceWidth, mCurSurfaceHeight);

        filter.onOutputSizeChanged(mCurSurfaceWidth, mCurSurfaceHeight);
        filter.onInputSizeChanged(mCameraNaturalWidth, mCameraNaturalHeight);
        filter.setViewportParam(mViewportStartX, mViewportStartY, mCurViewportWidth, mCurViewportHeight);
    }

    private void releaseCameraInputerIfNeeded() {
        if (cameraInputFilter != null) {
            cameraInputFilter.destroyFramebuffers();
            cameraInputFilter.destroy();
        }
    }


    private void releaseFilterIfNeeded() {
        if (filter != null) {
            Timber.d("filter.destroy start:" + filter);
            filter.destroy();
            Timber.d("filter.destroy end:" + filter);
            filter = null;
        }
    }


}
