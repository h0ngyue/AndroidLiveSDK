package com.yolo.livesdk.widget.publish_3.filter;


import com.yolo.beautycamera.beauty_preview.utils.OpenGlUtils;
import com.yolo.livesdk.R;

import jp.co.cyberagent.android.gpuimage.GPUImageFilter;

/**
 * Created by shuailongcheng on 06/12/2016.
 */

public class SimpleSmoothFilter extends GPUImageFilter {
    public SimpleSmoothFilter() {
        super(NO_FILTER_VERTEX_SHADER, OpenGlUtils.readShaderFromRawResource(R.raw.lg_smooth_frag_new_tex2d_all));
    }
}
