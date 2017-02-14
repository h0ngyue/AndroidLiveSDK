package com.yolo.livesdk.widget.publish2;

import android.app.Activity;
import android.graphics.Bitmap;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.github.piasy.cameracompat.processor.RgbYuvConverter;
import com.yolo.beautycamera.camera.CameraUtil;
import com.yolo.beautycamera.util.Const;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

import timber.log.Timber;

import static android.opengl.GLES20.GL_COLOR_ATTACHMENT0;
import static android.opengl.GLES20.glBindBuffer;
import static android.opengl.GLES30.GL_DYNAMIC_READ;
import static android.opengl.GLES30.GL_MAP_READ_BIT;
import static android.opengl.GLES30.GL_PIXEL_PACK_BUFFER;
import static javax.microedition.khronos.opengles.GL10.GL_RGBA;
import static javax.microedition.khronos.opengles.GL10.GL_UNSIGNED_BYTE;

/**
 * Created by shuailongcheng on 23/11/2016.
 */

public class BeautyCameraPreivewDebugger {
    public volatile ImageView mDumpImage;

    BeautyCameraPreview mBeautyCameraPreview;

    DebugDumpGuide mDumpGuide;

    public boolean mPushOnInitActivity = true;
    public volatile boolean mDebugShouldOutputFrame = true;
    public boolean mLogDumpProfile = false;

    public int mDebugViewportStartX, mDebugViewportStartY;
    public int mDebugCurViewportWidth, mDebugCurViewportHeight;

    // MARK : debug area
    int mDebugBeautyLevel = 0;

    void setViewportParam(int startX, int startY, int vpW, int vpH) {
        mDebugViewportStartX = startX;
        mDebugViewportStartY = startY;
        mDebugCurViewportWidth = vpW;
        mDebugCurViewportHeight = vpH;

    }

    public void toggleOutputFrameProfile() {
        mLogDumpProfile = !mLogDumpProfile;
    }

    public boolean isOutputFrameProfile() {
        return mLogDumpProfile;
    }

    public boolean isOutputFrame() {
        return mDebugShouldOutputFrame;
    }

    public BeautyCameraPreivewDebugger(BeautyCameraPreview beautyCameraPreview) {
        this.mBeautyCameraPreview = beautyCameraPreview;
    }

    public void debugChangeBeautyLevel() {
        mDebugBeautyLevel = (mDebugBeautyLevel + 1) % 6;
        Timber.d("debugChangeBeautyLevel, mDebugBeautyLevel:%d", mDebugBeautyLevel);
        mBeautyCameraPreview.cameraInputFilter.setBeautyLevel(mDebugBeautyLevel);
    }


    public void debugTransitionStartPosViewport(final int transX, final int transY) {
        mBeautyCameraPreview.queueEvent(new Runnable() {
            @Override
            public void run() {
                mDebugViewportStartX += transX;
                mDebugViewportStartY += transY;
                GLES20.glViewport(mDebugViewportStartX, mDebugViewportStartY, mDebugCurViewportWidth, mDebugCurViewportHeight);
            }
        });
    }

    public void debugChangeSizeViewport(final int deltaWidth, final int deltaWidtY) {
        mBeautyCameraPreview.queueEvent(new Runnable() {
            @Override
            public void run() {
                mDebugCurViewportWidth += deltaWidth;
                mDebugCurViewportHeight += deltaWidtY;
                GLES20.glViewport(mDebugViewportStartX, mDebugViewportStartY, mDebugCurViewportWidth, mDebugCurViewportHeight);
            }
        });
    }

    public void debugScaleViewport(final float factorX, final float factorY) {
        mBeautyCameraPreview.queueEvent(new Runnable() {
            @Override
            public void run() {
                GLES20.glViewport(0, 0, (int) (mBeautyCameraPreview.mCurSurfaceWidth * factorX), (int) (mBeautyCameraPreview.mCurSurfaceHeight * factorY));
            }
        });
    }

    int mCurFixedSizeX = Const.DEFAULT_CAMERA_PREVIEW_HEIGHT, mCurFixedSizeY = Const.DEFAULT_CAMERA_PREVIEW_WIDTH;

