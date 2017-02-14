package com.yolo.livesdk.widget.publish_3;

import com.yolo.livesdk.YoloLiveNative;
import com.yolo.livesdk.widget.publish_controller.BeautyCameraPreviewOutput;

import java.nio.ByteBuffer;
import java.util.concurrent.LinkedBlockingQueue;

import timber.log.Timber;

/**
 * Created by shuailongcheng on 12/12/2016.
 */

public class BeautyPublishOutput {
    private static final int MAX_PENDING_MSG_COUNT = 10;

    private volatile boolean mRunning;

    private BeautyCameraPreviewOutput outputListener;

    public FpsController mFpsController;

    private boolean mIsCurrentFallbackStrategy;

    private boolean mIsFrontCamera;
    private boolean mMirror;

    private FrameMessagePool mFrameMessagePool;

    private LinkedBlockingQueue<FrameMessage> mPendingMessageQueue;

    private Thread mThread;

    public boolean canOutput(long curMs) {
        boolean can = !isBusy() && mRunning && mFpsController.canTake(curMs);
//        if (!can) {
//            Timber.e("canOutput = false");
//        }
        return can;
    }

    public int getCurPushFps() {
        return mFpsController.getFpsRecorder().getCurFps();
    }

    public void useFallbackFpsController(boolean use) {
        if (mIsCurrentFallbackStrategy == use) {
            return;
        }
        mIsCurrentFallbackStrategy = use;
        mFpsController.useFallbackSettings();
    }

    public BeautyPublishOutput() {
        mFpsController = new FpsController();
        mIsCurrentFallbackStrategy = false;

        mPendingMessageQueue = new LinkedBlockingQueue<>(MAX_PENDING_MSG_COUNT);
        mFrameMessagePool = new FrameMessagePool();
        mThread = new Thread("BeautyOutputNew") {
            @Override
            public void run() {
                Timber.d("OutputThread mRunning = true");
                mRunning = true;

                try {
                    loop();
                } catch (Exception ex) {
                    ex.printStackTrace();
                    Timber.e("BeautyPublishOutput loop exception ,is exiting:" + ex);
                }

                mRunning = false;
                mFrameMessagePool.release();
                Timber.d("OutputThread exiting");
            }
        };
    }

    public void startThread() {
        mThread.start();
    }

    private void loop() {
        while (mRunning) {
            FrameMessage frame = null;
            try {
                frame = mPendingMessageQueue.take();
            } catch (InterruptedException ex) {
                ex.printStackTrace();
                Timber.w("mPendingMessageQueue.take() exception: " + ex);
                continue;
            }
            if (frame == null) {
                Timber.e("frame == null");
                continue;
            }
            handleNewFrame(frame);
        }
    }

    public FrameMessage obtain(int mOutputWidth, int mOutputHeight, long curMs) {
        return mFrameMessagePool.obtain(mOutputWidth, mOutputHeight, curMs);
    }

    public boolean isBusy() {
        int cur = mPendingMessageQueue.size();
        boolean ret = cur > 2;
        if (ret) {
            Timber.e("isBusys, mPendingMessageQueue.size:%d", cur);
        } else {
            Timber.v("is not Busy mPendingMessageQueue.size:%d", cur);
        }
        return ret;
    }

    public void newFrame(FrameMessage frame) {
        try {
            mPendingMessageQueue.put(frame);
        } catch (InterruptedException e) {
            Timber.e("newFrame exception : " + e);
            e.printStackTrace();
        }
    }

    public void quit() {
        if (!mThread.isInterrupted()) {
            mThread.interrupt();
        }
    }

    public void setOutputListener(BeautyCameraPreviewOutput outputListener) {
        this.outputListener = outputListener;
    }

    public void setFrontCamera(boolean isFrontCamera) {
        this.mIsFrontCamera = isFrontCamera;
    }

    public void setMirror(boolean mirror) {
        this.mMirror = mirror;
    }

    public boolean isMirror() {
        return mMirror;
    }


    private void handleNewFrame(FrameMessage frame) {
        BeautyCameraPreviewOutput output = outputListener;
        if (output == null) {
            return;
        }

        ByteBuffer yuvBuf = ByteBuffer.allocateDirect(frame.mWidth * frame.mHeight * 3 / 2);
        if (mIsFrontCamera && !mMirror) {
            YoloLiveNative.rgba2yuvRotate180(frame.mWidth, frame.mHeight, frame.mRgbBuffer.array(), yuvBuf.array());
        } else {
            YoloLiveNative.rgba2yuvRotate180Flip(frame.mWidth, frame.mHeight, frame.mRgbBuffer.array(), yuvBuf.array());
        }

        output.onFrameData(yuvBuf.array(), frame.mWidth, frame.mHeight, frame.mTimestampMs);
        mFrameMessagePool.recycle(frame);
    }

    public void setCameraValid(boolean cameraValid) {
//        mCameraValid = cameraValid;
    }
}
