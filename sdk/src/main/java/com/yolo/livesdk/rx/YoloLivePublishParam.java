package com.yolo.livesdk.rx;

import android.content.Context;
import android.support.annotation.IntDef;
import android.support.annotation.Keep;

/**
 * Created by shuailongcheng on 9/20/16.
 */
@Keep
public class YoloLivePublishParam {
    public static final int BITRATE_KB_DEFAULT = 800; //kb;
    public static final int BITRATE_KB_MIN = 300; //kb ;
    public static final int BITRATE_KB_MAX = 2048; //kb;

    public static final int DEFAULT_SAMPLERATE = 16000;
    public static final int DEFAULT_CHANNEL_NUM = 1;


    public static final float CRF_DEFAULT = 30.0f;// 30-34
    public static final float CRF_WIFI = 28.0f;// 30-34
    public static final float CRF_2G3G = 32.0f;// 30-34 //29.5 is ok
    public static final float CRF_MIN = 20.0f;// 30-34 //29.5 is ok
    public static final float CRF_MAX = 32.0f;// 30-34 //29.5 is ok

    public static final int FPS_DEFAULT = 15;   // 还是不能用15，低端机不适配啊
    public static final int FPS_MIN = 10;
    public static final int FPS_MAX = 25;


    // 这个必须和PublishParam里的值保持一致
    public static final int ROTATE_NONE = 1;
    public static final int ROTATE_CLOCKWISE_90 = 2;
    public static final int ROTATE_COUNTER_CLOCKWISE_90 = 3;

    @IntDef(value = {ROTATE_NONE, ROTATE_CLOCKWISE_90, ROTATE_COUNTER_CLOCKWISE_90})
    public @interface RotateType {
    }

    public final int width;
    public final int height;
    public final int fps;
    public final float crf;
    public final int bitRate;
    public final int sampleRate;
    public final int channelNum;
    public final String url;
    public final int rotateType;

    public YoloLivePublishParam(String url, int width, int height, int fps, float crf, int bitRate, int sampleRate, int channelNum, int rotateType) {
        this.url = url;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.crf = crf;
        this.bitRate = bitRate;
        this.sampleRate = sampleRate;
        this.channelNum = channelNum;
        this.rotateType = rotateType;
    }


    public static class Builder {
        // 必填
        int width;
        int height;
        String url;

        // 必填 这个必须和recorder一致
        int sampleRate;
        int channelNum;

        // 以下是选填
        int fps = FPS_DEFAULT;
        float crf = CRF_DEFAULT;
        int bitrateKB = BITRATE_KB_DEFAULT;

        int rotateType;

        public Builder(String url, int width, int height, int sampleRate, int channelNum) {
            this.url = url;
            this.width = width;
            this.height = height;
            this.sampleRate = sampleRate;
            this.channelNum = channelNum;
            this.rotateType = ROTATE_COUNTER_CLOCKWISE_90;
        }

        public Builder autoCrf(Context context) {
            if (context == null) {
                return this;
            }
            int netType = NetDetectorReceiver.detectNetType(context);
            switch (netType) {
                case NetDetectorReceiver.NET_2G3G:
                    crf = CRF_2G3G;
                    break;
                case NetDetectorReceiver.NET_WIFI:
                    crf = CRF_WIFI;
                    break;
                case NetDetectorReceiver.NET_UNKNOWN:
                case NetDetectorReceiver.NET_ERROR:
                    crf = CRF_DEFAULT;
                    break;
                default:
                    break;
            }
            return this;
        }

        public Builder crf(float crf) {
            if (crf <= CRF_MAX && crf >= CRF_MIN) {
                this.crf = crf;
            }

            return this;
        }

        public Builder fps(int fps) {
            if (fps > FPS_MAX || fps < FPS_MIN) {
                return this;
            }
            this.fps = fps;
            return this;
        }

        public Builder bitrateKb(int bitrate) {
            if (bitrate < BITRATE_KB_MIN || bitrate > BITRATE_KB_MAX) {
                return this;
            }
            this.bitrateKB = bitrate;
            return this;
        }


        public Builder rotateType(@RotateType int rotateType) {
            this.rotateType = rotateType;
            return this;
        }

        public YoloLivePublishParam build() {
            YoloLivePublishParam p = new YoloLivePublishParam(url, width, height, fps, crf, bitrateKB, sampleRate, channelNum, rotateType);
            return p;
        }
    }
}