    public void debugChangeFixedSize(final int deltaX, final int deltaY) {
        mBeautyCameraPreview.post(new Runnable() {
            @Override
            public void run() {
                mCurFixedSizeX += deltaX;
                mCurFixedSizeY += deltaY;
                mBeautyCameraPreview.getHolder().setFixedSize(mCurFixedSizeX, mCurFixedSizeY);
            }
        });
    }

    public void toggleShouldOutputFrame() {
        mDebugShouldOutputFrame = !mDebugShouldOutputFrame;
    }

    public void setDebugShouldOutputFrame(boolean debugShouldOutputFrame) {
        this.mDebugShouldOutputFrame = debugShouldOutputFrame;
    }

    public DebugDumpGuide getDumpGuide() {
        return mDumpGuide;
    }

    private int dumpDeltaStartX = 0;
    private int dumpDeltaWidth = 0;

    public static void setCameraDisplayOrientation(Activity activity, int cameraOrientation, boolean isFront) {
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (isFront) {
            result = (cameraOrientation + degrees) % 360;
            result = (360 - result) % 360; // compensate the mirror
        } else { // back-facing
            result = (cameraOrientation - degrees + 360) % 360;
        }
        Timber.d("should setCameraDisplayOrientation:%d", result);
//        camera.setDisplayOrientation(result);
    }

    public static class DebugDumpGuide {

        // at most one of projectionWidth and projectionHeight will be less than 1.0f, the other will be 1.0f
        public int startX, startY, dumpWidth, dumpHeight;
        boolean mWidthCutted = true;

        public boolean mUseOrigRgb = true;

        @Override
        public String toString() {
            StringBuffer sb = new StringBuffer();
            sb.append("startX:").append(startX)
                    .append(",startY:").append(startY)
                    .append(",projectionWidth:").append(dumpWidth)
                    .append(",projectionHeight:").append(dumpHeight);
            return sb.toString();
        }

        public DebugDumpGuide(int startX, int startY, int projectionWidth, int dumpHeight) {
            this.startX = startX;
            this.startY = startY;
            this.dumpWidth = projectionWidth;
            this.dumpHeight = dumpHeight;
        }

        public DebugDumpGuide(DebugDumpGuide other) {
            this.startX = other.startX;
            this.startY = other.startY;
            this.dumpWidth = other.dumpWidth;
            this.dumpHeight = other.dumpHeight;
        }

        public int getStartX() {
            return startX;
        }

        public int getStartY() {
            return startY;
        }

        public int getDumpWidth() {
            return dumpWidth;
        }

        public int getDumpHeight() {
            return dumpHeight;
        }
    }

    IntBuffer pbo_id = IntBuffer.allocate(1);
    int pbo_size;
    ByteBuffer pobBuffer;

