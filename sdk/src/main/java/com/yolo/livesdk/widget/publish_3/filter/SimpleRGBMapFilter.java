package com.yolo.livesdk.widget.publish_3.filter;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;

import com.yolo.beautycamera.beauty_preview.BeautyCamera;
import com.yolo.beautycamera.beauty_preview.utils.OpenGlUtils;
import com.yolo.livesdk.R;

import jp.co.cyberagent.android.gpuimage.GPUImageTwoInputFilter;


/**
 * Created by shuailongcheng on 06/12/2016.
 */

public class SimpleRGBMapFilter extends GPUImageTwoInputFilter {
    int mLevel;

    public SimpleRGBMapFilter() {
        super(OpenGlUtils.readShaderFromRawResource(R.raw.lg_rgb_map_frag));

        Bitmap bitmap = BitmapFactory.decodeResource(BeautyCamera.sContext.getResources(), R.drawable.map);
        setBitmap(bitmap);
    }

    @Override
    public void onInit() {
        super.onInit();
        mLevel = GLES20.glGetUniformLocation(getProgram(), "mLevel");
    }
}
