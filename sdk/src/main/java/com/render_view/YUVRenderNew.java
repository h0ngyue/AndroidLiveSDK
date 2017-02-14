package com.render_view;

import android.annotation.SuppressLint;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;

import com.yolo.beautycamera.beauty_preview.utils.OpenGlUtils;
import com.yolo.beautycamera.beauty_preview.utils.TextureRotationUtil;
import com.yolo.livesdk.R;
import com.yolo.livesdk.widget.publish_3.FrameMessage;
import com.yolo.livesdk.widget.publish_3.FrameMessagePool;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.LinkedList;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import timber.log.Timber;

import static com.yolo.beautycamera.beauty_preview.utils.MyUtil.addDistance;

public class YUVRenderNew implements GLSurfaceView.Renderer {
    private static final boolean VERBOSE = false;

    public String TAG = "YUVRender";

    private int[] _textureU = null;

    private int[] _textureV = null;

    private int[] _textureY = null;

    private FloatBuffer mGLTextureBuffer;

    private int mProgram;

    private FloatBuffer mGLCubeBuffer;

    public int mVideoWidth = 0;

    public int mVideoHeight = 0;

    private float mVideoWHRatio;

    private int mVideoWidthHalf = 0;

    private int mVideoHeightHalf = 0;

    private int mGLAttribPosition;

    private int mGlAttribTexCoordIn;

    private int mYSize, mUVSize;

    private int mSurfaceWidth, mSurfaceHeight;

    private int mExpectYuvLength;

    private int mSamplerY;

    private int mSamplerU;

    private int mSamplerV;

    private FrameMessagePool mPoolY, mPoolUV;

    private FrameMessage mLastBufferY, mLastBufferU, mLastBufferV;

    private final LinkedList<Runnable> mRunOnDraw = new LinkedList<>();

    GLSurfaceView mGLSurfaceView;

    public YUVRenderNew() {
        //Log.d(TAG, "new YUVRender");
        float[] cube = TextureRotationUtil.CUBE;
        this.mGLCubeBuffer = ByteBuffer.allocateDirect(4 * cube.length)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        this.mGLCubeBuffer.put(cube).position(0);

        float[] texture = TextureRotationUtil.TEXTURE_NO_ROTATION;
        this.mGLTextureBuffer = ByteBuffer.allocateDirect(4 * texture.length)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        this.mGLTextureBuffer.put(texture).position(0);

        mPoolY = new FrameMessagePool();
        mPoolUV = new FrameMessagePool();
    }


    public void setGLSurfaceView(GLSurfaceView glSurfaceView) {
        this.mGLSurfaceView = glSurfaceView;
    }

    public void initGLInitTask(int w, int h) {
//        Timber.e("testRender , YUVRenderNew.init(w, h) w:%d, h:%d  ****************************************", w, h);
        needInit = () -> initGL(w, h);
//        Timber.e("testRender , set needInit OVER!   w:%d, h:%d ****************************************", w, h);

        if (mGLSurfaceView != null) {
            mGLSurfaceView.requestRender();
        }
    }

    volatile boolean logSetYUVDataAll = true;

    public void setYUVDataAll(byte[] YUV) {
        if (mYSize == 0 || mGLSurfaceView == null) {
            return;
        }

        if (logSetYUVDataAll) {
            if (VERBOSE)
                Timber.d("testRender UpdateScreenAll, mVideoWidth:%d, mVideoHeight:%d, mYSize:%d, mUVSize:%d, desireYUVLength:%d,YUV.length:%d",
                        mVideoWidth, mVideoHeight, mYSize, mUVSize, mVideoWidth * mVideoHeight * 3 / 2, YUV.length);
            logSetYUVDataAll = false;
        }

        if (isBusy()) {
//            Timber.w("isBusy");
            return;
        }

        FrameMessage frameY = mPoolY.obtain(mYSize);
        frameY.getBuffer().put(YUV, 0, mYSize).position(0);
        FrameMessage frameU = mPoolUV.obtain(mYSize);
        frameU.getBuffer().put(YUV, mYSize, mUVSize).position(0);
        FrameMessage frameV = mPoolUV.obtain(mYSize);
        frameV.getBuffer().put(YUV, mYSize + mUVSize, mUVSize).position(0);

        runOnDraw(() -> {
            if (YUV.length != mExpectYuvLength) {
                Timber.e("testRender YUV.length != mExpectYuvLength");
                return;
            }

            drawYUV(frameY.getBuffer(), frameU.getBuffer(), frameV.getBuffer());

            setLastFrameBuffers(frameY, frameU, frameV);
        });

        if (mGLSurfaceView != null) {
            mGLSurfaceView.requestRender();
        }
    }

