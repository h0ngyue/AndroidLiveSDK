package com.yolo.livesdk.widget.watch;

import android.opengl.GLSurfaceView;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.SparseArray;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;

import timber.log.Timber;

/**
 * Created by shuailongcheng on 22/12/2016.
 */

public class FastWatch {
    private static SparseArray<FastWatchViewManager> sMapPrepared = new SparseArray<>();


    public static synchronized int prepare(String url) {
        if (TextUtils.isEmpty(url)) {
            throw new IllegalArgumentException("url can not be null");
        }

        Timber.d("prepare, url:%s", url);

        FastWatchViewManager fastWatchViewManager = new FastWatchViewManager();
        fastWatchViewManager.init(url);
        fastWatchViewManager.prepare();

        int id = fastWatchViewManager.getId();
        sMapPrepared.put(id, fastWatchViewManager);
        return id;
    }

    /**
     * id
     * @param id for FastWatchManager
     * @return
     */
    public static synchronized void resume(int id) {
        FastWatchViewManager fastWatchViewManager = sMapPrepared.get(id);

        if (fastWatchViewManager == null) {
            Timber.e("resume, sMapPrepared.get(%d) = null", id);
            return;
        }
        fastWatchViewManager.resume();
    }


    public static synchronized void attachToManager(int id, @Nonnull GLSurfaceView glSurfaceView, @Nonnull FastWatchStatusListener listener) {
        FastWatchViewManager fastWatchViewManager = sMapPrepared.get(id);
        if (fastWatchViewManager == null) {
            Timber.e("attachToManager, sMapPrepared.get(%d) = null", id);
            return;
        }

        fastWatchViewManager.setupGLSurfaceView(glSurfaceView);
        fastWatchViewManager.setListener(listener);
    }

    public static synchronized void safeRelease(int id) {
        FastWatchViewManager fastWatchViewManager = sMapPrepared.get(id);

        if (fastWatchViewManager != null) {
            Timber.d("safeRelease, fastWatchViewManager != null");
            fastWatchViewManager.destroy();
            sMapPrepared.remove(id);
        } else {
            Timber.e("safeRelease, fastWatchViewManager not found in map, id: %d", id);
        }
    }

    public static synchronized void releaseAll() {
        for (int i=0;i < sMapPrepared.size();i++) {
            sMapPrepared.removeAt(i);
        }
        Timber.d("releaseAll, sCurrent = null now");
    }

}
