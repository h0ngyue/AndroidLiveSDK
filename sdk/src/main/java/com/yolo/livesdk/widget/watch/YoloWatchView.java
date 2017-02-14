package com.yolo.livesdk.widget.watch;

import android.content.Context;
import android.content.res.TypedArray;
import android.media.AudioFormat;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.FrameLayout;

import com.github.piasy.rxandroidaudio.StreamAudioPlayer;
import com.render_view.PlayView;
import com.utils.RxUtils;
import com.utils.ScreenUtils;
import com.yolo.livesdk.R;
import com.yolo.livesdk.YoloLiveNative;
import com.yolo.livesdk.rx.YoloLiveObs;
import com.yolo.livesdk.rx.RxYoloLive;

import java.util.ArrayList;
import java.util.List;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;


/**
 * Created by shuailongcheng on 10/10/16.
 */
public class YoloWatchView extends FrameLayout {

    int AUDIO_FRAME_BUFFER_SIZE = 4096;


    private String mUrl;

    private boolean mStarted = false;

    private int mStreamId;

    private PlayView mPlayView;

    private volatile boolean mHasInitAudio = false;

    private StreamAudioPlayer mStreamAudioPlayer;

    private Context mContext;

    private static final float DEFAULT_WIDTH = 480f;

    private static final float DEFAULT_HEIGHT = 640f;

    /**
     * 视频实际的分辨率
     */
    private float mRenderPixelWidth, mRenderPixelHeight;

    static final String TAG_WIDTHHEIGHT = "TAG_WIDTHHEIGHT";

    static final String TAG = "YoloWatchView:";

    private List<Subscription> mSubscriptionList = new ArrayList<>();

    private boolean mFillClip = true;

    /**
     * 多次调用有副作用,不要多次调用
     *
     * @param url
     */
    public void init(String url) {
        mUrl = url;

        addSubscription(RxYoloLive.notifyWidthHeight()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(whInfo -> {
                    if (mStreamId != whInfo.id) {
                        return;
                    }

                    onVideoResolutionWH(whInfo.width, whInfo.height);

                }, RxUtils.IgnoreErrorProcessor));

        // 这里不要随便在subscribe的时候切到io()线程，否则不能保证帧的有序性
        addSubscription(RxYoloLive.notifyVideoData()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(videoData -> {
                    if (mStreamId != videoData.id) {
                        return;
                    }

                    if (mPlayView != null && mPlayView.isReady()) {
                        if (videoData.debugIndex < mLastDebugIndex) {
                            Timber.e("mPlayView.UpdateScreenAll, video Frame debugIndex:%d, but mLastDebugIndex:%d", videoData.debugIndex, mLastDebugIndex);
                            // 权宜之计,目前确实是因为会有更小index的帧反复来几下,出现抖动,原因还没找到
                            return;
                        } else {
                            Timber.v("mPlayView.UpdateScreenAll, video Frame debugIndex:%d", videoData.debugIndex);
                        }
                        mLastDebugIndex = videoData.debugIndex;
                        mPlayView.UpdateScreenAll(videoData.data);

                    } else {
                        Log.e(TAG_WIDTHHEIGHT, "but mPlayView not ready");
                    }
                }, RxUtils.IgnoreErrorProcessor));

        addSubscription(RxYoloLive.notifyAudioSpecInfo()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(audioInfo -> {
//                    Timber.d("RxYoloLive.notifyAudioSpecInfo:" + audioInfo);
                    if (mStreamId != audioInfo.id) {
                        return;
                    }
                    Log.e("notifyAudioSpecInfo", "audioInfo:" + audioInfo);
                    initAudioPlayer(audioInfo);
                }, RxUtils.IgnoreErrorProcessor));

        // 这里不要随便在subscribe的时候切到io()线程，否则不能保证帧的有序性
        addSubscription(RxYoloLive.notifyAudioData()
                .observeOn(Schedulers.computation())
                .subscribe(data -> {
//                    Timber.d("RxYoloLive.notifyAudioData , id:" + data.id + ", size:" + data.data.length);
                    if (mStreamId != data.id) {
                        return;
                    }

                    if (mStreamAudioPlayer != null) {
                        mStreamAudioPlayer.play(data.data, data.data.length);
                    } else {
                        Log.e("notifyAudioData",
                                "LiveStreamReceiver.notifyAudioData. but mPlayView not ready");
                    }
                }));
    }


    /**
     * 可以多次调用,始终只会有一个收流实例,底层会把之前的冲掉
     * 所以一些场景下可以当成restart直接调用
     *
     * @return
     */
    public synchronized boolean start() {
        mStarted = true;

        if (TextUtils.isEmpty(mUrl)) {
            return false;
        }

        startNative();

        return true;
    }

