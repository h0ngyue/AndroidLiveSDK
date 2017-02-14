package com.utils;

import timber.log.Timber;

/**
 * Created by shuailongcheng on 15/12/2016.
 */

public class BenchUtil {

    public static class Average {
        private String tag;
        private int mCountEveryRound;
        private int count;
        private long totalDur;

        public Average(String tag, int countEveryRound) {
            this.tag = tag;
            mCountEveryRound = countEveryRound;
        }

        public void tick(long durMs) {
            count++;
            totalDur += durMs;
            if (count % mCountEveryRound == 0) {
                // 结算时刻
                Timber.w("name:%s, %d ms", tag, totalDur/ count);
                count = 0;
                totalDur = 0;
            }
        }
    }
}
