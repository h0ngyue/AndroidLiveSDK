package com.yolo.livesdk.widget.publish;

/**
 * Created by guyacong on 2015/7/21.
 */
public class SenderControlEvent {
    private SenderControlEvent() {

    }

    public static class StopPublish {

    }

    public static class ChangeAudio {

        boolean isAudioOn;

        public ChangeAudio(boolean isAudioOn) {
            this.isAudioOn = isAudioOn;
        }

        public boolean isAudioOn() {
            return isAudioOn;
        }
    }

    public static class ChangeCamera {
    }

    public static class ChangeLight {

    }

    public static class ChangeMirror {

    }

    public static class SwitchUseBeauty {

    }


    public static class ResumeRender {

    }

    public static class PauseRender {

        private boolean shouldClear = true;

        public PauseRender() {
        }

        public PauseRender(boolean shouldClear) {
            this.shouldClear = shouldClear;
        }

        public boolean shouldClear() {
            return this.shouldClear;
        }
    }
}