    private void clearLastFrameBuffers() {
        mPoolY.recycle(mLastBufferY);
        mPoolUV.recycle(mLastBufferU);
        mPoolUV.recycle(mLastBufferV);

        mLastBufferY = null;
        mLastBufferU = null;
        mLastBufferV = null;
    }

    private void setLastFrameBuffers(FrameMessage y, FrameMessage u, FrameMessage v) {
        mPoolY.recycle(mLastBufferY);
        mPoolUV.recycle(mLastBufferU);
        mPoolUV.recycle(mLastBufferV);

        mLastBufferY = y;
        mLastBufferU = u;
        mLastBufferV = v;
    }

    private void checkGlError(String paramString) {
        int i = GLES20.glGetError();
        if (i != 0) {
            Timber.e("YUVRenderNew， %s:: glError 0x%x", paramString, i);
        }
    }


    public volatile Runnable needInit;

    @SuppressLint("NewApi")
    public void onDrawFrame(GL10 glUnused) {
        if (needInit != null) {
            needInit.run();
            needInit = null;

            clearPreviousDrawTasks();
            return;
        }
        runPendingOnDrawTasks();
    }


    private void initGL(int w, int h) {
        if (mVideoWidth == w && mVideoHeight == h) {
            if (VERBOSE)
                Timber.e("testRender mVideoWidth == w && mVideoHeight == h return ?!?!  mVideoWidth:%d, mVideoHeight:%d, w:%d, h:%d *************************************************** ",
                        mVideoWidth, mVideoHeight, w, h);
            return;
        }
        if (VERBOSE) Timber.e("testRender initGL 1");

        clearLastFrameBuffers();

        mVideoWidth = w;
        mVideoHeight = h;
        mVideoWHRatio = mVideoWidth * 1.0f / mVideoHeight;

        mExpectYuvLength = mVideoWidth * mVideoHeight * 3 / 2;

        mVideoWidthHalf = (mVideoWidth >> 1);
        mVideoHeightHalf = (mVideoHeight >> 1);
        mYSize = mVideoHeight * mVideoWidth;
        mUVSize = (mVideoHeight * mVideoWidth >> 2);

        logSetYUVDataAll = true;

        initYuvTexture();

        adjustImageScaling(0, mGLTextureBuffer, mGLCubeBuffer);

        if (VERBOSE)
            Timber.e("testRender initGL, mVideoWidth:%d, mVideoHeight:%d, mYSize:%d, mUVSize:%d *************************************************** ", mVideoWidth, mVideoHeight, mYSize, mUVSize);
    }


    int total;

