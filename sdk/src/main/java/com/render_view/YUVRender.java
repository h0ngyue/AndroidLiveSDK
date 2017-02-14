package com.render_view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.util.Log;

import com.yolo.beautycamera.beauty_preview.utils.OpenGlUtils;
import com.yolo.beautycamera.beauty_preview.utils.TextureRotationUtil;
import com.yolo.livesdk.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import timber.log.Timber;

import static com.yolo.beautycamera.beauty_preview.utils.MyUtil.addDistance;

public class YUVRender implements GLSurfaceView.Renderer {

    public String TAG = "YUVRender";

    public int[] _textureU = null;

    public int[] _textureV = null;

    public int[] _textureY = null;

    private FloatBuffer mGLTextureBuffer;

    private ByteBuffer mDataU;

    private ByteBuffer mDataV;

    private ByteBuffer mDataY;

    private int mProgram;

    private FloatBuffer mGLCubeBuffer;

    public int mVideoWidth = 0;

    public int mVideoHeight = 0;

    private float mVideoWHRatio;

    public int mVideoWidthHalf = 0;

    public int mVideoHeightHalf = 0;

    private int mGLAttribPosition;

    private int mGlAttribTexCoordIn;

    public int mYSize, mUVSize;

    public int mSurfaceWidth, mSurfaceHeight;

    private int mExpectYuvLength;

    //  /**
    //   * FBO顶点缓冲
    //   */
    //   public int[] mFbo;

    /**
     * 是否正在渲染
     */
    public boolean mIsRunning = false;

    /**
     * 是否需要渲染
     */
    public boolean mIsNeedRender = false;

    public int mSamplerY;

    public int mSamplerU;

    public int mSamplerV;

    public boolean mIsInit = false;

    public YUVRender() {
        //Log.d(TAG, "new YUVRender");
        mIsRunning = false;
        mIsNeedRender = false;
        mIsInit = false;

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
    }

    /**
     * 设置渲染器的高宽
     */
    public boolean SetupRender(int aW, int aH) {
        mIsInit = false;
        // synchronized (mDataY) {
        mIsNeedRender = false;

        mVideoWidth = aW;
        mVideoHeight = aH;
        mVideoWHRatio = mVideoWidth * 1.0f / mVideoHeight;

        mExpectYuvLength = mVideoWidth * mVideoHeight * 3 / 2;

        mVideoWidthHalf = (mVideoWidth >> 1);
        mVideoHeightHalf = (mVideoHeight >> 1);
        mYSize = mVideoHeight * mVideoWidth;
        mUVSize = (mVideoHeight * mVideoWidth >> 2);

        mDataY = null;
        mDataU = null;
        mDataV = null;
        mDataY = ByteBuffer.allocateDirect(mYSize).order(ByteOrder.nativeOrder());
        mDataY.position(0);
        mDataU = ByteBuffer.allocateDirect(mUVSize).order(ByteOrder.nativeOrder());
        mDataU.position(0);
        mDataV = ByteBuffer.allocateDirect(mUVSize).order(ByteOrder.nativeOrder());
        mDataV.position(0);
        Log.e(TAG, "Render初始化完成");
        return true;
        //}

    }

    /**
     * 设置YUV数据
     */
    public void setYUVData(byte[] Y, byte[] U, byte[] V) {
        if (mDataY == null || (mDataY.capacity() < mYSize)) {
            return;
        }
        if (LockStore.getLock("YUVDisplay")) {
            // synchronized (mDataY) {
            //System.out.println("get YUV data");
            mIsNeedRender = true;
            // LogUtils.e(true, "setYUVData==========================>");
            mDataY.position(0);

            mDataY.put(Y, 0, mYSize).position(0);

            mDataU.position(0);
            mDataU.put(U, 0, mUVSize).position(0);

            mDataV.position(0);
            mDataV.put(V, 0, mUVSize).position(0);

            LockStore.releaseLock("YUVDisplay");
        }
        // LogUtils.e(true, "setYUVData==========================over");
    }

    public boolean setYUVDataAll(byte[] YUV) {
        if (mDataY == null || (mDataY.capacity() < mYSize)) {
            return false;
        }
        if (YUV.length != mExpectYuvLength) {
            return false;
        }

        // if (LockStore.getLock("YUVDisplay")) {
        //synchronized (mDataY) {
        //System.out.println("get YUV data");
        mIsNeedRender = true;
        // LogUtils.e(true, "setYUVData==========================>");
        mDataY.position(0);

        mDataY.put(YUV, 0, mYSize).position(0);

        mDataU.position(0);
        mDataU.put(YUV, mYSize, mUVSize).position(0);

        mDataV.position(0);
        mDataV.put(YUV, mYSize + mUVSize, mUVSize).position(0);
        LockStore.releaseLock("YUVDisplay");
        //}
        // LogUtils.e(true, "setYUVData==========================over");
        return true;
    }

