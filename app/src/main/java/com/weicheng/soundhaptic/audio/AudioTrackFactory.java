package com.weicheng.soundhaptic.audio;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import com.weicheng.soundhaptic.PulsePlayer;

/** Centralizes low-latency track construction, speaker routing and safe release. */
@SuppressWarnings("deprecation")
public final class AudioTrackFactory {
    private final Context context;
    private final AudioAttributes attributes;
    private final AudioFormat format;

    public AudioTrackFactory(Context context) {
        this.context = context.getApplicationContext();
        attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setFlags(AudioAttributes.FLAG_AUDIBILITY_ENFORCED
                        | AudioAttributes.FLAG_LOW_LATENCY)
                .build();
        format = new AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(WaveformSynthesizer.SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build();
    }

    public AudioTrack createStatic(short[] pcm, boolean forceSpeaker, float gain) {
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(pcm.length * Short.BYTES)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build();
        boolean success = false;
        try {
            if (forceSpeaker) {
                bindSpeaker(track);
            }
            track.setVolume(gain);
            int written = track.write(pcm, 0, pcm.length, AudioTrack.WRITE_BLOCKING);
            if (written != pcm.length) {
                throw new IllegalStateException(
                        "PCM write returned " + written + "/" + pcm.length);
            }
            success = true;
            return track;
        } finally {
            if (!success) {
                track.release();
            }
        }
    }

    public AudioTrack createStream(int bufferBytes, boolean forceSpeaker, float gain) {
        AudioTrack track = new AudioTrack.Builder()
                .setAudioAttributes(attributes)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufferBytes)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build();
        if (forceSpeaker && !bindSpeaker(track)) {
            track.release();
            throw new IllegalStateException("built-in speaker unavailable for system stream");
        }
        track.setVolume(gain);
        return track;
    }

    public boolean bindSpeaker(AudioTrack track) {
        AudioManager manager = context.getSystemService(AudioManager.class);
        if (manager == null) {
            return false;
        }
        AudioDeviceInfo speaker = null;
        for (AudioDeviceInfo device : manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                speaker = device;
                break;
            }
        }
        if (speaker == null) {
            Log.w(PulsePlayer.TAG, "Built-in speaker is not published yet; route binding deferred");
            return false;
        }
        AudioDeviceInfo preferred = track.getPreferredDevice();
        if (preferred != null && preferred.getId() == speaker.getId()) {
            return true;
        }
        boolean accepted = track.setPreferredDevice(speaker);
        Log.println(accepted ? Log.INFO : Log.WARN, PulsePlayer.TAG,
                accepted ? "module AudioTrack rebound to built-in speaker id=" + speaker.getId()
                        : "Built-in speaker preference was rejected by AudioTrack");
        return accepted;
    }

    public static void release(AudioTrack track) {
        if (track == null) {
            return;
        }
        try {
            track.stop();
        } catch (IllegalStateException ignored) {
            // The track may not have reached PLAYSTATE_PLAYING yet.
        }
        track.release();
    }
}
