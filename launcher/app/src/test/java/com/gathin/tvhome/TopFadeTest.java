package com.gathin.tvhome;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Plain JVM tests for fade_rows: how tall the fade at the grid's top edge is for a given scroll. */
public class TopFadeTest {

    @Test
    public void theDockAtRestIsUntouched() {
        assertEquals(0f, TopFade.height(0, 72f), 0f);
        assertEquals(0f, TopFade.height(-5, 72f), 0f);   // an overscroll never fades anything
    }

    @Test
    public void theFadeGrowsInWithTheFirstPixelsOfScrolling() {
        assertEquals(10f, TopFade.height(10, 72f), 0f);
        assertEquals(71f, TopFade.height(71, 72f), 0f);
    }

    @Test
    public void thenItStaysAtItsFullLength() {
        assertEquals(72f, TopFade.height(72, 72f), 0f);
        assertEquals(72f, TopFade.height(5000, 72f), 0f);
    }

    @Test
    public void theBandStopsAtTheParkedLineWithoutAFocusedTile() {
        assertEquals(300f, TopFade.bandEdge(300f, Float.NaN, 12f), 0f);
    }

    @Test
    public void theBandNeverReachesTheFocusedTilesZoomedTop() {
        // Parked: the zoomed tile rises 4px above the line, the erasing ends its reach above that.
        assertEquals(284f, TopFade.bandEdge(300f, 296f, 12f), 0f);
        // Scrolling back in from above (Up from the second row below the dock): the focused row is never cut.
        assertEquals(88f, TopFade.bandEdge(300f, 100f, 12f), 0f);
        // A tile well below the line leaves the band where it parks.
        assertEquals(300f, TopFade.bandEdge(300f, 520f, 12f), 0f);
    }
}
