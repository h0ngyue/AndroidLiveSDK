package com.yolo.livesdk.widget.publish_controller;

/**
 * Created by shuailongcheng on 19/12/2016.
 */

public class PublishTimeStamp {
    private static long systemCurMs() {
        return System.nanoTime() / 1000_000;
    }

    private final static long sAppStartTsMs = systemCurMs();

    public static long currentMs() {
        return systemCurMs() - sAppStartTsMs;
    }
}
