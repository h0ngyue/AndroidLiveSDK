package com.yolo.beautycamera.beauty_preview.utils;

/**
 * Created by shuailongcheng on 24/11/2016.
 */
public class CosumeOnceLock {
    private volatile boolean mHasData = false;

    public void newDataArrive() {
        synchronized (this) {
            mHasData = true;
            notifyAll();
        }
    }

    public void waitData() throws InterruptedException {
        synchronized (this) {
            while (!mHasData) {
                wait();
            }
            mHasData = false;
        }
    }
}