    /**
     * 是否等待
     */
    public boolean IsWaitting() {
        return (!mIsInit) || (mIsNeedRender && mIsRunning);
    }

    private void checkGlError(String paramString) {
        int i = GLES20.glGetError();
        if (i != 0) {
//            Log.e(TAG, paramString + ": glError " + i);
            //throw new RuntimeException(paramString + ": glError " + i);
            Timber.e("%s:: glError 0x%x", paramString, i);
        }
    }

    @SuppressLint("NewApi")
    public void onDrawFrame(GL10 glUnused) {
        if (Build.VERSION.SDK_INT < 9) {
            return;
        }
        try {
            if (mDataY == null) {
                return;
            }

            if (mIsNeedRender) {
                mIsRunning = true;

                GLES20.glUseProgram(this.mProgram);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this._textureY[0]);
                GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, this.mVideoWidth, this.mVideoHeight,
                        GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, mDataY);
                GLES20.glUniform1i(mSamplerY, 0);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this._textureU[0]);
                GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, this.mVideoWidthHalf, this.mVideoHeightHalf,
                        GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, mDataU);
                GLES20.glUniform1i(mSamplerU, 1);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this._textureV[0]);
                GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, this.mVideoWidthHalf, this.mVideoHeightHalf,
                        GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, mDataV);
                GLES20.glUniform1i(mSamplerV, 2);
                checkGlError("glYUV");


                mGLCubeBuffer.position(0);
                GLES20.glVertexAttribPointer(mGLAttribPosition, 2, GLES20.GL_FLOAT, false, 0, mGLCubeBuffer);
                GLES20.glEnableVertexAttribArray(mGLAttribPosition);
                mGLTextureBuffer.position(0);
                GLES20.glVertexAttribPointer(mGlAttribTexCoordIn, 2, GLES20.GL_FLOAT, false, 0, mGLTextureBuffer);
                GLES20.glEnableVertexAttribArray(mGlAttribTexCoordIn);
                checkGlError("glCubeBuffers");

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
                checkGlError("glDrawArrays");

                GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

                mIsNeedRender = false;
                mIsRunning = false;
                LockStore.releaseLock("YUVDisplay");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置宽高
     */
    public void initYuvTexture() {
        _textureY = new int[1];
        _textureU = new int[1];
        _textureV = new int[1];
        genTexture(_textureY, GLES20.GL_TEXTURE0, mVideoWidth, mVideoHeight);
        genTexture(_textureU, GLES20.GL_TEXTURE1, mVideoWidthHalf, mVideoHeightHalf);
        genTexture(_textureV, GLES20.GL_TEXTURE2, mVideoWidthHalf, mVideoHeightHalf);

        //  mFbo  = new int[2];
        //mFbo[0]= createFrameBuffer(mWidth, mHeight,_textureY[0]);
        // mFbo[1]= createFrameBuffer((mWidth >>1) , (mHeight>>1),_textureU[0]);
        // mFbo[2]= createFrameBuffer((mWidth >>1) , (mHeight>>1),_textureV[0]);
    }

    private void genTexture(int[] texture, int texture_obj_id, int width, int height) {
        GLES20.glGenTextures(1, texture, 0);
        GLES20.glActiveTexture(texture_obj_id);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture[0]);

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE, width, height, 0,
                GLES20.GL_LUMINANCE, GLES20.GL_UNSIGNED_BYTE, null);

//        checkGlError("1");
//        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
//                GLES20.GL_LINEAR);
//        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
//                GLES20.GL_LINEAR);
//        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20
//                .GL_CLAMP_TO_EDGE);
//        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20
//                .GL_CLAMP_TO_EDGE);

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
        Timber.d("render onSurfaceCreated");
        try {
            initGLProgram();

            initYuvTexture();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            mIsInit = true;
        }
    }


    private void initGLProgram() {
        mProgram = OpenGlUtils.loadProgram(OpenGlUtils.readShaderFromRawResource(R.raw.yuv_renderer_vert), OpenGlUtils.readShaderFromRawResource(R.raw.yuv_renderer_frag));
        if (mProgram == 0) {
            return;
        }
        this.mGLAttribPosition = GLES20.glGetAttribLocation(this.mProgram, "aPosition");
        checkGlError("glGetAttribLocation aPosition");
        if (this.mGLAttribPosition == -1) {
            throw new RuntimeException("Could not get attrib location for aPosition");
        }
        this.mGlAttribTexCoordIn = GLES20.glGetAttribLocation(this.mProgram, "TexCoordIn");
        checkGlError("glGetAttribLocation TexCoordIn");
        if (this.mGlAttribTexCoordIn == -1) {
            throw new RuntimeException("Could not get attrib location for aTextureCoord");
        }

        mSamplerY = GLES20.glGetUniformLocation(this.mProgram, "SamplerY");
        mSamplerU = GLES20.glGetUniformLocation(this.mProgram, "SamplerU");
        mSamplerV = GLES20.glGetUniformLocation(this.mProgram, "SamplerV");
    }

    public void onSurfaceChanged(GL10 glUnused, int width, int height) {
        mSurfaceWidth = width;
        mSurfaceHeight = height;


        GLES20.glClearColor(0.3F, 0.3F, 0.3F, 0F);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);
        adjustImageScaling(0, false, false, mGLTextureBuffer, mGLCubeBuffer);

