package com.yolo.livesdk.widget.publish_3.filter;

import java.util.Arrays;

import jp.co.cyberagent.android.gpuimage.GPUImageFilterGroup;
import timber.log.Timber;


/**
 * Created by shuailongcheng on 06/12/2016.
 */

public class SimpleBeautyFilter extends GPUImageFilterGroup {
    public SimpleBeautyFilter() {
        super(Arrays.asList(new SimpleSmoothFilter(), new SimpleRGBMapFilter()));
    }
}
