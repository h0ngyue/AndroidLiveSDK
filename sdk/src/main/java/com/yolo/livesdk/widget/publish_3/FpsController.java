package com.yolo.livesdk.widget.publish_3;

import com.yolo.livesdk.rx.YoloLivePublishParam;

import timber.log.Timber;

/**
 * Created by shuailongcheng on 20/12/2016.
 */
public class FpsController {
    private static final boolean VERBOSE = false;

    private int mDesireFps;
    private long mAvrIntevalMs;

    private long mLastFrameMs;
    private long mFirstFrameMs;
    private int mTakenFrames = 0;

    private FpsRecorder mFpsRecorder = new FpsRecorder();

    FpsController() {
        useNormalSettings();
    }


    FpsRecorder getFpsRecorder() {
        return mFpsRecorder;
    }


    public void useFallbackSettings() {
        setDesireFps(YoloLivePublishParam.FPS_MIN);
        setFlipFactor(2);
        setUseFlipPass(true);
    }

    public void useNormalSettings() {
        setDesireFps(YoloLivePublishParam.FPS_DEFAULT);
        setUseFlipPass(false);
    }

    public void setDesireFps(int desireFps) {
        if (desireFps > YoloLivePublishParam.FPS_MAX) {
            throw new IllegalArgumentException("desireFps is invalid, too big");
        }

        if (desireFps < YoloLivePublishParam.FPS_MIN) {
            throw new IllegalArgumentException("desireFps is invalid, too small");
        }

        this.mDesireFps = desireFps;
        mAvrIntevalMs = 1000 / mDesireFps;
        if (VERBOSE)
            Timber.w("setDesireFps, mDesireFps:%d, mAvrIntevalMs:%d", mDesireFps, mAvrIntevalMs);
        reset();

//        logAttributes();
    }

    private void logAttributes() {
        Timber.w("FpsController's attributes: mDesireFps:%d, mUseFlipPass:%b, mFlipFactor:%d", mDesireFps, mUseFlipPass, mFlipFactor);
    }

    private int mFlipIndex;
    private boolean mUseFlipPass = true;
    private int mFlipFactor = 2;

    public void setUseFlipPass(boolean useFlipPass) {
        this.mUseFlipPass = useFlipPass;
//        logAttributes();
    }

    public void setFlipFactor(int factor) {
        if (factor < 2) {
            throw new IllegalArgumentException("factor must be bigger than 2");
        }
        mFlipFactor = factor;
//        logAttributes();
    }

    /**
     * @return true 表示通过flip 过滤
     */
    private boolean flipPass() {
        if (mUseFlipPass) {
            mFlipIndex++;
            return mFlipIndex % mFlipFactor != 0;
        } else {
            return true;
        }
    }

    public void reset() {
        mLastFrameMs = 0;
        mFirstFrameMs = 0;
        mTakenFrames = 0;
        mFpsRecorder.reset();
    }


    /**
     * 1.如果离上一帧的时间超过 ave inteval 则返回true
     * 2.距离第一帧的时间足够久
     *
     * @return
     */
    public boolean canTake(long curMs) {
        if (!flipPass()) {
            if (VERBOSE)
                Timber.e("canTake NO! due to flipPass fail, mFlipIndex:%d, mFlipFactor:%d", mFlipIndex, mFlipFactor);
            return false;
        }

        boolean ret;

        if (mFirstFrameMs == 0) {
            mFirstFrameMs = curMs;
            // 第一张，可以take
            ret = true;
        } else if (curMs - mLastFrameMs > mAvrIntevalMs) {
            if (VERBOSE) Timber.w("canTake = true -> Interval enough: %d,curMs:%d, mLastFrameMs:%d "
                    , curMs - mLastFrameMs, curMs, mLastFrameMs);
            ret = true;
        } else {
            long elipseMs = (curMs - mFirstFrameMs);

            if (elipseMs * mDesireFps > mTakenFrames * 1000) {
                if (VERBOSE)
                    Timber.w("canTake = true -> elipseMs * mDesireFps > mTakenFrames * 1000 , mTakenFrames:%d, elipseMs * mDesireFps/1000:%d"
                            , mTakenFrames, elipseMs * mDesireFps / 1000);
                mTakenFrames++;
                ret = true;
            } else {
                if (VERBOSE) Timber.e("canTake NO!");
                ret = false;
            }
        }

        if (ret) {
            mLastFrameMs = curMs;
            mTakenFrames++;
            if (BeautySurfaceView.USE_FPS_RECORDER_DEBUG) {
                int fps = mFpsRecorder.recordOne();
                if (fps != 0) {
                    Timber.w("FpsRecorder fps:%d, mDesireFps:%d, mFpsRecorder:%s", fps, mDesireFps, mFpsRecorder);
                }
            }
        }
        return ret;
    }
}
