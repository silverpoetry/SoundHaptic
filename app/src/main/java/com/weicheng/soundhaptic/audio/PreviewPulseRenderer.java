package com.weicheng.soundhaptic.audio;

import android.media.AudioTrack;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Locale;

/** Renders independent static tracks used by the companion preview and module fallback path. */
public final class PreviewPulseRenderer {
    private static final String TAG = "SoundHaptic";
    private static final int MAX_ACTIVE_TRACKS = 6;

    private static final class ActiveTrack {
        final AudioTrack track;
        final long releaseAtMs;

        ActiveTrack(AudioTrack track, long releaseAtMs) {
            this.track = track;
            this.releaseAtMs = releaseAtMs;
        }
    }

    private final Handler audioHandler;
    private final AudioTrackFactory trackFactory;
    private final ArrayDeque<ActiveTrack> activeTracks = new ArrayDeque<>();

    public PreviewPulseRenderer(Handler audioHandler, AudioTrackFactory trackFactory) {
        this.audioHandler = audioHandler;
        this.trackFactory = trackFactory;
    }

    /** Must run on the shared audio thread. */
    public void play(double frequencyHz, int durationMs, int strengthPercent,
            HapticShape shape, boolean forceSpeaker, float trackGain) {
        cleanupFinishedTracks();
        while (activeTracks.size() >= MAX_ACTIVE_TRACKS) {
            AudioTrackFactory.release(activeTracks.removeFirst().track);
        }

        short[] pcm = WaveformSynthesizer.synthesize(
                frequencyHz, durationMs, strengthPercent / 100.0, shape);
        AudioTrack track = null;
        try {
            track = trackFactory.createStatic(pcm, forceSpeaker, trackGain);
            track.play();
            long releaseDelay = durationMs + 160L;
            activeTracks.addLast(new ActiveTrack(
                    track, SystemClock.uptimeMillis() + releaseDelay));
            AudioTrack trackToRelease = track;
            audioHandler.postDelayed(() -> releaseIfActive(trackToRelease), releaseDelay);
            Log.i(TAG, String.format(Locale.US,
                    "play freq=%.1fHz duration=%dms strength=%d%% shape=%s "
                            + "speaker=%s gain=%.2f",
                    frequencyHz, durationMs, strengthPercent, shape, forceSpeaker,
                    trackGain));
        } catch (Throwable error) {
            Log.e(TAG, "Unable to play haptic pulse", error);
            if (track != null) {
                AudioTrackFactory.release(track);
            }
        }
    }

    /** Must run on the shared audio thread. */
    public void stop() {
        for (ActiveTrack active : activeTracks) {
            AudioTrackFactory.release(active.track);
        }
        activeTracks.clear();
    }

    private void cleanupFinishedTracks() {
        long now = SystemClock.uptimeMillis();
        Iterator<ActiveTrack> iterator = activeTracks.iterator();
        while (iterator.hasNext()) {
            ActiveTrack active = iterator.next();
            if (active.releaseAtMs <= now) {
                iterator.remove();
                AudioTrackFactory.release(active.track);
            }
        }
    }

    private void releaseIfActive(AudioTrack target) {
        Iterator<ActiveTrack> iterator = activeTracks.iterator();
        while (iterator.hasNext()) {
            ActiveTrack active = iterator.next();
            if (active.track == target) {
                iterator.remove();
                AudioTrackFactory.release(target);
                return;
            }
        }
    }
}