    private void drawYUV(ByteBuffer dataY, ByteBuffer dataU, ByteBuffer dataV) {
        total++;
        if (VERBOSE) Timber.d("drawYUV , total:%d", total);
        GLES20.glUseProgram(this.mProgram);

        mGLCubeBuffer.position(0);
        GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, mGLCubeBuffer);
        GLES20.glEnableVertexAttribArray(mGLAttribPosition);
        mGLTextureBuffer.position(0);
        GLES20.glVertexAttribPointer(mGlAttribTexCoordIn, 2, GLES20.GL_FLOAT, false, 0, mGLTextureBuffer);
        GLES20.glEnableVertexAttribArray(mGlAttribTexCoordIn);
        checkGlError("glCubeBuffers");

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this._textureY[0]);
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, this.mVideoWidth, this.mVideoHeight,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, dataY);
        GLES20.glUniform1i(mSamplerY, 0);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this._textureU[0]);
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, this.mVideoWidthHalf, this.mVideoHeightHalf,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, dataU);
        GLES20.glUniform1i(mSamplerU, 1);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this._textureV[0]);
        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, this.mVideoWidthHalf, this.mVideoHeightHalf,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, dataV);
        GLES20.glUniform1i(mSamplerV, 2);
        checkGlError("glYUV");


        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        checkGlError("glDrawArrays");

        GLES20.glDisableVertexAttribArray(mGLAttribPosition);
        GLES20.glDisableVertexAttribArray(mGlAttribTexCoordIn);
    }


    private void clearPreviousDrawTasks() {
        mRunOnDraw.clear();
    }

    private boolean isBusy() {
        synchronized (mRunOnDraw) {
            return mRunOnDraw.size() >= 2;
        }
    }

    private void runPendingOnDrawTasks() {
        synchronized (mRunOnDraw) {
            while (!mRunOnDraw.isEmpty()) {
                mRunOnDraw.removeFirst().run();
            }
        }
    }

    private void runOnDraw(final Runnable runnable) {
        synchronized (mRunOnDraw) {
            mRunOnDraw.addLast(runnable);
        }
    }

    /**
     * 设置宽高
     */
    public void initYuvTexture() {
        destroyOldTextruesIfNeeded();

        _textureY = new int[1];
        _textureU = new int[1];
        _textureV = new int[1];
        genTexture(_textureY, GLES20.GL_TEXTURE0, mVideoWidth, mVideoHeight);
        genTexture(_textureU, GLES20.GL_TEXTURE1, mVideoWidthHalf, mVideoHeightHalf);
        genTexture(_textureV, GLES20.GL_TEXTURE2, mVideoWidthHalf, mVideoHeightHalf);
    }

    private void destroyOldTextruesIfNeeded() {
        if (_textureY != null) {
            GLES20.glDeleteTextures(1, _textureY, 0);
            _textureY = null;
            GLES20.glDeleteTextures(1, _textureU, 0);
            _textureU = null;
            GLES20.glDeleteTextures(1, _textureV, 0);
            _textureV = null;
        }
    }


    private void genTexture(int[] texture, int texture_obj_id, int width, int height) {
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glActiveTexture(texture_obj_id);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width, height, 0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, null);

        // 原来vhall的用到下面这一段，但是貌似用上面的也没啥区别
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);

    }

    public void onSurfaceCreated(GL10 glUnused, EGLConfig config) {
        if (VERBOSE) Timber.d("render onSurfaceCreated");
        initGLProgram();

        GLES20.glClearColor(0.118F, 0.118F, 0.118F, 0F);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
    }


    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        if (VERBOSE) Timber.d("render onSurfaceChanged, width: %d, height:%d", width, height);
        mSurfaceWidth = width;
        mSurfaceHeight = height;

        GLES20.glViewport(-7, -7, mSurfaceWidth + 14, mSurfaceHeight + 14);

        adjustImageScaling(0, mGLTextureBuffer, mGLCubeBuffer);

        // surfaceChanged后，glSurfaceView会还原黑屏，在这里还原最后一帧
        runOnDraw(() -> {
            if (mLastBufferY != null && mRunOnDraw.size() <= 1) {
                drawYUV(mLastBufferY.getBuffer(), mLastBufferU.getBuffer(), mLastBufferV.getBuffer());
            }
        });
    }


    private void initGLProgram() {
        mProgram = OpenGlUtils.loadProgram(OpenGlUtils.readShaderFromRawResource(R.raw.yuv_renderer_vert), OpenGlUtils.readShaderFromRawResource(R.raw.yuv_renderer_frag));
        if (mProgram == 0) {
            Timber.e("initGLProgram fail");
            return;
        } else {
            Timber.d("initGLProgram, mProgram:%d", mProgram);
        }
        this.mGLAttribPosition = GLES20.glGetAttribLocation(this.mProgram, "aPosition");
        checkGlError("glGetAttribLocation aPosition");
        if (this.mGLAttribPosition == -1) {
            throw new RuntimeException("Could not get attrib location for aPosition");
        }
        this.mGlAttribTexCoordIn = GLES20.glGetAttribLocation(this.mProgram, "TexCoordIn");
        checkGlError("glGetAttribLocation TexCoordIn");
        if (this.mGlAttribTexCoordIn == -1) {
            throw new RuntimeException("Could not get attrib location for TexCoordIn");
        }

        mSamplerY = GLES20.glGetUniformLocation(this.mProgram, "SamplerY");
        mSamplerU = GLES20.glGetUniformLocation(this.mProgram, "SamplerU");
        mSamplerV = GLES20.glGetUniformLocation(this.mProgram, "SamplerV");
    }

    private void adjustImageScaling(int rotation, FloatBuffer texBuffer, FloatBuffer cubeBuffer) {
        if (mVideoWidth == 0 || mSurfaceWidth == 0) {
            return;
        }

        float[] textureCords = TextureRotationUtil.TEXTURE_NO_ROTATION;

        int displayWidth, displayHeight;

        float surfaceRatio = mSurfaceWidth * 1.0f / mSurfaceHeight;

        if (surfaceRatio > mVideoWHRatio) {
            // 等宽裁内容的高
            displayWidth = mVideoWidth;
            displayHeight = (int) (displayWidth / surfaceRatio);
        } else {
            displayHeight = mVideoHeight;
            displayWidth = (int) (displayHeight * surfaceRatio);
        }

        // make textureBuffer to CENTER_CROP
        float ratioWidth = mVideoWidth * 1.0f / displayWidth;
        float ratioHeight = mVideoHeight * 1.0f / displayHeight;
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
}