//        tryLoadFirstImage();
    }


    public static Context testContext;
    int mBmTextureId = OpenGlUtils.NO_TEXTURE;

    private void tryLoadFirstImage() {
        BitmapFactory.Options op = new BitmapFactory.Options();
        op.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bm = BitmapFactory.decodeResource(testContext.getResources(), R.drawable.map, op);
        ByteBuffer buffer = ByteBuffer.allocateDirect(bm.getByteCount()).order(ByteOrder.nativeOrder());

        int pos = buffer.position();
        bm.copyPixelsToBuffer(buffer);
        pos = buffer.position();
//        mBmTextureId = OpenGlUtils.loadTexture(bm, mBmTextureId);
        mBmTextureId = OpenGlUtils.loadTexture(buffer, bm.getWidth(), bm.getHeight(), mBmTextureId);
    }

    private void adjustImageScaling(int rotation, boolean flipHorizontal, boolean flipVertical, FloatBuffer texBuffer, FloatBuffer cubeBuffer) {

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

//    /**
//     * 创建纹理
//     */
//    @SuppressWarnings("unused")
//    private int createTargetTexture(GL10 gl, int width, int height) {
//        int texture;
//        int[] textures = new int[1];
//        gl.glGenTextures(1, textures, 0);
//        texture = textures[0];
//        gl.glBindTexture(GL10.GL_TEXTURE_2D, texture);
//        gl.glTexImage2D(GL10.GL_TEXTURE_2D, 0, GL10.GL_RGBA, width, height, 0, GL10.GL_RGBA,
//                GL10.GL_UNSIGNED_BYTE, null);
//        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_NEAREST);
//        gl.glTexParameterf(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
//        gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_S, GL10.GL_REPEAT);
//        gl.glTexParameterx(GL10.GL_TEXTURE_2D, GL10.GL_TEXTURE_WRAP_T, GL10.GL_REPEAT);
//        return texture;
//    }

//    /**
//     * 创建指定的帧缓冲
//     *
//     * @param targetTextureId 指定的纹�?
//     */
//    @SuppressWarnings("unused")
//    private int createFrameBuffer(int width, int height, int targetTextureId) {
//
//        int framebuffer;
//        int[] framebuffers = new int[1];
//        GLES20.glGenFramebuffers(1, framebuffers, 0);
//        framebuffer = framebuffers[0];
//        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
//        checkGlError("glBindFramebuffer");
//        int depthbuffer;
//        int[] renderbuffers = new int[1];
//        GLES20.glGenRenderbuffers(1, renderbuffers, 0);
//        depthbuffer = renderbuffers[0];
//
//        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, depthbuffer);
//        checkGlError("glBindRenderbuffer");
//        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, width,
//                height);
//        checkGlError("glBindRenderbuffer");
//        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
//                GLES20.GL_RENDERBUFFER, depthbuffer);
//        checkGlError("glFramebufferRenderbuffer");
//        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
//                GLES20.GL_TEXTURE_2D, targetTextureId, 0);
//        checkGlError("glFramebufferTexture2D");
//        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
//        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
//            throw new RuntimeException(
//                    "Framebuffer is not complete: " + Integer.toHexString(status));
//        }
//        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
//        return framebuffer;
//    }

    /**
     * 判断是不否支持FBO
     */
    @SuppressWarnings("unused")
    private boolean checkIfContextSupportsFrameBufferObject() {
        return checkIfContextSupportsExtension("GL_OES_framebuffer_object");
    }

    /**
     * This is not the fastest way to check for an extension, but fine if
     * we are only checking for a few extensions each time a context is created.
     *
     * @return true if the extension is present in the current context.
     */
    private boolean checkIfContextSupportsExtension(String extension) {
        String extensions = " " + GLES20.glGetString(GL10.GL_EXTENSIONS) + " ";
        // The extensions string is padded with spaces between extensions, but not
        // necessarily at the beginning or end. For simplicity, add spaces at the
        // beginning and end of the extensions string and the extension string.
        // This means we can avoid special-case checks for the first or last
        // extension, as well as avoid special-case checks when an extension name
        // is the same as the first part of another extension name.
        return extensions.indexOf(" " + extension + " ") >= 0;
    }

}