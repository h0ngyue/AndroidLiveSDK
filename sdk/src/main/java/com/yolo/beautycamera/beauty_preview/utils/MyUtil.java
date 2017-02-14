package com.yolo.beautycamera.beauty_preview.utils;

import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.yolo.beautycamera.util.Profiler;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Created by shuailongcheng on 28/11/2016.
 */

public class MyUtil {
    static ByteBuffer pixelBuf;
    static long mLastReadEndMs;

    public static volatile ImageView mDebugDumpImage;

    static long total_consume = 0;
    static long count_consume = 0;

    public static void tryReadPixels(int startX, int startY, final int w, final int h) {
        tryReadPixels(0, 0, w, h, mDebugDumpImage);
    }


    public static void tryReadPixels(int w, int h, final ImageView imageView) {
        tryReadPixels(0, 0, w, h, imageView);
    }

    public static void tryReadPixels(int startX, int startY, int w, int h, final ImageView imageView) {
        Profiler profiler = new Profiler("MyUtil");

        if (pixelBuf == null || pixelBuf.limit() < w * h * 4) {
            pixelBuf = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.LITTLE_ENDIAN);
        }

        profiler.tick("ByteBuffer.alloc");

        // Try to ensure that rendering has finished.
        GLES20.glFinish();

        profiler.tick("glFinish");

//        pixelBuf.position(0);
//        GLES20.glReadPixels(0, 0, 1, 1,
//                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf);

        // Time individual extraction.  Ideally we'd be timing a bunch of these calls
        // and measuring the aggregate time, but we want the isolated time, and if we
        // just read the same buffer repeatedly we might get some sort of cache effect.

        pixelBuf.position(0);
        GLES20.glReadPixels(startX, startY, w, h,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuf);

        profiler.tick("glReadPixels");

        if (imageView != null) {

            final Bitmap tmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            tmp.copyPixelsFromBuffer(pixelBuf);
            imageView.post(new Runnable() {
                @Override
                public void run() {
                    adjustRatioByWidth(imageView, w, h);
                    imageView.setImageBitmap(tmp);
                }
            });

            profiler.tick("dump2Image");
        }

        profiler.over("OVER");
    }

    private static void adjustRatioByWidth(ImageView imageView, int w, int h) {
        ViewGroup.LayoutParams layoutParams = imageView.getLayoutParams();
        layoutParams.height = (int) (imageView.getWidth() * h * 1.0 / w);
        imageView.setLayoutParams(layoutParams);
    }

    private static boolean equalRatio(View view, int w, int h) {
        try {
            float diff = Math.abs((view.getWidth() * 1.0f / view.getHeight()) - (w * 1.0f / h));
//            Timber.d("equalRatio, diff:%f, w:%d, h:%d", diff, w, h);
            return diff < 0.01;
        } catch (Exception ex) {
            return false;
        }
    }

    public static int floor16BytesAlign(int i) {
        return i / 16 * 16;
    }

    /**
     * @param num 如果
     * @return
     */
    public static int round16IfCan(int num) {
        int remainder = num % 16;
        if (remainder >= 8) {
            return (num + 15) / 16 * 16;
        } else {
            return num;
        }
    }

    public static float addDistance(float coordinate, float distance) {
        return coordinate == 0.0f ? distance : 1 - distance;
    }


}
