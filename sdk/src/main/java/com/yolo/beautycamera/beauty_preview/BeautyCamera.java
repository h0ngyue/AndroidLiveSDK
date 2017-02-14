package com.yolo.beautycamera.beauty_preview;

import android.content.Context;

import com.yolo.beautycamera.camera.CameraEngine;

public class BeautyCamera {
    public static void init(Context appContext, boolean useCamera1Only) {
        sContext = appContext;
        sUseCamera1Only = useCamera1Only;
    }

    public static Context sContext;
    public static boolean sUseCamera1Only;


    public static int beautyLevel = 5;

    public BeautyCamera() {

    }
}
