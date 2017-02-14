package com.yolo.livesdk.widget.publish_3;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import timber.log.Timber;

/**
 * Created by shuailongcheng on 13/12/2016.
 */
public class FrameMessagePool {
    private static final boolean VERBOSE = false;
    private FrameMessage mPool;
    private final Object mPoolSync = new Object();
    private int mPoolSize = 0;

    public FrameMessage obtain(int width, int height, long curMs) {
        synchronized (mPoolSync) {
            while (true) {
                if (mPool != null) {
                    FrameMessage m = mPool;
                    mPool = m.next;
                    m.next = null;
                    mPoolSize--;
                    if ((m.mWidth == width && m.mHeight == height)) {
                        if (VERBOSE) Timber.d("obtain , yes got one, mPoolSize:%d", mPoolSize);
                        m.mTimestampMs = curMs;
                        return m;
                    } else {
                        m.release();
                    }
                } else {
                    break;
                }
            }
        }
        if (VERBOSE) Timber.e("obtain , not got one, mPoolSize:%d", mPoolSize);
        ByteBuffer buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
        return new FrameMessage(buffer, width, height, curMs);
    }

    private int total;

    public FrameMessage obtain(int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("obtain size must be positive");
        }

        synchronized (mPoolSync) {
            while (true) {
                if (mPool != null) {
                    FrameMessage m = mPool;
                    mPool = m.next;
                    m.next = null;
                    mPoolSize--;
                    if ((m.mBufferSize == bufferSize)) {
                        if (VERBOSE) Timber.d("obtain , yes got one, mPoolSize:%d", mPoolSize);
                        return m;
                    } else {
                        m.release();
                    }
                } else {
                    break;
                }
            }
        }
        total++;
        if (VERBOSE) Timber.e("obtain , not got one, mPoolSize:%d, total:%d", mPoolSize, total);
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize).order(ByteOrder.nativeOrder());
        buffer.position(0);
        return new FrameMessage(buffer, bufferSize, 0);
    }

    public void recycle(FrameMessage message) {
        if (message == null) {
            return;
        }

        synchronized (mPoolSync) {
            message.mRgbBuffer.position(0);
            message.next = mPool;
            mPool = message;
            mPoolSize++;
            if (VERBOSE) Timber.d("recycle ,  mPoolSize++,  mPoolSize:%d", mPoolSize);
        }
    }

    public void release() {
        synchronized (mPoolSync) {
            while (true) {
                if (mPool != null) {
                    FrameMessage m = mPool;
                    mPool = m.next;
                    mPoolSize--;

                    m.release();
                } else {
                    break;
                }
            }
        }

    }
}
