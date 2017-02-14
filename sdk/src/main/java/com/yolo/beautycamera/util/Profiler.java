package com.yolo.beautycamera.util;

import timber.log.Timber;

/**
 * Created by shuailongcheng on 15/11/2016.
 */

public class Profiler {
    private final String name;
    private long startNano;

    public Profiler(String name) {
        this.name = name;
        startNano = System.nanoTime();
    }

    boolean mSilent = false;

    private static boolean mSilentAll = false;

    public static void setSilentAll(boolean silentAll) {
        mSilentAll = silentAll;
    }

    public Profiler silent() {
        mSilent = true;
        return this;
    }

    public void reset() {
        startNano = System.nanoTime();
    }

    public long tick(String tag) {
        long dur = (System.nanoTime() - startNano) / 1000_000;
        if (!mSilent && !mSilentAll) {
            Timber.w("Profiler(%s), tag:%s, consume %d ms", name, tag, dur);
        }
        reset();
        return dur;
    }

    public void over(String tag) {
        if (mSilent || mSilentAll) {
            return;
        }
        Timber.w("--------- Profiler(%s)    over , tag:%s", name, tag);
    }
}
