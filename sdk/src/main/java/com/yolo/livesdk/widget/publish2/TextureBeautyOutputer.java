/*
 * Copyright 2013 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.yolo.livesdk.widget.publish2;

import android.graphics.SurfaceTexture;
import android.opengl.EGLContext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.ViewGroup;

import com.github.piasy.cameracompat.processor.RgbYuvConverter;
import com.yolo.beautycamera.beauty_preview.gles.EglCore;
import com.yolo.beautycamera.beauty_preview.gles.OffscreenSurface;
import com.yolo.beautycamera.beauty_preview.gles.WindowSurface;
import com.yolo.beautycamera.beauty_preview.filter.base.MagicCameraInputFilter;
import com.yolo.beautycamera.beauty_preview.filter.base.MyBeautyFilter;
import com.yolo.beautycamera.beauty_preview.utils.MyUtil;
import com.yolo.beautycamera.beauty_preview.utils.Rotation;
import com.yolo.beautycamera.beauty_preview.utils.TextureRotationUtil;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.atomic.AtomicBoolean;

import timber.log.Timber;

import static com.yolo.beautycamera.util.Const.OUTPUT_RATOTE_90;


/**
 * Encode a movie from frames rendered from an external texture image.
 * <p>
 * The object wraps an encoder running on a dedicated thread.  The various control messages
 * may be sent from arbitrary threads (typically the app UI thread).  The encoder thread
 * manages both sides of the encoder (feeding and draining); the only external input is
 * the GL texture.
 * <p>
 * The design is complicated slightly by the need to create an EGL context that shares state
 * with a view that gets restarted if (say) the device orientation changes.  When the view
 * in question is a GLSurfaceView, we don't have full control over the EGL context creation
 * on that side, so we have to bend a bit backwards here.
 * <p>
 * To use:
 * <ul>
 * <li>create TextureMovieEncoder object
 * <li>create an EncoderConfig
 * <li>call TextureMovieEncoder#startRecording() with the config
 * <li>call TextureMovieEncoder#setTextureId() with the texture object that receives frames
 * <li>for each frame, after latching it with SurfaceTexture#updateTexImage(),
 * call TextureMovieEncoder#frameAvailable().
 * </ul>
 * <p>
 * TODO: tweak the API (esp. textureId) so it's less awkward for simple use cases.
 */
public class TextureBeautyOutputer implements Runnable {
    private static final boolean VERBOSE = false;

    public interface OnBeatuyOutputListener {
        void onBeautyFrameData(byte[] yuvData, int w, int h, long ts);
    }


    public static volatile boolean DUMP_2_IMAGEVIEW = false;

    private static final String TAG = "";

    private static final int MSG_START_RECORDING = 0;
    private static final int MSG_STOP_RECORDING = 1;
    private static final int MSG_FRAME_AVAILABLE = 2;
    private static final int MSG_SET_TEXTURE_ID = 3;
    private static final int MSG_QUIT = 4;

    // ----- accessed exclusively by encoder thread -----
    public static OffscreenSurface mOffscreenSurface;
    private WindowSurface mInputWindowSurface;
    private EglCore mEglCore;

    // filter
    private volatile MagicCameraInputFilter cameraInputFilter;
    private int mTextureId;
    // public for debug
    public volatile MyBeautyFilter filter;
    public volatile FloatBuffer gLCubeBuffer;
    public volatile FloatBuffer gLTextureBufferMirror;
    public volatile FloatBuffer gLTextureBufferNormal;
    private volatile boolean mMirror;
    private volatile boolean mIsFront;

    // ----- accessed by multiple threads -----
    private volatile EncoderHandler mHandler;

    private Object mReadyFence = new Object();      // guards ready/running
    private boolean mReady;
    private boolean mRunning;

    private static final boolean USE_OFF_SCREEN = false;
    private volatile OnBeatuyOutputListener mOnBeatuyOutputListener;

