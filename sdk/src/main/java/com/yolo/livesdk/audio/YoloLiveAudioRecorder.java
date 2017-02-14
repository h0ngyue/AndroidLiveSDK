package com.yolo.livesdk.audio;

import com.github.piasy.rxandroidaudio.StreamAudioRecorder;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by Piasy{github.com/Piasy} on 16/2/25.
 */
public final class YoloLiveAudioRecorder {

    private final StreamAudioRecorder mStreamAudioRecorder;

    private final AtomicBoolean mIsMuted;

    private final byte[] mMutedBuffer;

    private YoloLiveAudioRecorder(StreamAudioRecorder streamAudioRecorder) {
        // singleton
        mStreamAudioRecorder = streamAudioRecorder;
        mIsMuted = new AtomicBoolean(false);
        mMutedBuffer = new byte[StreamAudioRecorder.DEFAULT_BUFFER_SIZE];
    }

    private static final class YLLiveAudioRecorderHolder {

        private static final YoloLiveAudioRecorder INSTANCE = new YoloLiveAudioRecorder(
                StreamAudioRecorder.getInstance());
    }

    public static YoloLiveAudioRecorder getInstance() {
        return YLLiveAudioRecorderHolder.INSTANCE;
    }


    public boolean start(int sampleRate, int channelConfig, int bitDepthConfig,
            final StreamAudioRecorder.AudioDataCallback audioDataCallback) {
        return mStreamAudioRecorder.start(sampleRate, channelConfig,
                bitDepthConfig, StreamAudioRecorder.DEFAULT_BUFFER_SIZE,
                new StreamAudioRecorder.AudioDataCallback() {
                    @Override
                    public void onAudioData(byte[] data, int size) {
                        if (mIsMuted.get()) {
                            audioDataCallback.onAudioData(mMutedBuffer, size);
                        } else {
                            audioDataCallback.onAudioData(data, size);
                        }
                    }

                    @Override
                    public void onError() {
                        audioDataCallback.onError();
                    }
                });
    }

    public void stop() {
        mStreamAudioRecorder.stop();
    }

    public void mute() {
        mIsMuted.compareAndSet(false, true);
    }

    public void unMute() {
        mIsMuted.compareAndSet(true, false);
    }
}
