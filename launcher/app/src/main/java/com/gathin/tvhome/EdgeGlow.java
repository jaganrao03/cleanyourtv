package com.gathin.tvhome;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/**
 * edge_glow: the left and right screen edges glow in the focused app's colour, each a horizontal gradient from
 * the colour at the edge to nothing a fifth of the screen inward. It sits under the tiles and never takes focus or
 * touch. Two gradient rects per frame, redrawn only when the colour changes (a short fade), never on a timer.
 */
public final class EdgeGlow extends View {

    /** How far inward each side's glow reaches, as a share of the view's width. */
    static final float REACH = 0.20f;
    /** The glow's opacity at the edge at edge_glow 100. */
    static final float MAX_ALPHA = 0.34f;
    private static final int FADE_MS = 180;

    private final Paint left = new Paint();
    private final Paint right = new Paint();
    private final ArgbEvaluator argb = new ArgbEvaluator();
    private ValueAnimator fade;
    /** The colour's opacity at the edge, 0 to 255, from edge_glow. */
    private int edgeAlpha;
    /** The colour drawn now (opaque), 0 before the first tile is focused. */
    private int color;
    /** The colour the gradients were built for, so a frame without a change reuses them. */
    private int shaderColor;
    private int shaderWidth;

    public EdgeGlow(Context context) {
        this(context, null);
    }

    public EdgeGlow(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(false);
        setClickable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        setWillNotDraw(false);
    }

    /** The colour's opacity at the screen edge for an edge_glow strength of 0 to 100. */
    static int alphaFor(int strength) {
        int s = Math.max(0, Math.min(100, strength));
        return Math.round(255 * MAX_ALPHA * s / 100f);
    }

    /** edge_glow, 0 to 100; 0 hides the view. */
    void setStrength(int strength) {
        edgeAlpha = alphaFor(strength);
        shaderWidth = 0;
        setVisibility(edgeAlpha > 0 ? VISIBLE : GONE);
        invalidate();
    }

    /** Re-tints to this app's accent, fading over about 180ms unless {@code animate} is false. */
    void tint(int accent, boolean animate) {
        if (edgeAlpha <= 0) return;
        // 0 means nothing to tint by: the edges fade out instead of turning opaque black.
        int target = (accent & 0xFFFFFF) == 0 ? 0 : accent | 0xFF000000;
        if (fade != null) fade.cancel();
        fade = null;
        if (target == color) return;
        if (!animate || color == 0) {
            color = target;
            invalidate();
            return;
        }
        final int from = color;
        fade = ValueAnimator.ofFloat(0f, 1f);
        fade.setDuration(FADE_MS);
        fade.addUpdateListener(a -> {
            color = (int) argb.evaluate(a.getAnimatedFraction(), from, target);
            invalidate();
        });
        fade.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (fade != null) fade.cancel();
        fade = null;
        super.onDetachedFromWindow();
    }

    @Override
    public boolean hasOverlappingRendering() {
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (color == 0 || edgeAlpha <= 0 || w <= 0 || h <= 0) return;
        float reach = w * REACH;
        if (color != shaderColor || w != shaderWidth) {
            int solid = (color & 0x00FFFFFF) | (edgeAlpha << 24);
            int clear = color & 0x00FFFFFF;
            left.setShader(new LinearGradient(0f, 0f, reach, 0f, solid, clear, Shader.TileMode.CLAMP));
            right.setShader(new LinearGradient(w, 0f, w - reach, 0f, solid, clear, Shader.TileMode.CLAMP));
            shaderColor = color;
            shaderWidth = w;
        }
        canvas.drawRect(0f, 0f, reach, h, left);
        canvas.drawRect(w - reach, 0f, w, h, right);
    }
}
