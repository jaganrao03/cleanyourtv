package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Plain JVM tests for the cinematic shelf's artwork: cover-cropping and the colour under transparent pixels. */
public class HeroArtTest {

    @Test
    public void aSquareIconCoversTheWideTargetAndIsCroppedTopAndBottom() {
        // 192/100 beats 108/100: 192×192, centred, 42px cut off above and below.
        assertArrayEquals(new int[]{0, -42, 192, 150}, HeroArt.cover(100, 100, 192, 108));
    }

    @Test
    public void aBannerOfTheSameShapeFillsTheTargetExactly() {
        assertArrayEquals(new int[]{0, 0, 192, 108}, HeroArt.cover(320, 180, 192, 108));
    }

    @Test
    public void aWidePanoramaIsCroppedAtTheSides() {
        // 108/100 beats 192/400: 432×108, centred, 120px cut off at each side.
        assertArrayEquals(new int[]{-120, 0, 312, 108}, HeroArt.cover(400, 100, 192, 108));
    }

    @Test
    public void aDrawableWithoutASizeIsStretched() {
        assertArrayEquals(new int[]{0, 0, 192, 108}, HeroArt.cover(-1, -1, 192, 108));
    }

    @Test
    public void transparentPixelsTakeTheArtworksColourNotBlack() {
        int[] px = {0xFFFF0000, 0x00000000};
        assertTrue(HeroArt.flatten(px));
        assertEquals(0xFFFF0000, px[0]);
        assertEquals(0xFFFF0000, px[1]);
    }

    @Test
    public void nothingDrawnMeansNoArtwork() {
        assertFalse(HeroArt.flatten(new int[]{0, 0, 0}));
    }
}
