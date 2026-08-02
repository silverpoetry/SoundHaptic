package com.weicheng.soundhaptic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.weicheng.soundhaptic.audio.HapticShape;
import com.weicheng.soundhaptic.audio.WaveformSynthesizer;

import org.junit.Test;

public final class HapticSettingsTest {
    @Test
    public void releaseDefaultsMatchValidatedTabletProfile() {
        HapticSettings.Snapshot defaults = HapticSettings.defaults();

        assertTrue(defaults.enabled);
        assertEquals(31, defaults.volumePercent);
        assertEquals(252, defaults.frequencyHz);
        assertEquals(16, defaults.referenceDurationMs);
        assertEquals(38, defaults.referenceStrengthPercent);
        assertEquals(HapticShape.ERM_START_STOP, defaults.shape);
        assertTrue(defaults.forceSpeaker);
    }

    @Test
    public void synthesizerUsesExactPcmDurationAndSafeAmplitude() {
        short[] pcm = WaveformSynthesizer.synthesize(
                252.0, 16, 0.38, HapticShape.ERM_START_STOP);

        assertEquals(768, pcm.length);
        int peak = 0;
        for (short sample : pcm) {
            peak = Math.max(peak, Math.abs((int) sample));
        }
        assertTrue(peak > 0);
        assertTrue(peak <= Short.MAX_VALUE);
    }
}
