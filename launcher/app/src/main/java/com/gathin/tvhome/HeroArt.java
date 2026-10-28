package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * The cinematic shelf's backdrop: an app's artwork reduced to 48×27 pixels, saturated and smoothed,
 * then stretched over the screen by its ImageView. Bilinear filtering of an image that small is a
 * blur that costs one tiny texture; a real blur would mean RenderScript or a shader pass per frame,
 * which a 1 GB TV cannot spare.
 */
final class HeroArt {
    static final int W = 48, H = 27;
    private static final float SATURATION = 2.1f;

    private HeroArt() {
    }

    /** The softened artwork, about 5 KB; null when there is nothing to draw. */
    static Bitmap from(Drawable live) {
        if (live == null) return null;
        // Draw a private copy: changing the bounds of the drawable a tile shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        Bitmap big = Bitmap.createBitmap(W * 4, H * 4, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(big);
        // Cover, never letterbox: a square icon fills the 16:9 target and its overflow is cropped by the canvas.
        // Nothing is filled first; the transparent parts of an icon take its own colour below, not black.
        int[] b = cover(d.getIntrinsicWidth(), d.getIntrinsicHeight(), big.getWidth(), big.getHeight());
        d.setBounds(b[0], b[1], b[2], b[3]);
        d.draw(c);
        d.setBounds(saved);
        // Halve twice: each filtered halving averages 2×2 pixels, where one 4× step would skip most of them.
        Bitmap half = Bitmap.createScaledBitmap(big, W * 2, H * 2, true);
        big.recycle();
        Bitmap small = Bitmap.createScaledBitmap(half, W, H, true);
        half.recycle();

        int[] px = new int[W * H];
        small.getPixels(px, 0, W, 0, 0, W, H);
        if (!flatten(px)) {
            small.recycle();
            return null;
        }
        saturate(px);
        // Three box passes approximate a gaussian, so the stretched image shows no seams between pixels.
        for (int pass = 0; pass < 3; pass++) {
            px = boxBlur(px, W, H, 2, true);
            px = boxBlur(px, W, H, 2, false);
        }
        Bitmap out = small.isMutable() ? small : small.copy(Bitmap.Config.ARGB_8888, true);
        if (out != small) small.recycle();
        out.setPixels(px, 0, W, 0, 0, W, H);
        return out;
    }

    /**
     * cinematic_grid: the softened artwork as a band over the top {@code share} of a 16:9 screen. The middle rows are
     * zoomed to fill the band, its lower part fades out, and the rest is transparent, so the same full-screen
     * ImageView (fitXY) can show it. Twice the rows of {@link #from}, so the fade has room to be smooth.
     */
    static Bitmap band(Bitmap art, float share) {
        if (art == null) return null;
        int outH = H * 2;
        int bandH = Math.max(2, Math.min(outH, Math.round(outH * share)));
        // The art is 16:9 like the screen: rows covering share of its height, from the middle, fill the band.
        int rows = Math.max(1, Math.min(art.getHeight(), Math.round(art.getHeight() * share)));
        int top = (art.getHeight() - rows) / 2;
        Bitmap out = Bitmap.createBitmap(W, outH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawBitmap(art, new Rect(0, top, art.getWidth(), top + rows), new RectF(0, 0, W, bandH),
                new Paint(Paint.FILTER_BITMAP_FLAG));
        // The fade starts at 45% of a short band. A full-bleed band (cinematic_band near 100, which is the
        // tvOS look) has to keep the artwork all the way down behind the rows, so it only fades near the
        // bottom; fading at 45% of the full screen would leave the lower rows on the bare wallpaper.
        float fadeFrom = bandH * Math.max(0.45f, share - 0.25f);
        Paint fade = new Paint();
        fade.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        fade.setShader(new LinearGradient(0, fadeFrom, 0, bandH, 0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
        c.drawRect(0, fadeFrom, W, bandH, fade);
        return out;
    }

    /**
     * The bounds that make a {@code iw}×{@code ih} drawable cover a {@code tw}×{@code th} target: scaled by the larger
     * of the two ratios, centred, so it overflows on one axis and never leaves a bar. A drawable without a size
     * (a colour) is stretched over the target. Left, top, right, bottom.
     */
    static int[] cover(int iw, int ih, int tw, int th) {
        if (iw <= 0 || ih <= 0) return new int[]{0, 0, tw, th};
        float scale = Math.max(tw / (float) iw, th / (float) ih);
        int w = Math.round(iw * scale), h = Math.round(ih * scale);
        int left = (tw - w) / 2, top = (th - h) / 2;
        return new int[]{left, top, left + w, top + h};
    }

    /**
     * Lays the pixels over the artwork's own average colour, weighted by opacity, so an icon's transparent corners
     * read as the app's colour instead of black. False when nothing at all was drawn.
     */
    static boolean flatten(int[] px) {
        long r = 0, g = 0, b = 0, a = 0;
        for (int p : px) {
            int alpha = p >>> 24;
            r += (long) ((p >> 16) & 0xFF) * alpha;
            g += (long) ((p >> 8) & 0xFF) * alpha;
            b += (long) (p & 0xFF) * alpha;
            a += alpha;
        }
        if (a == 0) return false;
        int mr = (int) (r / a), mg = (int) (g / a), mb = (int) (b / a);
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int alpha = p >>> 24, rest = 255 - alpha;
            px[i] = 0xFF000000 | ((((p >> 16) & 0xFF) * alpha + mr * rest) / 255) << 16
                    | ((((p >> 8) & 0xFF) * alpha + mg * rest) / 255) << 8 | (((p & 0xFF) * alpha + mb * rest) / 255);
        }
        return true;
    }

    private static void saturate(int[] px) {
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int r = Color.red(p), g = Color.green(p), b = Color.blue(p);
            float lum = 0.2126f * r + 0.7152f * g + 0.0722f * b;
            px[i] = Color.rgb(clamp(lum + (r - lum) * SATURATION), clamp(lum + (g - lum) * SATURATION),
                    clamp(lum + (b - lum) * SATURATION));
        }
    }

    private static int clamp(float v) {
        return v < 0 ? 0 : v > 255 ? 255 : Math.round(v);
    }

    /** One box blur pass along rows (horizontal) or columns, edges clamped. */
    private static int[] boxBlur(int[] src, int w, int h, int radius, boolean horizontal) {
        int[] dst = new int[src.length];
        int lines = horizontal ? h : w, length = horizontal ? w : h, span = 2 * radius + 1;
        for (int line = 0; line < lines; line++) {
            for (int i = 0; i < length; i++) {
                int r = 0, g = 0, b = 0;
                for (int k = -radius; k <= radius; k++) {
                    int j = Math.max(0, Math.min(length - 1, i + k));
                    int p = horizontal ? src[line * w + j] : src[j * w + line];
                    r += Color.red(p);
                    g += Color.green(p);
                    b += Color.blue(p);
                }
                dst[horizontal ? line * w + i : i * w + line] = Color.rgb(r / span, g / span, b / span);
            }
        }
        return dst;
    }
}