    private volatile boolean mBeautyOn = true;

    public TextureBeautyOutputer() {
        gLCubeBuffer = ByteBuffer.allocateDirect(TextureRotationUtil.CUBE.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLCubeBuffer.put(TextureRotationUtil.CUBE).position(0);

        gLTextureBufferMirror = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLTextureBufferMirror.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);

        gLTextureBufferNormal = ByteBuffer.allocateDirect(TextureRotationUtil.TEXTURE_NO_ROTATION.length * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        gLTextureBufferMirror.put(TextureRotationUtil.TEXTURE_NO_ROTATION).position(0);
        mMirror = true;
    }

    public void beautyOn(boolean on) {
        mBeautyOn = on;
    }

    private BeautyCameraPreivewDebugger mDebugger;

    public boolean mDebugFilpHorizontal;
    public boolean mDebugFilpVertical;
    public boolean mDebugMirrorFilpHorizontal;
    public boolean mDebugMirrorFilpVertical;
    public int mDebugCurRotation;

    public void adjustSize(boolean isFront, int rotation, boolean flipHorizontal, boolean flipVertical, boolean mirrorFlipHori, boolean mirrorFlipVert) {
        mIsFront = isFront;

        Timber.d("adjustSize, rotation:%d, flipHorizontal:%b, flipVertical:%b, mirrorFlipHori:%b, mirrorFlipVert:%b",
                rotation, flipHorizontal, flipVertical, mirrorFlipHori, mirrorFlipVert);
        mDebugFilpHorizontal = flipHorizontal;
        mDebugFilpVertical = flipVertical;
        mDebugCurRotation = rotation;
        mDebugMirrorFilpHorizontal = mirrorFlipHori;
        mDebugMirrorFilpVertical = mirrorFlipVert;


        float[] mirrorTextureCords = TextureRotationUtil.getRotation(Rotation.fromInt(rotation),
                mirrorFlipHori, mirrorFlipVert);
        // 负责旋转和边距
        gLTextureBufferMirror.clear();
        gLTextureBufferMirror.put(mirrorTextureCords).position(0);

        float[] textureCords = TextureRotationUtil.getRotation(Rotation.fromInt(rotation),
                flipHorizontal, flipVertical);
        gLTextureBufferNormal.clear();
        gLTextureBufferNormal.put(textureCords).position(0);

        float[] cube = TextureRotationUtil.CUBE;
        // 负责 stretch伸展
        gLCubeBuffer.clear();
        gLCubeBuffer.put(cube).position(0);
    }

    public void switchMirror() {
        mMirror = !mMirror;
        Timber.d("adjustSize, switchMirror, cur mMirror:%b", mMirror);
    }

    public boolean isMirror() {
        return mMirror;
    }

    public void debugIncreRoation(int diff) {
        mDebugCurRotation += (diff + 360);
        mDebugCurRotation %= 360;
        adjustSize(mIsFront, mDebugCurRotation, mDebugFilpHorizontal, mDebugFilpVertical, mDebugMirrorFilpHorizontal, mDebugMirrorFilpVertical);
    }

    public void debugWwitchFlipHori() {
        mDebugFilpHorizontal = !mDebugFilpHorizontal;
        adjustSize(mIsFront, mDebugCurRotation, mDebugFilpHorizontal, mDebugFilpVertical, mDebugMirrorFilpHorizontal, mDebugMirrorFilpVertical);
    }

    public void debugSwitchFlipVert() {
        mDebugFilpVertical = !mDebugFilpVertical;
        adjustSize(mIsFront, mDebugCurRotation, mDebugFilpHorizontal, mDebugFilpVertical, mDebugMirrorFilpHorizontal, mDebugMirrorFilpVertical);
    }

    public void setDebugger(BeautyCameraPreivewDebugger debugger) {
        this.mDebugger = debugger;
    }

    public void setBeautyOutputListener(OnBeatuyOutputListener mBeautyOutputListener) {
        this.mOnBeatuyOutputListener = mBeautyOutputListener;
    }


    /**
     * Encoder configuration.
     * <p>
     * Object is immutable, which means we can safely pass it between threads without
     * explicit synchronization (and don't need to worry about it getting tweaked out from
     * under us).
     * <p>
     * TODO: make frame rate and iframe interval configurable?  Maybe use builder pattern
     * with reasonable defaults for those and bit rate.
     */
    public static class OutputConfig {
        final int mCameraPreviewWidth;
        final int mCameraPreviewHeight;
        final int mOutputWidth;
        final int mOutputHeight;
        final EGLContext mEglContext;

        public OutputConfig(int cameraPreviewWidth, int cameraPreviewHeight, int outputWidth, int outputHeight, EGLContext sharedEglContext) {
            mCameraPreviewWidth = cameraPreviewWidth;
            mCameraPreviewHeight = cameraPreviewHeight;
            mOutputWidth = outputWidth;
            mOutputHeight = outputHeight;
            mEglContext = sharedEglContext;
        }

        @Override
        public String toString() {
            return "EncoderConfig: " + mCameraPreviewWidth + "x" + mCameraPreviewHeight + ", ctxt=" + mEglContext
                    + ", mOutputWidth=" + mOutputWidth
                    + ", mOutputHeight=" + mOutputHeight;
        }
    }

    public void startOutput(final ViewGroup viewGroup, final OutputConfig outputConfig) {
        mGhostTxtView = new TextureView(viewGroup.getContext());

        mGhostTxtView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                surface.setDefaultBufferSize(outputConfig.mCameraPreviewWidth, outputConfig.mCameraPreviewHeight);
                Timber.d("TextureView. setDefaultBufferSize, w:%d, h:%d", outputConfig.mCameraPreviewWidth, outputConfig.mCameraPreviewHeight);
                doStartOutputThread(outputConfig);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
                Timber.d("TextureView, prepareGhostTextureView onSurfaceTextureSizeChanged");
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                Timber.d("TextureView,  onSurfaceTextureDestroyed");
                // FIXME: 30/11/2016 mInputWindowSurface.release里释放了Wrapper Surface是不是就不需要返回true了呢？
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {
//                Timber.d("TextureView,  onSurfaceTextureUpdated");
                hasFrameToRead.set(true);
            }
        });

        ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(1, 1);
        mGhostTxtView.setLayoutParams(lp);
        viewGroup.post(new Runnable() {
            @Override
            public void run() {
                int count = viewGroup.getChildCount();
                viewGroup.addView(mGhostTxtView, count == 0 ? 0 : count - 1);
            }
        });
    }

    private TextureView mGhostTxtView;

    /**
     * Tells the video recorder to start recording.  (Call from non-encoder thread.)
     * <p>
     * Creates a new thread, which will create an encoder using the provided configuration.
     * <p>
     * Returns after the recorder thread has started and is ready to accept Messages.  The
     * encoder may not yet be fully configured.
     */
    private void doStartOutputThread(OutputConfig config) {
        Timber.d("Encoder: startRecording()");
        synchronized (mReadyFence) {
            if (mRunning) {
                Timber.w("Encoder thread already running");
                return;
            }
            mRunning = true;
            new Thread(this, "BeautyOutputer").start();
            while (!mReady) {
                try {
                    mReadyFence.wait();
                } catch (InterruptedException ie) {
                    // ignore
                }
            }
        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_START_RECORDING, config));
    }

    public boolean isRunning() {
        return mRunning;
    }

    /**
     * Tells the video recorder to stop recording.  (Call from non-encoder thread.)
     * <p>
     * Returns immediately; the encoder/muxer may not yet be finished creating the movie.
     * <p>
     * TODO: have the encoder thread invoke a callback on the UI thread just before it shuts down
     * so we can provide reasonable status UI (and let the caller know that movie encoding
     * has completed).
     */
    public void stopOutput() {
        mHandler.sendMessage(mHandler.obtainMessage(MSG_STOP_RECORDING));
        mHandler.sendMessage(mHandler.obtainMessage(MSG_QUIT));
        // We don't know when these will actually finish (or even start).  We don't want to
        // delay the UI thread though, so we return immediately.
    }

    /**
     * Tells the video recorder that a new frame is available.  (Call from non-encoder thread.)
     * <p>
     * This function sends a message and returns immediately.  This isn't sufficient -- we
     * don't want the caller to latch a new frame until we're done with this one -- but we
     * can get away with it so long as the input frame rate is reasonable and the encoder
     * thread doesn't stall.
     * <p>
     * TODO: either block here until the texture has been rendered onto the encoder surface,
     * or have a separate "block if still busy" method that the caller can execute immediately
     * before it calls updateTexImage().  The latter is preferred because we don't want to
     * stall the caller while this thread does work.
     */

    public void frameAvailable(SurfaceTexture st) {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }

        float[] transform = new float[16];      // TODO - avoid alloc every frame
        st.getTransformMatrix(transform);

        long timestamp = st.getTimestamp();
        if (timestamp == 0) {
            // Seeing this after device is toggled off/on with power button.  The
            // first frame back has a zero timestamp.
            //
            // MPEG4Writer thinks this is cause to abort() in native code, so it's very
            // important that we just ignore the frame.
            Log.w(TAG, "frameAvailable HEY: got SurfaceTexture with timestamp of zero");
            return;

        }

        mHandler.sendMessage(mHandler.obtainMessage(MSG_FRAME_AVAILABLE,
                (int) (timestamp >> 32), (int) timestamp, transform));
    }

    /**
     * Tells the video recorder what texture name to use.  This is the external texture that
     * we're receiving camera previews in.  (Call from non-encoder thread.)
     * <p>
     * TODO: do something less clumsy
     */
    public void setTextureId(int id) {
        synchronized (mReadyFence) {
            if (!mReady) {
                return;
            }
        }
        mHandler.sendMessage(mHandler.obtainMessage(MSG_SET_TEXTURE_ID, id, 0, null));
    }

    /**
     * Encoder thread entry point.  Establishes Looper/Handler and waits for messages.
     * <p>
     *
     * @see Thread#run()
     */
    @Override
    public void run() {
        // Establish a Looper for this thread, and define a Handler for it.
        Looper.prepare();
        Timber.d("OutputThread starting");
        synchronized (mReadyFence) {
            mHandler = new EncoderHandler(this);
            mReady = true;
            mReadyFence.notify();
        }
        Looper.loop();

        Timber.d("OutputThread exiting");
        synchronized (mReadyFence) {
            mReady = mRunning = false;
            mHandler = null;
        }
    }


    /**
     * Handles encoder state change requests.  The handler is created on the encoder thread.
     */
    private static class EncoderHandler extends Handler {
        private WeakReference<TextureBeautyOutputer> mWeakEncoder;

        public EncoderHandler(TextureBeautyOutputer encoder) {
            mWeakEncoder = new WeakReference<TextureBeautyOutputer>(encoder);
        }

        @Override  // runs on encoder thread
        public void handleMessage(Message inputMessage) {
            int what = inputMessage.what;
            Object obj = inputMessage.obj;

            TextureBeautyOutputer outputer = mWeakEncoder.get();
            if (outputer == null) {
                Log.w(TAG, "EncoderHandler.handleMessage: encoder is null");
                return;
            }

            switch (what) {
                case MSG_START_RECORDING:
                    outputer.handleStartRecording((OutputConfig) obj);
                    break;
                case MSG_STOP_RECORDING:
                    outputer.handleStopRecording();
                    break;
                case MSG_FRAME_AVAILABLE:
                    long timestamp = (((long) inputMessage.arg1) << 32) |
                            (((long) inputMessage.arg2) & 0xffffffffL);
                    outputer.handleFrameAvailable((float[]) obj, timestamp);
                    break;
                case MSG_SET_TEXTURE_ID:
                    outputer.handleSetTexture(inputMessage.arg1);
                    break;
                case MSG_QUIT:
                    Looper.myLooper().quit();
                    break;

                default:
                    throw new RuntimeException("Unhandled msg what=" + what);
            }
        }
    }


    /**
     * Starts recording.
     */
    private void handleStartRecording(final OutputConfig config) {
        Timber.d("handleStartRecording here");
        hasFrameToRead.set(false);

        mPreviewWidth = config.mCameraPreviewWidth;
        mPreviewHeight = config.mCameraPreviewHeight;
        mOutputWidth = config.mOutputWidth;
        mOutputHeight = config.mOutputHeight;

//        mViewportStartX = -(mPreviewWidth - mOutputWidth) / 2;
//        mViewportStartY = -(mPreviewHeight - mOutputHeight) / 2;
//        mCurViewportWidth = mPreviewWidth;
//        mCurViewportHeight = mPreviewHeight;
//
//        Timber.d("mViewportStartX:%d, mViewportStartY:%d, mCurViewportWidth:%d, mCurViewportHeight:%d", mViewportStartX, mViewportStartY, mCurViewportWidth, mCurViewportHeight);

        if (mOutputWidth < mPreviewWidth) {
            mOutputStartX = (mPreviewWidth - mOutputWidth) / 2;
        }
        if (mOutputHeight < mPreviewHeight) {
            mOutputStartY = (mPreviewHeight - mOutputHeight) / 2;
        }

        mEglCore = new EglCore(config.mEglContext, EglCore.FLAG_RECORDABLE);
        if (USE_OFF_SCREEN) {
            mOffscreenSurface = new OffscreenSurface(mEglCore, mPreviewWidth, mPreviewHeight);
            mOffscreenSurface.makeCurrent();
        } else {
            mInputWindowSurface = new WindowSurface(mEglCore, new Surface(mGhostTxtView.getSurfaceTexture()), true);
            mInputWindowSurface.makeCurrent();
        }
        Timber.d("handleCreateSurface");

        createCameraInputer();
        createBeautyFilter();
    }


    /**
     * Handles notification of an available frame.
     * <p>
     * The texture is rendered onto the encoder's input surface, along with a moving
     * box (just because we can).
     * <p>
     *
     * @param transform      The texture transform, from SurfaceTexture.
     * @param timestampNanos The frame's timestamp, from SurfaceTexture.
     */
    private void handleFrameAvailable(float[] transform, long timestampNanos) {
        if (mOffscreenSurface == null && mInputWindowSurface == null) {
            return;
        }

        if (VERBOSE) Timber.v("handleFrameAvailable : step 1");
        if (VERBOSE) Timber.v("handleFrameAvailable : step 2");
        cameraInputFilter.setTextureTransformMatrix(transform);

        long startDraw = System.nanoTime() / 1000_000;
        // 只有前置摄像的时候才有镜像
        FloatBuffer textureBuff = mMirror && mIsFront ? gLTextureBufferMirror : gLTextureBufferNormal;
        int id = mTextureId;
        if (!mBeautyOn) {
            if (VERBOSE) Timber.v("handleFrameAvailable : step 3.1");
            cameraInputFilter.onDraw(id, gLCubeBuffer, textureBuff);
        } else {
            if (VERBOSE) Timber.v("handleFrameAvailable : step 3.2");
            id = cameraInputFilter.onDrawToTexture(mTextureId,
                    0,
                    0,
                    mPreviewWidth,
                    mPreviewHeight);

            if (VERBOSE)
                Timber.v("goting to handleFrameAvailable:filter.onDrawFrame ,id: %d, filter:%s", id, filter);

            filter.onDraw(id, gLCubeBuffer, textureBuff);

            if (VERBOSE)
                Timber.v("goting to handleFrameAvailable:filter.onDrawFrame done :" + filter);
        }

        long endDraw = System.nanoTime() / 1000_000;
        Timber.d("draw consume: %d", endDraw - startDraw);

        if (VERBOSE) Timber.v("lockTest handleFrameAvailable before setPresentationTime");

        if (USE_OFF_SCREEN) {
            mOffscreenSurface.setPresentationTime(timestampNanos);
            if (VERBOSE) Timber.v("lockTest handleFrameAvailable after setPresentationTime");
            mOffscreenSurface.swapBuffers();
        } else {
            // FIXME: 30/11/2016 blocked here
            mInputWindowSurface.setPresentationTime(timestampNanos);
            if (VERBOSE) Timber.v("lockTest handleFrameAvailable after setPresentationTime");
            mInputWindowSurface.swapBuffers();
        }

        if (VERBOSE) Timber.v("lockTest logframeNum handleFrameAvailable after swapBuffers");

        checkAndReadFrame();
    }


    private void checkAndReadFrame() {
        if (!hasFrameToRead.getAndSet(false)) {
            Timber.w("logframeNum , hasFrameToRead = false,can not read frame");
            return;
        } else {
            Timber.v("logframeNum , hasFrameToRead = true, to read frame");
        }

        if (DUMP_2_IMAGEVIEW) {
            MyUtil.tryReadPixels(mOutputStartX, mOutputStartY, mOutputWidth, mOutputHeight);
        } else {
            outputFrame();
        }
    }

    /**
     * Handles a request to stop encoding.
     */
    private void handleStopRecording() {
        Log.d(TAG, "handleStopRecording");
        releaseEncoder();
    }

    /**
     * Sets the texture name that SurfaceTexture will use when frames are received.
     */
    private void handleSetTexture(int id) {
        //Log.d(TAG, "handleSetTexture " + id);
        mTextureId = id;
    }


    private void createBeautyFilter() {
        filter = new MyBeautyFilter();
        filter.init();
        filter.onInputSizeChanged(mPreviewWidth, mPreviewHeight);
        filter.setViewportParam(0, 0, mPreviewWidth, mPreviewHeight);
    }


    /**
     * cameraInputFilter只创建一次，而且是必创建的
     */
    private void createCameraInputer() {
        if (mPreviewWidth == 0 || mOutputWidth == 0) {
            throw new IllegalStateException("can not createCameraInputer with invalid width and height");
        }
        cameraInputFilter = new MagicCameraInputFilter();
        cameraInputFilter.init();
        cameraInputFilter.initCameraFrameBuffer(mPreviewWidth, mPreviewHeight);
        cameraInputFilter.onInputSizeChanged(mPreviewWidth, mPreviewHeight);
    }

    private void releaseCameraInputer() {
        if (cameraInputFilter != null) {
            cameraInputFilter.destroyFramebuffers();
            cameraInputFilter.destroy();
        }
    }

    private void releaseFilterIfNeeded() {
        if (filter != null) {
            filter.destroy();
            Timber.d("filter.destroy start:" + filter);
            filter = null;
            Timber.d("filter.destroy end:" + filter);
        }
    }

    private static AtomicBoolean hasFrameToRead = new AtomicBoolean(false);

    private void releaseEncoder() {
        if (USE_OFF_SCREEN) {
            if (mOffscreenSurface != null) {
                mOffscreenSurface.release();
                mOffscreenSurface = null;
            }
        } else {
            if (mInputWindowSurface != null) {
                mInputWindowSurface.release();
                mInputWindowSurface = null;
            }
        }
        releaseCameraInputer();
        if (mEglCore != null) {
            mEglCore.release();
            mEglCore = null;
        }
        releaseFilterIfNeeded();
    }


    // previewXXX是输入的宽高
    private int mPreviewWidth = -1;
    private int mPreviewHeight = -1;
    // mOutputXXX是最后输出的宽高
    public int mOutputWidth = -1;
    public int mOutputHeight = -1;

    public int mOutputStartX;
    public int mOutputStartY;

    private long mLastOutputEndTsMs = 0;

    private void outputFrame() {
        initBufferIfNeeded(mOutputWidth, mOutputHeight);

        long newFrameMs = System.nanoTime() / 1000_000;

        long diff = newFrameMs - mLastOutputEndTsMs;
        if (mDebugger != null && mDebugger.mLogDumpProfile) {
            if (diff < 10) {
                Timber.e("lockTest outputFrame ,diff:%d ms 卡帧了 **************** ", diff);
            } else {
                Timber.d("lockTest outputFrame ,diff:%d ms 没卡帧=============== ", diff);
            }
        }

        if (mDebugger.mDebugShouldOutputFrame) {
            GLES20.glFinish();
            if (OUTPUT_RATOTE_90) {
                mRgbaByteBuffer.position(0);
                GLES20.glReadPixels(mOutputStartX, mOutputStartY, mOutputWidth, mOutputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mRgbaByteBuffer);
            } else {
                mRgbaIntBuffer.position(0);
                GLES20.glReadPixels(mOutputStartX, mOutputStartY, mOutputWidth, mOutputHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mRgbaIntBuffer);
            }
        }


        long afterReadPixelMs = System.nanoTime() / 1000_000;
        if (mDebugger != null && mDebugger.mLogDumpProfile) {
            Timber.d("outputFrame2， glReadPixels, consume:%d ms", (afterReadPixelMs - newFrameMs));
        }

        if (mDebugger.mDebugShouldOutputFrame) {
            OnBeatuyOutputListener previewOutput = mOnBeatuyOutputListener;
            if (previewOutput != null) {
                if (OUTPUT_RATOTE_90) {
                    RgbYuvConverter.rgba2yuvRotateC90(mOutputWidth, mOutputHeight, mRgbaByteBuffer.array(), mYuvBuffer.array());
                    previewOutput.onBeautyFrameData(mYuvBuffer.array(), mOutputHeight, mOutputWidth, System.currentTimeMillis());
                } else {
                    RgbYuvConverter.rgba2yuv(mOutputWidth, mOutputHeight, mRgbaIntBuffer.array(), mYuvBuffer.array());
                    previewOutput.onBeautyFrameData(mYuvBuffer.array(), mOutputWidth, mOutputHeight, System.currentTimeMillis());
                }
            }
        }

        long afterConvertYuvAndOutputMs = System.nanoTime() / 1000_000;
        if (mDebugger.mLogDumpProfile) {
            Timber.d("outputFrame3, ConvertYuvAndOutputMs, consume:%d ms", afterConvertYuvAndOutputMs - afterReadPixelMs);
        }
        mLastOutputEndTsMs = afterConvertYuvAndOutputMs;
    }

    private ByteBuffer mYuvBuffer;
    private IntBuffer mRgbaIntBuffer;
    private ByteBuffer mRgbaByteBuffer;


    private void initBufferIfNeeded(int width, int height) {
        if (OUTPUT_RATOTE_90) {
            if (mRgbaByteBuffer == null || mRgbaByteBuffer.capacity() < width * height * 4) {
                mRgbaByteBuffer = ByteBuffer.allocateDirect(width * height * 4);
            }
        } else {
            if (mRgbaIntBuffer == null || mRgbaIntBuffer.capacity() < width * height * 4) {
                mRgbaIntBuffer = IntBuffer.allocate(width * height);
            }
        }

        if (mYuvBuffer == null || mYuvBuffer.capacity() < width * height * 3 / 2) {
            mYuvBuffer = ByteBuffer.allocateDirect(width * height * 3 / 2).order(ByteOrder.nativeOrder());
        }
    }
}
