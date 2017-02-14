package com.yolo.beautycamera.util;

/**
 * Created by shuailongcheng on 21/12/2016.
 */

public class TimeUtil {
    public static long curMs() {
        return System.nanoTime()/1000_000;
    }
}
