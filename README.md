 
##主播端quickStart(maybe not that quick)：
 
 
* root build.gradle:
 
```gradle
allprojects {
		repositories {
			...
			maven { url 'https://jitpack.io' }
		}
	}
```
 
* your app gradle:
 
```gradle
dependencies {
 	compile 'com.github.h0ngyue:AndroidLiveSDK:1.0.1'
}
```
 
####step 1.布局植入BeautySurfaceView
 ```xml
 <RelativeLayout
        xmlns:android="http://schemas.android.com/apk/res/android"
        xmlns:tools="http://schemas.android.com/tools"
        android:id="@+id/activity_main"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        tools:context="com.github.h0ngyue.androidlivesdk.MainActivity"
        >
    <com.yolo.livesdk.widget.publish_3.BeautySurfaceView
            android:id="@+id/mBeautySurfaceView"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            />
</RelativeLayout>
 ```
 
####step 2. Activity里设置
 ```java
 
 @Override
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_main);

    initPreviewPublisher();
}

   private void initPreviewPublisher() {
        // 现在这个没存pref,就是每场都默认开启麦克的
        mBeautyPreview = (BeautySurfaceView) findViewById(R.id.mBeautySurfaceView);
        mBeautyPublishController = mBeautyPreview.getController();
        mBeautyPublishController.audioOn(true);

        boolean initFrontCamera = true;
        boolean initUseBeauty = true;
        boolean initMirror = true;
        boolean portrait = true;
        mBeautyPublishController
                .initPrefs(initFrontCamera, initUseBeauty, initMirror,
                        portrait);
    }
 
 ```
 
####step 3. 推流

```java
@Override
    protected void onStart() {
        super.onStart();
        mBeautyPublishController
                .startPublish(MainActivity.this, mRtmpUrl, 		mBeautyPublisherCallback);    }
```

> mBeautyPublisherCallback是回调接口，用于观察推流和相机失败等一些错误状态

#### 其他配置

* 权限：

```xml

<uses-feature android:name="android.hardware.camera"/>
    <uses-feature android:name="android.hardware.camera.autofocus"/>
    <uses-feature android:glEsVersion="0x00020000"/>
    <uses-feature android:name="android.hardware.location.gps"/>

    <uses-permission android:name="android.permission.CAMERA"/>
    <uses-permission android:name="android.permission.RECORD_AUDIO"/>
    <uses-permission android:name="android.permission.INTERNET"/>
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
    <uses-permission android:name="android.permission.ACCESS_WIFI_STATE"/>
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>
    <uses-permission android:name="android.permission.RECEIVE_USER_PRESENT"/>
    <uses-permission android:name="android.permission.FLASHLIGHT"/>

```

* build.gradle 

```gradle
android {
	
    defaultConfig {
       ...

        ndk {
            abiFilter "armeabi"
        }
    }
    ...
}
```

* App

```java

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

```

一些细节可以直接参照demo里的设置

##TODO
* 完善demo，加入控制开关：开关美颜，开关闪关灯，前后置摄像头切换等，镜像（对应的功能已实现）
* 完善demo，加入观看示例

##Licence
```licence
            DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE
                    Version 2, December 2004

 Copyright (C) 2004 Sam Hocevar <sam@hocevar.net>

 Everyone is permitted to copy and distribute verbatim or modified
 copies of this license document, and changing it is allowed as long
 as the name is changed.

            DO WHAT THE FUCK YOU WANT TO PUBLIC LICENSE
   TERMS AND CONDITIONS FOR COPYING, DISTRIBUTION AND MODIFICATION

  0. You just DO WHAT THE FUCK YOU WANT TO.
```
aka [wtfpl](http://www.wtfpl.net/txt/copying/)
