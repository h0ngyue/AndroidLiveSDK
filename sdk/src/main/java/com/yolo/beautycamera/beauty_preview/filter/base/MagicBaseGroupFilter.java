package com.yolo.beautycamera.beauty_preview.filter.base;


import android.opengl.GLES20;

import com.yolo.beautycamera.beauty_preview.filter.base.gpuimage.MyGPUImageFilter;
import com.yolo.beautycamera.beauty_preview.utils.OpenGlUtils;

import java.nio.FloatBuffer;
import java.util.List;


public class MagicBaseGroupFilter extends MyGPUImageFilter {
    protected static int[] frameBuffers = null;
    protected static int[] frameBufferTextures = null;
    private int frameWidth = -1;
    private int frameHeight = -1;
    protected List<MyGPUImageFilter> filters;

    private int viewportStartX, viewportStartY, vpWidth, vpHeight;

    public void setViewportParam(int x, int y, int vpWidth, int vpHeight) {
        viewportStartX = x;
        viewportStartY = y;
        this.vpWidth = vpWidth;
        this.vpHeight = vpHeight;
    }

    public MagicBaseGroupFilter(List<MyGPUImageFilter> filters) {
        this.filters = filters;
    }

    @Override
    public void onDestroy() {
        for (MyGPUImageFilter filter : filters) {
            filter.destroy();
        }
    }

    @Override
    public void onInit() {
        for (MyGPUImageFilter filter : filters) {
            filter.onInit();
        }
    }

    @Override
    public void onInitialized() {
        for (MyGPUImageFilter filter : filters) {
            filter.onInitialized();
        }
    }

    @Override
    public void onInputSizeChanged(final int width, final int height) {
        super.onInputSizeChanged(width, height);
        int size = filters.size();
        for (int i = 0; i < size; i++) {
            filters.get(i).onInputSizeChanged(width, height);
        }
        if (frameBuffers != null && (frameWidth != width || frameHeight != height || frameBuffers.length != size - 1)) {
            destroyFramebuffers();
            frameWidth = width;
            frameHeight = height;
        }
        if (frameBuffers == null) {
            frameBuffers = new int[size - 1];
            frameBufferTextures = new int[size - 1];

            for (int i = 0; i < size - 1; i++) {
                GLES20.glGenFramebuffers(1, frameBuffers, i);

                GLES20.glGenTextures(1, frameBufferTextures, i);
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, frameBufferTextures[i]);
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[i]);
                GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                        GLES20.GL_TEXTURE_2D, frameBufferTextures[i], 0);

                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            }
        }
    }

    @Override
    public void onDraw(final int textureId, final FloatBuffer cubeBuffer,
                      final FloatBuffer textureBuffer) {
        if (frameBuffers == null || frameBufferTextures == null) {
//            return OpenGlUtils.NOT_INIT;
            return;
        }

        int size = filters.size();
        int previousTexture = textureId;
        for (int i = 0; i < size; i++) {
            MyGPUImageFilter filter = filters.get(i);
            boolean isNotLast = i < size - 1;
            if (isNotLast) {
                GLES20.glViewport(0, 0, mIntputWidth, mIntputHeight);
//                GLES20.glViewport(viewportStartX, viewportStartY, mIntputWidth, mIntputHeight);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[i]);
                GLES20.glClearColor(0, 0, 0, 0);
                filter.onDraw(previousTexture, mGLCubeBuffer, mGLTextureBuffer);
                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
                previousTexture = frameBufferTextures[i];
            } else {
                GLES20.glViewport(viewportStartX, viewportStartY, vpWidth, vpHeight);
                filter.onDraw(previousTexture, cubeBuffer, textureBuffer);
            }
        }

//        Timber.d("onDrawFrame, viewportStartX:%d, viewportStartY:%d, vpWidth:%d, vpHeight:%d,mIntputWidth:%d, mIntputHeight:%d, mOutputWidth:%d, mOutputHeight:%d",
//                viewportStartX, viewportStartY, vpWidth, vpHeight, mIntputWidth, mIntputHeight, mOutputWidth, mOutputHeight);

//        return OpenGlUtils.ON_DRAWN;
    }



//    @Override
//    public int onDrawFrame(final int textureId) {
//        if (frameBuffers == null || frameBufferTextures == null) {
//            return OpenGlUtils.NOT_INIT;
//        }
//        int size = filters.size();
//        int previousTexture = textureId;
//        for (int i = 0; i < size; i++) {
//            MyGPUImageFilter filter = filters.get(i);
//            boolean isNotLast = i < size - 1;
//            if (isNotLast) {
//                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frameBuffers[i]);
//                GLES20.glClearColor(0, 0, 0, 0);
//                filter.onDraw(previousTexture, mGLCubeBuffer, mGLTextureBuffer);
//                GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
//                previousTexture = frameBufferTextures[i];
//            } else {
//                filter.onDraw(previousTexture, mGLCubeBuffer, mGLTextureBuffer);
//            }
//        }
//        return OpenGlUtils.ON_DRAWN;
//    }

    private void destroyFramebuffers() {
        if (frameBufferTextures != null) {
            GLES20.glDeleteTextures(frameBufferTextures.length, frameBufferTextures, 0);
            frameBufferTextures = null;
        }
        if (frameBuffers != null) {
            GLES20.glDeleteFramebuffers(frameBuffers.length, frameBuffers, 0);
            frameBuffers = null;
        }
    }

    public int getSize() {
        return filters.size();
    }
}
