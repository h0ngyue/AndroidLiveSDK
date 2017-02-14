package com.yolo.livesdk.widget.watch;

import android.support.annotation.Nullable;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;

import timber.log.Timber;

/**
 * Created by shuailongcheng on 17/12/2016.
 */
public class WatchViewUtils {

    /**
     * @return true表示resize成功了，否则表示因为某些原因（比如参数不齐）而失败了
     */
    public static boolean resize(View view, float maxWidth, float maxHeight,
                                 float renderPixelWidth,
                                 float renderPixelHeight, boolean fill) {
        if (maxHeight <= 0 || maxWidth <= 0) {
            throw new IllegalArgumentException("can't resize to zero with or zero height");
        }

        Timber.d("resize: maxWidth:%f, maxHeight:%f", maxWidth, maxHeight);

        float resultH, resultW;

        Timber.d(YoloWatchView.TAG + "fillClip = " + fill);
        Pair<Float, Float> actualVideoSize;
        if (fill) {
            actualVideoSize = calcuateSizeWithClip(maxWidth, maxHeight, renderPixelWidth,
                    renderPixelHeight);
        } else {
            actualVideoSize = calcuateSizeNoClip(maxWidth, maxHeight, renderPixelWidth,
                    renderPixelHeight);
        }
        if (actualVideoSize == null) {
            Timber.d("!!! Utils.resize, get actualVideoSize == null, give up resize");
            return false;
        }

        resultW = actualVideoSize.first;
        resultH = actualVideoSize.second;

        resizeExactlyTo(view, (int) resultW, (int) resultH, maxWidth, maxHeight, fill);
        return true;
    }

    public static void setVisible(View view, boolean visible) {
        AlphaAnimation sa = new AlphaAnimation(visible ? 0 : 1, visible ? 1 : 0);
        sa.setFillAfter(true);
        view.startAnimation(sa);
    }

    public static void resizeExactlyTo(View view, int glWidth, int glHeight,
                                       float watchViewWidth,
                                       float watchViewHeight, boolean fill) {
        ViewGroup.LayoutParams layoutParams = view.getLayoutParams();
        if (layoutParams == null) {
            layoutParams = new FrameLayout.LayoutParams(glWidth, glHeight);
        } else {
            layoutParams.width = glWidth;
            layoutParams.height = glHeight;
        }

        // for huawei p8 debug
        Timber.d("resizeExactlyTo: glWidth:%d, glHeight:%d, watchViewWidth:%f, watchViewHeight:%f",
                glWidth, glHeight, watchViewWidth, watchViewHeight);

        view.setLayoutParams(layoutParams);

        if (fill) {
            ScaleAnimation sa = new ScaleAnimation(1f, watchViewWidth / glWidth, 1f,
                    watchViewHeight / glHeight,
                    Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            sa.setDuration(0);
            sa.setFillAfter(true);
            view.post(() -> view.startAnimation(sa));
            view.startAnimation(sa);
        }
    }

    static public Pair<Float, Float> calcuateSizeWithClip(float maxWidth, float maxHeight,
                                                          float renderPixelWidth, float renderPixelHeight) {
        if (renderPixelWidth == 0 || renderPixelHeight == 0) {
            return null;
        }

        float resultH, resultW;
        float whRatio = maxWidth / maxHeight;
        float videoWhRatio = renderPixelWidth / renderPixelHeight;
        if (whRatio > videoWhRatio) {
            // 提供的宽更宽, 重新算高度
            resultW = maxWidth;
            resultH = maxWidth / videoWhRatio;
        } else {
            resultH = maxHeight;
            resultW = maxHeight * videoWhRatio;
        }

        return new Pair<>(resultW, resultH);
    }

    @Nullable
    public static Pair<Float, Float> calcuateSizeNoClip(float maxWidth, float maxHeight,
                                                        float renderPixelWidth, float renderPixelHeight) {
        if (renderPixelWidth == 0 || renderPixelHeight == 0) {
            return null;
        }

        float resultH, resultW;
        float whRatio = maxWidth / maxHeight;
        float videoWhRatio = renderPixelWidth / renderPixelHeight;
        if (whRatio > videoWhRatio) {
            // 提供的宽太宽了,宽度重新算宽度
            resultH = maxHeight;
            resultW = maxHeight * videoWhRatio;
        } else {
            resultW = maxWidth;
            resultH = maxWidth / videoWhRatio;
        }

        return new Pair<>(resultW, resultH);
    }
}
