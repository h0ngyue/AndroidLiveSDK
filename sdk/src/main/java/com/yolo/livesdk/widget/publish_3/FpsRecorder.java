package com.yolo.livesdk.widget.publish_3;

import timber.log.Timber;

/**
 * Created by shuailongcheng on 13/12/2016.
 */

public class FpsRecorder {
    private static final boolean VERBOSE = false;
    private int mFrameNum = -1;
    private long mFpsStartTs;
    private int mCurFps;
    private static final int FACTOR = 15;

    public FpsRecorder() {
        Timber.d("FpsRecorder");
    }

    public int recordOne() {
        int frameNum = (mFrameNum++) % FACTOR;

        if (frameNum == 0) {
            mFpsStartTs = curMs();
        } else if (frameNum == FACTOR - 1) {
            if (VERBOSE)
                Timber.w("recordOne frameNum:%d, mCurFps:%d, this: %s", frameNum, mCurFps, this);
            final long intevalMs = ((curMs() - mFpsStartTs) / FACTOR);
            mCurFps = (int) (1000 / intevalMs);
            return mCurFps;
        }

        return 0;
    }

    private long curMs() {
        return System.nanoTime() / 1000000;
    }

    public int getCurFps() {
        if (VERBOSE)
            Timber.w("getCurFps mFrameNum:%d, mCurFps:%d, this:%s", mFrameNum, mCurFps, this);
        if ((mFrameNum + 1) % FACTOR == 0) {
            return mCurFps;
        } else {
            return 0;
        }
    }

    public void reset() {
        if (VERBOSE) Timber.d("reset this:%s", this);
        mFrameNum = -1;
        mFpsStartTs = 0;
        mCurFps = 0;
    }
}