    public void initPBO(int width, int height) {
        Timber.d("initPOB, width:%d, height:%d", width, height);
        pbo_size = width * height * 4;
        pobBuffer = ByteBuffer.allocateDirect(pbo_size).order(ByteOrder.nativeOrder());
        GLES30.glGenBuffers(1, pbo_id);
        GLES30.glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo_id.get(0));
        GLES30.glBufferData(GL_PIXEL_PACK_BUFFER, pbo_size, pobBuffer, GL_DYNAMIC_READ);
        glBindBuffer(GL_PIXEL_PACK_BUFFER, 0);
    }

    public void dumpImagePboAsync() {
        final int width = mDumpGuide.dumpWidth;
        final int height = mDumpGuide.dumpHeight;
        Timber.d("dumpImagePBO, width:%d, height:%d", width, height);

        mBeautyCameraPreview.queueEvent(new Runnable() {
            @Override
            public void run() {
                GLES30.glReadBuffer(GL_COLOR_ATTACHMENT0);
//                GLES30.glReadBuffer(GL_BACK);
                GLES30.glBindBuffer(GL_PIXEL_PACK_BUFFER, pbo_id.get(0));

                long start = System.nanoTime();
                GLES30.glReadPixels(0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, pobBuffer);
                Timber.d("glReadPixels consume: %d,", (System.nanoTime() - start) / 1000_000);

                Buffer ptr = GLES30.glMapBufferRange(GL_PIXEL_PACK_BUFFER, 0, pbo_size, GL_MAP_READ_BIT);

                if (ptr == null) {
                    Timber.e("read pobBuff null!");
                    return;
                }
                final Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
                result.copyPixelsFromBuffer(ptr);

                GLES30.glUnmapBuffer(GL_PIXEL_PACK_BUFFER);
                GLES30.glBindBuffer(GLES30.GL_PIXEL_PACK_BUFFER, 0);

                mDumpImage.post(new Runnable() {
                    @Override
                    public void run() {
                        ViewGroup.LayoutParams layoutParams = mDumpImage.getLayoutParams();
                        layoutParams.width = width * 2;
                        layoutParams.height = height * 2;
                        mDumpImage.setLayoutParams(layoutParams);
                        mDumpImage.setVisibility(View.VISIBLE);
                        // setRotationX暂时会让 set yuv的情况图片不可见，暂时注释掉
//                        mDumpImage.setRotationX(180f);
                        mDumpImage.setImageBitmap(result);
                    }
                });
            }
        });

    }

    public void dumpImage() {
        mBeautyCameraPreview.queueEvent(new Runnable() {
            @Override
            public void run() {
                final int width = mDumpGuide.getDumpWidth();
                final int height = mDumpGuide.getDumpHeight();

                final ByteBuffer mRgbaBuf = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder());
//        final IntBuffer mRgbaBuf = IntBuffer.allocate(width*height);

                mRgbaBuf.position(0);

                final String type = "GLES20.GL_RGBA";

                int GLFormat = GLES20.GL_RGBA;
                final Bitmap.Config BitmapFormat = Bitmap.Config.ARGB_8888;

                long start = System.nanoTime();

                GLES20.glFinish();
                GLES20.glReadPixels(0, 0, 1, 1,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mRgbaBuf);

                GLES20.glReadPixels(mDumpGuide.startX, mDumpGuide.startY, width, height, GLFormat, GLES20.GL_UNSIGNED_BYTE, mRgbaBuf);
                Timber.d("glReadPixels, %s, consume: %d, width:%d, height:%d,mDumpGuide:%s",
                        type, (System.nanoTime() - start) / 1000_000, width, height, mDumpGuide);
                CameraUtil.dumpGlError("glReadPixels");


                mDumpImage.post(new Runnable() {
                    @Override
                    public void run() {
                        long start = System.nanoTime();

                        int imageWidth = width;
                        int imageHeight = height;
                        Bitmap result;

                        if (!mDumpGuide.mUseOrigRgb) {
                            ByteBuffer yuvBuf = ByteBuffer.allocateDirect(width * height * 3 / 2);
                            ByteBuffer rgbBackBuf = ByteBuffer.allocateDirect(width * height * 4);

//                    RgbYuvConverter.rgba2yuv(width, height, mRgbaBuf.array(), yuvBuf.array());
//                    RgbYuvConverter.yuv2rgba(width,height, yuvBuf.array(), rgbBackBuf.array());

                            RgbYuvConverter.rgba2yuvRotateC90(width, height, mRgbaBuf.array(), yuvBuf.array());
                            RgbYuvConverter.yuv2rgba(height, width, yuvBuf.array(), rgbBackBuf.array());
                            imageWidth = height;
                            imageHeight = width;

                            result = Bitmap.createBitmap(imageWidth, imageHeight, BitmapFormat);
                            result.copyPixelsFromBuffer(rgbBackBuf);
                        } else {
                            result = Bitmap.createBitmap(imageWidth, imageHeight, BitmapFormat);
                            result.copyPixelsFromBuffer(mRgbaBuf);
                        }

                        ViewGroup.LayoutParams layoutParams = mDumpImage.getLayoutParams();
                        layoutParams.width = imageWidth * 2;
                        layoutParams.height = imageHeight * 2;
                        mDumpImage.setLayoutParams(layoutParams);

                        mDumpImage.setVisibility(View.VISIBLE);
//                        mDumpImage.setRotationX(180f);
                        mDumpImage.setImageBitmap(result);

//                Timber.d("dumpImage, createBitmap, %s consume: %d, width:%d, height:%d, result.getByteCount():%d",
//                        type, (System.nanoTime() - start) / 1000_000, width, height, result.getByteCount());
                    }
                });
            }
        });

    }
}