    /**
     * 多次调用无害
     */
    public synchronized void stop() {
        if (!mStarted) {
            return;
        }
        mStarted = false;
        for (Subscription sc : mSubscriptionList) {
            if (!sc.isUnsubscribed()) {
                sc.unsubscribe();
            }
        }

        YoloLiveNative.stopReceive(mStreamId);
        Timber.d("StreamAudioPlayer  mStreamAudioPlayer.release()");
    }

    /**
     * 1.保证在销毁的时候调用
     * 2.多次调用无害,但是如果多次调用的话,说明对这个接口的使用不是很对
     */
    public void destroy() {
        stop();
        mStreamAudioPlayer.release();
    }

    public boolean isStarted() {
        return mStarted;
    }


    public Observable<RxYoloLive.NotifyEvent> getEventObservable() {
        return RxYoloLive.notifyEvent();
    }


    private void addSubscription(Subscription sc) {
        mSubscriptionList.add(sc);
    }

    private int mLastDebugIndex;

    private synchronized void startNative() {
        mStreamId = YoloLiveNative.startReceive(mUrl);
        Log.e(TAG, "reRecv, startNative, mStreamId=" + mStreamId);
    }


    public float getRenderPixelWidth() {
        return mRenderPixelWidth;
    }

    public float getRenderPixelHeight() {
        return mRenderPixelHeight;
    }

    /**
     * // 不耗时
     */
    private synchronized void onVideoResolutionWH(int pixelWidth, int pixelHeight) {
        Timber.d(TAG + "onVideoResolutionWH: mVideoPixelWidth:" + mRenderPixelWidth
                + ", mVideoPixelHeight:" + mRenderPixelHeight
                + ", pixelWidth:" + pixelWidth
                + ", pixelHeight:" + pixelHeight);

        if (mRenderPixelWidth == pixelWidth
                && mRenderPixelHeight == pixelHeight && mStarted) {
            return;
        }

        mRenderPixelWidth = pixelWidth;
        mRenderPixelHeight = pixelHeight;

        initPlayViewGlView(mContext, pixelWidth, pixelHeight);
    }

    private void initPlayViewGlView(Context context, int pixelWidth, int pixelHeight) {
        GLSurfaceView glSurfaceView = new GLSurfaceView(context);
        mPlayView = new PlayView(glSurfaceView);
        mPlayView.init(pixelWidth, pixelHeight);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        glSurfaceView.setLayoutParams(lp);

        this.removeAllViews();
        this.addView(glSurfaceView);
    }

    private synchronized void initAudioPlayer(YoloLiveObs.AudioSpecInfo audioInfo) {
        // CHECKSTYLE:OFF
        if (mHasInitAudio) {
            return;
        }

        int channelConfig = audioInfo.mNChannels == 1 ? AudioFormat.CHANNEL_OUT_MONO
                : AudioFormat.CHANNEL_OUT_STEREO;
        int audioConfig = AudioFormat.ENCODING_PCM_16BIT;
        if (audioInfo.mBitDepth == 16) {

        } else if (audioInfo.mBitDepth == 8) {
            audioConfig = AudioFormat.ENCODING_PCM_8BIT;
        } else {
            throw new IllegalArgumentException(
                    "dont support bit depth of " + audioInfo.mBitDepth);
        }
        mStreamAudioPlayer.init(audioInfo.mSampleRate, channelConfig, audioConfig,
                AUDIO_FRAME_BUFFER_SIZE);

        mHasInitAudio = true;
        // CHECKSTYLE:ON
    }

    public YoloWatchView(Context context) {
        super(context);
        initOnConstructor(context, null);
    }

    public YoloWatchView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initOnConstructor(context, attrs);
    }

    public YoloWatchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initOnConstructor(context, attrs);
    }

    @android.support.annotation.RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    public YoloWatchView(Context context, AttributeSet attrs, int defStyleAttr,
                         int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initOnConstructor(context, attrs);
    }

    public void initOnConstructor(Context context, AttributeSet attrs) {
        mContext = context;
        mStreamAudioPlayer = StreamAudioPlayer.getInstance();

        if (attrs != null) {
            TypedArray a = context.getTheme()
                    .obtainStyledAttributes(attrs, R.styleable.YoloWatchView, 0, 0);

            try {
                mFillClip = a.getBoolean(R.styleable.YoloWatchView_clipFill, true);
            } finally {
                a.recycle();
            }
        }
    }

}
