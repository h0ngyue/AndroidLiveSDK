package com.github.h0ngyue.androidlivesdk;

import com.yolo.beautycamera.beauty_preview.BeautyCamera;
import com.yolo.livesdk.YoloLiveNative;

import android.app.Application;

/**
 * Created by shuailongcheng on 14/02/2017.
 */

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        // 底层lib初始化
        YoloLiveNative.init(this, false);
        // 美颜相机初始化
        BeautyCamera.init(getApplicationContext(), true);
    }
}
