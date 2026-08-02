package com.weicheng.soundhaptic.audio;

/** Pure PCM synthesis for the supported motor-inspired envelopes. */
public final class WaveformSynthesizer {
    public static final int SAMPLE_RATE = 48_000;

    private WaveformSynthesizer() { }

    public static short[] synthesize(double frequencyHz, int durationMs, double strength,
            HapticShape shape) {
        int frameCount = Math.max(1, SAMPLE_RATE * durationMs / 1000);
        short[] output = new short[frameCount];
        double gain = 0.82 * clamp(strength, 0.01, 1.0);
        double ermPhase = 0.0;

        for (int index = 0; index < frameCount; index++) {
            double time = index / (double) SAMPLE_RATE;
            double phase = 2.0 * Math.PI * frequencyHz * time;
            double progress = frameCount == 1 ? 1.0 : index / (double) (frameCount - 1);
            double sample = switch (shape) {
                case LRA_TWO_CYCLE -> softenedSquare(phase, 1.72)
                        * edgeWindow(progress, 0.18, 0.38);
                case LRA_ACTIVE_BRAKE -> lraActiveBrake(phase, progress);
                case DAMPED_TWO_CYCLE -> softenedSquare(phase, 1.50)
                        * Math.exp(-1.75 * progress)
                        * edgeWindow(progress, 0.10, 0.20);
                case BIPHASIC_BRAKE -> biphasicBrake(progress);
                case SINE_REFERENCE -> Math.sin(phase) * edgeWindow(progress, 0.18, 0.38);
                case ERM_START_STOP -> 0.0; // Calculated below because it carries phase state.
            };
            if (shape == HapticShape.ERM_START_STOP) {
                double speed = ermSpeedEnvelope(progress);
                double instantaneousFrequency = frequencyHz * (0.55 + 0.45 * speed);
                ermPhase += 2.0 * Math.PI * instantaneousFrequency / SAMPLE_RATE;
                sample = speed * (0.92 * Math.sin(ermPhase)
                        + 0.08 * Math.sin(ermPhase * 3.0 + 0.25));
            }

            double limited = Math.tanh(sample * gain * 1.15) / Math.tanh(1.15);
            output[index] = (short) Math.round(
                    clamp(limited, -1.0, 1.0) * Short.MAX_VALUE);
        }
        return output;
    }

    private static double softenedSquare(double phase, double hardness) {
        return Math.tanh(hardness * Math.sin(phase)) / Math.tanh(hardness);
    }

    private static double lraActiveBrake(double phase, double progress) {
        double polarity;
        if (progress < 0.08) {
            polarity = smoothStep(progress / 0.08);
        } else if (progress < 0.60) {
            polarity = lerp(1.0, 0.62, (progress - 0.08) / 0.52);
        } else if (progress < 0.72) {
            polarity = lerp(0.62, -0.82, smoothStep((progress - 0.60) / 0.12));
        } else if (progress < 0.91) {
            polarity = lerp(-0.82, -0.36, (progress - 0.72) / 0.19);
        } else {
            polarity = lerp(-0.36, 0.0, smoothStep((progress - 0.91) / 0.09));
        }
        return softenedSquare(phase, 1.62) * polarity;
    }

    private static double biphasicBrake(double progress) {
        final double split = 0.42;
        if (progress < split) {
            return Math.sin(Math.PI * progress / split);
        }
        double negativeAmplitude = split / (1.0 - split);
        return -negativeAmplitude
                * Math.sin(Math.PI * (progress - split) / (1.0 - split));
    }

    private static double ermSpeedEnvelope(double progress) {
        if (progress < 0.52) {
            return smoothStep(progress / 0.52);
        }
        if (progress < 0.72) {
            return 1.0;
        }
        return Math.pow(Math.max(0.0, 1.0 - (progress - 0.72) / 0.28), 1.7);
    }

    private static double edgeWindow(double progress, double attack, double release) {
        double envelope = 1.0;
        if (progress < attack) {
            envelope *= smoothStep(progress / Math.max(0.0001, attack));
        }
        if (progress > 1.0 - release) {
            envelope *= smoothStep((1.0 - progress) / Math.max(0.0001, release));
        }
        return envelope;
    }

    private static double smoothStep(double value) {
        double x = clamp(value, 0.0, 1.0);
        return x * x * (3.0 - 2.0 * x);
    }

    private static double lerp(double start, double end, double amount) {
        return start + (end - start) * clamp(amount, 0.0, 1.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
