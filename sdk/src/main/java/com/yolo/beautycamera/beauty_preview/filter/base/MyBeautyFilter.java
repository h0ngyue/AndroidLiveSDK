package com.yolo.beautycamera.beauty_preview.filter.base;

import java.util.Arrays;

import jp.co.cyberagent.android.gpuimage.GPUImageFilterGroup;

/**
 * Created by shuailongcheng on 06/12/2016.
 */

public class MyBeautyFilter extends MagicBaseGroupFilter {
    public MyBeautyFilter() {
        super(Arrays.asList(new MySmoothFilter(), new MyRGBMapFilter()));
    }
}
