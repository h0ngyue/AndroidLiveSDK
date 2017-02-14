package com.yolo.beautycamera.util;

import android.graphics.Point;

/**
 * Created by shuailongcheng on 15/11/2016.
 * front:[1280x960, 1280x720, 864x480, 640x640, 832x468, 800x480, 720x480, 768x432, 640x480,
 * 480x640, 576x432, 640x360, 480x360, 480x320, 384x288, 352x288, 320x240, 240x320, 240x160, 176x144]
 */

public class Const {
    public static int ORIENTATION = 90;

    public static int DEFAULT_CAMERA_PREVIEW_WIDTH = 640;
    public static int DEFAULT_CAMERA_PREVIEW_HEIGHT = 480;

    public static Point getNaturalWH(int orientation, int width, int height) {
        return (orientation == 90 || orientation == 270)
                ? new Point(height, width) : new Point(width, height);
    }

    public static final boolean OUTPUT_RATOTE_90 = false;
}
