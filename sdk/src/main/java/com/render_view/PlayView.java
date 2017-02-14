package com.render_view;

import android.opengl.GLSurfaceView;

import timber.log.Timber;

public class PlayView {

    public static GLSurfaceView mGlSurfaceView;

    private YUVRender mYUVRender;

    private volatile boolean ready = false;

    public boolean isReady() {
        return ready;
    }

    public PlayView(GLSurfaceView glSurfaceView) {
        mGlSurfaceView = glSurfaceView;
        mGlSurfaceView.setEGLContextClientVersion(2);
        mYUVRender = new YUVRender();
        mGlSurfaceView.setRenderer(mYUVRender);
        mGlSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        ready = false;
    }

    public void UpdateScreen(byte[] Y, byte[] U, byte[] V) {
        try {
            if (!mYUVRender.IsWaitting()) {
                mYUVRender.setYUVData(Y, U, V);
                mGlSurfaceView.requestRender();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void UpdateScreenAll(byte[] YUV) {
        Timber.d("UpdateScreenAll");
        try {
            if (!mYUVRender.IsWaitting()) {
                mYUVRender.setYUVDataAll(YUV);
                mGlSurfaceView.requestRender();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void init(int aVideoW, int aVideoH) {
        ready = false;
        ready = mYUVRender.SetupRender(aVideoW, aVideoH);
    }
}
