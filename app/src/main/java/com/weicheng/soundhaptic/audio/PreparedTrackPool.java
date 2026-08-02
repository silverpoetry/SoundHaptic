package com.weicheng.soundhaptic.audio;

import android.media.AudioTrack;
import android.util.Log;

import com.weicheng.soundhaptic.PulsePlayer;

import java.util.ArrayList;

/** Reusable static AudioTrack pool for low-latency UI-process feedback. */
public final class PreparedTrackPool implements AutoCloseable {
    private static final int TRACK_COUNT = 4;

    private final AudioTrackFactory trackFactory;
    private final ArrayList<AudioTrack> tracks = new ArrayList<>();
    private AudioTrack keepAliveTrack;
    private int nextTrack;

    public PreparedTrackPool(AudioTrackFactory trackFactory) {
        this.trackFactory = trackFactory;
    }

    public void prepare(short[] initialPcm, boolean forceSpeaker, float gain) {
        if (!tracks.isEmpty()) {
            return;
        }
        for (int index = 0; index < TRACK_COUNT; index++) {
            try {
                tracks.add(trackFactory.createStatic(initialPcm, forceSpeaker, gain));
            } catch (Throwable error) {
                Log.e(PulsePlayer.TAG, "Unable to prepare module AudioTrack " + index, error);
                break;
            }
        }
        Log.i(PulsePlayer.TAG, "prepared " + tracks.size()
                + " module track: configurable semantic haptic profiles");
    }

    /** Returns the selected pool index, or -1 when preparation failed completely. */
    public int play(short[] pcm, boolean forceSpeaker, float gain) {
        if (tracks.isEmpty()) {
            return -1;
        }
        int index = nextTrack++ % tracks.size();
        try {
            replay(index, pcm, forceSpeaker, gain);
        } catch (Throwable error) {
            Log.w(PulsePlayer.TAG, "Prepared track failed; rebuilding track=" + index, error);
            AudioTrackFactory.release(tracks.get(index));
            tracks.set(index, trackFactory.createStatic(pcm, forceSpeaker, gain));
            replay(index, pcm, forceSpeaker, gain);
            Log.i(PulsePlayer.TAG, "module pulse track recovered=" + index);
        }
        return index;
    }

    public void startKeepAlive(short[] silence, boolean forceSpeaker, float gain) {
        if (keepAliveTrack != null) {
            return;
        }
        AudioTrack created = null;
        try {
            created = trackFactory.createStatic(silence, forceSpeaker, gain);
            int loopResult = created.setLoopPoints(0, silence.length, -1);
            if (loopResult != AudioTrack.SUCCESS) {
                throw new IllegalStateException("setLoopPoints returned " + loopResult);
            }
            created.play();
            keepAliveTrack = created;
            Log.i(PulsePlayer.TAG, "speaker keep-alive started (screen on)");
        } catch (Throwable error) {
            AudioTrackFactory.release(created);
            Log.e(PulsePlayer.TAG, "Unable to start speaker keep-alive", error);
        }
    }

    public void stopKeepAlive() {
        AudioTrack current = keepAliveTrack;
        keepAliveTrack = null;
        AudioTrackFactory.release(current);
        if (current != null) {
            Log.i(PulsePlayer.TAG, "speaker keep-alive stopped (screen off)");
        }
    }

    public void apply(float gain, boolean forceSpeaker) {
        for (AudioTrack track : tracks) {
            track.setVolume(gain);
            if (forceSpeaker) {
                trackFactory.bindSpeaker(track);
            } else {
                track.setPreferredDevice(null);
            }
        }
    }

    public void stop() {
        for (AudioTrack track : tracks) {
            if (track.getPlayState() != AudioTrack.PLAYSTATE_PLAYING) {
                continue;
            }
            try {
                track.stop();
            } catch (IllegalStateException ignored) {
                // Track state may change between the query and stop call.
            }
        }
    }

    @Override
    public void close() {
        stopKeepAlive();
        for (AudioTrack track : tracks) {
            AudioTrackFactory.release(track);
        }
        tracks.clear();
    }

    private void replay(int index, short[] pcm, boolean forceSpeaker, float gain) {
        AudioTrack track = tracks.get(index);
        if (track.getState() != AudioTrack.STATE_INITIALIZED) {
            throw new IllegalStateException("AudioTrack is not initialized");
        }
        if (track.getPlayState() != AudioTrack.PLAYSTATE_STOPPED) {
            track.stop();
        }
        if (forceSpeaker && !trackFactory.bindSpeaker(track)) {
            throw new IllegalStateException("built-in speaker is unavailable for module track");
        }
        if (!forceSpeaker) {
            track.setPreferredDevice(null);
        }
        track.setVolume(gain);
        int written = track.write(pcm, 0, pcm.length, AudioTrack.WRITE_BLOCKING);
        if (written != pcm.length) {
            throw new IllegalStateException(
                    "module PCM write returned " + written + "/" + pcm.length);
        }
        int positionResult = track.setPlaybackHeadPosition(0);
        if (positionResult != AudioTrack.SUCCESS) {
            throw new IllegalStateException(
                    "setPlaybackHeadPosition returned " + positionResult);
        }
        track.play();
    }
}
