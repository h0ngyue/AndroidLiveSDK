package com.yolo.livesdk.widget;

import android.media.AudioFormat;

/**
 * Created by shuailongcheng on 05/12/2016.
 */
public interface PublishConst {
    int DEFAULT_SAMPLERATE = 16000;

    int DEFAULT_CHANNEL_NUM = 1;

    int DEFAULT_CHANNEL_CONFIG = DEFAULT_CHANNEL_NUM == 1 ? AudioFormat.CHANNEL_IN_MONO
            : AudioFormat.CHANNEL_IN_STEREO;

    int DEFAULT_BITDEPTH_CONFIG = AudioFormat.ENCODING_PCM_16BIT;
}
