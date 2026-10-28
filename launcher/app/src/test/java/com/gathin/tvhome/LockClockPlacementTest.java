package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class LockClockPlacementTest {
    @Test public void visualClockGapAccountsForTheRealFaceAscent() {
        // Representative synthetic line-box metrics; the TV render remains the acceptance check.
        // Negative ascent and ink top: the empty space ABOVE the digit is -ascent+inkTop.
        assertEquals(-52, LockClockPlacement.timeTopMargin(-250f, -142f, 16f, 8f, 64f));
        // A different font's line box keeps the same requested visible gap.
        assertEquals(36, LockClockPlacement.timeTopMargin(-100f, -80f, 16f, 8f, 64f));
        assertEquals(64, LockClockPlacement.timeTopMargin(-40f, -48f, 0f, 4f, 64f));
    }
    @org.junit.Test public void wideTimeIsCappedToFortyPercentOfScreen() {
        org.junit.Assert.assertEquals(0.8f, LockClockPlacement.clockWidthScale(1000f, 2000f), 0.001f);
        org.junit.Assert.assertEquals(1f, LockClockPlacement.clockWidthScale(760f, 2000f), 0.001f);
        org.junit.Assert.assertEquals(1f, LockClockPlacement.clockWidthScale(0f, 2000f), 0.001f);
    }

    @Test public void wallpaperClockStaysCenteredInTheOpenSkyWhileDrifting() {
        float[] left = LockClockPlacement.wallpaper(1920, 1080, 400, 220, 0f, 0f);
        float[] right = LockClockPlacement.wallpaper(1920, 1080, 400, 220, 1f, 1f);
        assertTrue(left[0] >= 1920 * 0.35f && right[0] + 400 <= 1920 * 0.65f);
        assertEquals(0f, left[1], 0.0001f);
        assertTrue(right[1] <= 1080 * 0.025f);
        assertTrue(right[1] + 220 <= 1080 * 0.33f);
        assertTrue(right[0] > left[0]);
        assertTrue(right[1] > left[1]);
    }

    @Test public void wallpaperClockIsClampedToSmallOrZeroBounds() {
        float[] p = LockClockPlacement.wallpaper(100, 50, 200, 120, 1f, 1f);
        assertEquals(0f, p[0], 0.0001f);
        assertEquals(0f, p[1], 0.0001f);
    }
}
