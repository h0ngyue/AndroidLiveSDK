package com.yolo.livesdk.widget.publish_3;

import com.yolo.livesdk.widget.publish_controller.PublishTimeStamp;

import java.nio.ByteBuffer;

import timber.log.Timber;

/**
 * Created by shuailongcheng on 13/12/2016.
 */
public class FrameMessage {
    ByteBuffer mRgbBuffer;
    int mWidth;
    int mHeight;
    int mBufferSize;
    long mTimestampMs;

    FrameMessage next;

    public FrameMessage(ByteBuffer mRgbBuffer, int width, int height, long tsMs) {
        this.mRgbBuffer = mRgbBuffer;
        this.mWidth = width;
        this.mHeight = height;
        mTimestampMs = tsMs;
    }

    public FrameMessage(ByteBuffer mRgbBuffer, int bufferSize, long tsMs) {
        this.mRgbBuffer = mRgbBuffer;
        this.mBufferSize = bufferSize;
        this.mTimestampMs = tsMs;
    }

    public void release() {
        next = null;
        mRgbBuffer = null;
    }

    public ByteBuffer getBuffer() {
        return mRgbBuffer;
    }

    public void printTimestampDiff(String tag) {
        long cur = PublishTimeStamp.currentMs();
        Timber.d("printTimestampDiff, tag:%s, diff:%d ms, print cur:%d, mTimestampMs:%d", tag, cur - mTimestampMs, cur, mTimestampMs);
    }
}
