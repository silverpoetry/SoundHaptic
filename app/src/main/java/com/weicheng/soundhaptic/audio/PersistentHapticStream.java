package com.weicheng.soundhaptic.audio;

import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Process;
import android.util.Log;

import com.weicheng.soundhaptic.PulsePlayer;

import java.util.ArrayDeque;
import java.util.Arrays;

/**
 * Continuously mixed system_server stream. It avoids HyperOS dropping very short static tracks
 * before its mixer has raised their gain.
 */
public final class PersistentHapticStream implements AutoCloseable {
    private static final int CHUNK_FRAMES = WaveformSynthesizer.SAMPLE_RATE / 200; // 5 ms
    private static final int MAX_QUEUE_SIZE = 64;

    private final AudioTrackFactory trackFactory;
    private final Object queueLock = new Object();
    private final ArrayDeque<short[]> queue = new ArrayDeque<>();

    private volatile boolean running;
    private volatile boolean closed;
    private AudioTrack track;
    private Thread writerThread;
    private short[] activePulse;
    private int activeOffset;

    public PersistentHapticStream(AudioTrackFactory trackFactory) {
        this.trackFactory = trackFactory;
    }

    public void start(float gain, boolean forceSpeaker) {
        if (closed || running) {
            return;
        }
        AudioTrack created = null;
        try {
            int minimum = AudioTrack.getMinBufferSize(WaveformSynthesizer.SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            int bufferBytes = Math.max(minimum, CHUNK_FRAMES * Short.BYTES * 4);
            created = trackFactory.createStream(bufferBytes, forceSpeaker, gain);

            short[] prime = new short[Math.max(CHUNK_FRAMES, bufferBytes / Short.BYTES)];
            int primed = created.write(prime, 0, prime.length, AudioTrack.WRITE_BLOCKING);
            if (primed < 0) {
                throw new IllegalStateException("system stream prime returned " + primed);
            }
            created.play();

            track = created;
            running = true;
            AudioTrack writerTrack = created;
            writerThread = new Thread(() -> writeLoop(writerTrack),
                    "SoundHapticSystemStream");
            writerThread.setDaemon(true);
            writerThread.start();
            Log.i(PulsePlayer.TAG, "persistent system haptic stream started buffer="
                    + bufferBytes + " bytes");
        } catch (Throwable error) {
            running = false;
            track = null;
            AudioTrackFactory.release(created);
            Log.e(PulsePlayer.TAG, "Unable to start persistent system haptic stream", error);
        }
    }

    public void enqueue(short[] pcm) {
        synchronized (queueLock) {
            while (queue.size() >= MAX_QUEUE_SIZE) {
                queue.removeFirst();
            }
            queue.addLast(pcm);
        }
    }

    public void apply(float gain, boolean forceSpeaker) {
        AudioTrack current = track;
        if (current == null) {
            return;
        }
        current.setVolume(gain);
        if (forceSpeaker) {
            trackFactory.bindSpeaker(current);
        } else {
            current.setPreferredDevice(null);
        }
    }

    public void clear() {
        synchronized (queueLock) {
            queue.clear();
            activePulse = null;
            activeOffset = 0;
        }
    }

    public void stop() {
        running = false;
        clear();
        Thread writer = writerThread;
        writerThread = null;
        if (writer != null) {
            writer.interrupt();
        }
        AudioTrack current = track;
        track = null;
        AudioTrackFactory.release(current);
        if (current != null) {
            Log.i(PulsePlayer.TAG, "persistent system haptic stream stopped");
        }
    }

    private void writeLoop(AudioTrack writerTrack) {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO);
        short[] chunk = new short[CHUNK_FRAMES];
        try {
            while (running && !closed && writerTrack == track) {
                Arrays.fill(chunk, (short) 0);
                fill(chunk);
                int offset = 0;
                while (offset < chunk.length && running && !closed) {
                    int written = writerTrack.write(chunk, offset, chunk.length - offset,
                            AudioTrack.WRITE_BLOCKING);
                    if (written < 0) {
                        throw new IllegalStateException("system stream write returned " + written);
                    }
                    offset += written;
                }
            }
        } catch (Throwable error) {
            if (running && !closed) {
                Log.e(PulsePlayer.TAG, "Persistent system haptic stream failed", error);
            }
        }
    }

    private void fill(short[] output) {
        synchronized (queueLock) {
            int outputOffset = 0;
            while (outputOffset < output.length) {
                if (activePulse == null) {
                    activePulse = queue.pollFirst();
                    activeOffset = 0;
                    if (activePulse == null) {
                        return;
                    }
                }
                int remaining = activePulse.length - activeOffset;
                int count = Math.min(remaining, output.length - outputOffset);
                System.arraycopy(activePulse, activeOffset, output, outputOffset, count);
                outputOffset += count;
                activeOffset += count;
                if (activeOffset >= activePulse.length) {
                    activePulse = null;
                    activeOffset = 0;
                }
            }
        }
    }

    @Override
    public void close() {
        closed = true;
        stop();
    }
}
