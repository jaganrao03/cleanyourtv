package com.gathin.tvhome;

/** Upper-sky clock positioning for the photo screensaver, with a small burn-in-safe drift. */
final class LockClockPlacement {
    private LockClockPlacement() { }

    /** Pull both line boxes' empty space out so the ink-to-ink date/time separation is gapPx. */
    static int timeTopMargin(float timeAscent, float timeInkTop,
                             float dateDescent, float dateInkBottom, float gapPx) {
        float topBlank = Math.max(0f, -timeAscent + timeInkTop);
        float dateBottomBlank = Math.max(0f, dateDescent - dateInkBottom);
        return Math.round(gapPx - topBlank - dateBottomBlank);
    }

    /** Keep the lock-screen time under 40% of the display while never enlarging a smaller face. */
    static float clockWidthScale(float measuredTextWidth, float screenWidth) {
        if (measuredTextWidth <= 0f || screenWidth <= 0f) return 1f;
        return Math.min(1f, screenWidth * 0.40f / measuredTextWidth);
    }

    static float[] wallpaper(int screenW, int screenH, int clockW, int clockH,
                              float driftX, float driftY) {
        int maxX = Math.max(0, screenW - clockW);
        int maxY = Math.max(0, screenH - clockH);
        // Keep the photograph's mountain in view; avoid a perfectly stationary OLED clock.
        return new float[]{maxX * (0.48f + 0.04f * driftX),
                           maxY * (0.02f * driftY)};
    }
}
