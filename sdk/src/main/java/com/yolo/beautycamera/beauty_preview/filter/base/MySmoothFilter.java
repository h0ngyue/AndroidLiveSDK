package com.yolo.beautycamera.beauty_preview.filter.base;

import com.yolo.beautycamera.beauty_preview.filter.base.gpuimage.MyGPUImageFilter;
import com.yolo.beautycamera.beauty_preview.utils.OpenGlUtils;
import com.yolo.livesdk.R;

import jp.co.cyberagent.android.gpuimage.GPUImageFilter;

/**
 * Created by shuailongcheng on 06/12/2016.
 */

public class MySmoothFilter extends MyGPUImageFilter {
    public MySmoothFilter() {
        super(NO_FILTER_VERTEX_SHADER, OpenGlUtils.readShaderFromRawResource(R.raw.lg_smooth_frag_orig_deprecated));
    }
}
