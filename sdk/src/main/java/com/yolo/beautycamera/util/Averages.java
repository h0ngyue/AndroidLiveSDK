package com.yolo.beautycamera.util;

import javax.annotation.Nonnull;

import timber.log.Timber;

/**
 * Created by shuailongcheng on 20/12/2016.
 */

public class Averages {
    private int mRoundCount = 20;
    private int mIndex;
    private int mTotal;
    private long mAverage = 0;
    private String mName;

    public Averages(String name) {
        this(name, 20);
    }

    public Averages(@Nonnull String name, int roundCnt) {
        mName = name;
        if (roundCnt < 2) {
            throw new IllegalArgumentException("round count can note be less than 2");
        }
        mRoundCount = roundCnt;
    }

    public void push(long number) {
        mTotal += number;
        mIndex++;
        if (mIndex % mRoundCount == 0) {
            mAverage = mTotal / mIndex;
            mIndex = 0;
            mTotal = 0;
            Timber.w("%s average, %d ms", mName, mAverage);
        }

        if (mAverage == 0) {
            mAverage = number;
        }
    }

    public long get() {
        return mAverage;
    }

    public void setInitValue(int init) {
        mAverage = init;
    }
}
