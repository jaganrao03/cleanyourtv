package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextClock;
import android.util.TypedValue;

import java.io.File;
import java.util.Random;


/** A personal wallpaper's clock-focus transition; every clock stroke remains in front of the photo. */
final class DepthClockOverlay {
    private final FrameLayout root;
    private final View header;
    private final View headerClock;
    private final FrameLayout layer;
    private final LinearLayout clockGroup;
    /** Scrim, grain and vignette over the photo: the lock screen's own texture, faded in with the clock. */
    private final View texture;
    private final boolean animate;
    private boolean focused;
    /** Layout retries left when focus lands before Home has been measured. */
    private int waits;

    static DepthClockOverlay create(Activity activity, FrameLayout root, Config config) {
        // Any wallpaper collection: one picture or a slideshow, the clock sits over whichever is showing.
        if (!config.focusClockFirst || !config.homeClock || !config.wallpaperEnabled
                || config.wallpapers.isEmpty()) {
            Log.i("TvHome.Depth", "off: clockFirst=" + config.focusClockFirst
                    + " homeClock=" + config.homeClock + " enabled=" + config.wallpaperEnabled
                    + " count=" + config.wallpapers.size());
            return null;
        }
        DepthClockOverlay overlay = new DepthClockOverlay(activity, root, activity.findViewById(R.id.clock),
                activity.findViewById(R.id.leftBlock), config.animations && !config.reducedMotion);
        // After Home's own focus setup has run: attach last, and honour a clock that is already focused.
        root.post(overlay::attach);
        return overlay;
    }

    private void attach() {
        headerClock.setOnFocusChangeListener((view, hasFocus) -> setFocused(hasFocus));
        // No manual layout here: the group's position must come from the layer's own layout pass,
        // otherwise it stays parked at 0,0 instead of centring.
        if (headerClock.isFocused()) setFocused(true);
    }

    /**
     * The iPhone lock-screen face once the font is installed beside the wallpapers (files/fonts/), else the
     * theme's own bundled font, so Home still draws a clock without it.
     */
    private static Typeface lockScreenFace(Activity activity, String name, int fallback) {
        File dir = activity.getExternalFilesDir(null);
        File file = dir == null ? null : new File(new File(dir, "fonts"), name);
        if (file != null && file.isFile()) {
            try {
                return Typeface.createFromFile(file);
            } catch (RuntimeException e) {
                Log.w("TvHome.Depth", "unreadable clock font " + name);
            }
        }
        return UiFont.get(activity, fallback);
    }

    private DepthClockOverlay(Activity activity, FrameLayout root, View headerClock, View header,
                              boolean animate) {
        this.root = root;
        this.header = header;
        this.headerClock = headerClock;
        this.animate = animate;
        layer = new FrameLayout(activity);
        layer.setFocusable(false);
        layer.setClickable(false);
        layer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        // INVISIBLE keeps the centered clock measured between focus changes.
        layer.setVisibility(View.INVISIBLE);
        // Index 1: over the wallpaper but under the sky tint and every piece of Home UI.
        root.addView(layer, 1, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        texture = new Texture(activity);
        layer.addView(texture, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        clockGroup = new LinearLayout(activity);
        clockGroup.setOrientation(LinearLayout.VERTICAL);
        clockGroup.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams clockParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        // iPhone lock-screen composition: the block sits in the upper middle of the photo, not against the
        // top edge, and starts below the header pills so a wide time never runs under them.
        clockParams.topMargin = Math.round(activity.getResources().getDisplayMetrics().heightPixels * 0.10f);
        layer.addView(clockGroup, clockParams);
        clockGroup.setAlpha(0f);

        TextClock date = new TextClock(activity);
        CharSequence dateFormat = activity.getString(R.string.date_format_home);
        date.setFormat12Hour(dateFormat);
        date.setFormat24Hour(dateFormat);
        date.setTextColor(0xF2FFFFFF);
        date.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        date.setTypeface(lockScreenFace(activity, "SF-Pro-Text-Semibold.otf", R.string.font_medium));
        // The lock screen's text carries a soft shadow so it survives a bright photo behind it.
        date.setShadowLayer(12f * density(activity), 0f, 4f * density(activity), 0x59000000);
        date.setGravity(Gravity.CENTER);
        clockGroup.addView(date);
        TextClock time = new TextClock(activity);
        time.setFormat12Hour("h:mm");
        time.setFormat24Hour("HH:mm");
        // A pale, luminous SF face, not a slab of frosted glass. The image never masks a digit.
        time.setTextColor(0xF2FFFFFF);
        // Lock-screen scale: about 38% of the screen's width, the size Home has always looked right at.
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 190);
        time.setTypeface(lockScreenFace(activity, "SF-Pro-Display-Light.otf", R.string.font_regular));
        // The TV's real font metrics can differ from the emulator's at the same 190sp size.
        // Cap the widest tabular time to 40% of its display without changing its height.
        time.setTextScaleX(LockClockPlacement.clockWidthScale(
                time.getPaint().measureText("23:58"), activity.getResources().getDisplayMetrics().widthPixels));
        // SF Pro's own digit spacing, and tabular figures so the time keeps its width minute to minute.
        time.setLetterSpacing(0f);
        time.setFontFeatureSettings("tnum");
        time.setIncludeFontPadding(false);
        time.setShadowLayer(20f * density(activity), 0f, 5f * density(activity), 0xA6000000);
        // SF Pro's line box carries ~0.356em of air above the digits, which drops the time halfway down the
        // photo. Lift the digits until they leave a lock-screen gap under the date rather than touching it.
        float gap = 14f * density(activity);
        time.setTranslationY(-(0.356f * time.getTextSize() - gap));
        clockGroup.addView(time);
    }

    private static float density(Activity activity) {
        return activity.getResources().getDisplayMetrics().density;
    }

    private void setFocused(boolean hasFocus) {
        focused = hasFocus;
        Log.i("TvHome.Depth", "focus=" + hasFocus + " rootW=" + root.getWidth() + " clockW=" + clockGroup.getWidth());
        root.post(() -> {
            if (root.getWidth() == 0 || clockGroup.getWidth() == 0) {
                // The clock can take focus before Home is measured; try again on the next frames.
                if (waits++ < 8) root.postDelayed(() -> setFocused(focused), 150);
                return;
            }
            waits = 0;
            int[] small = new int[2];
            int[] large = new int[2];
            headerClock.getLocationOnScreen(small);
            clockGroup.getLocationOnScreen(large);
            // Calculate from the *untranslated* center; otherwise a second focus after scrolling drifts.
            float startX = small[0] + headerClock.getWidth() / 2f
                    - (large[0] - clockGroup.getTranslationX() + clockGroup.getWidth() / 2f);
            float startY = small[1] + headerClock.getHeight() / 2f
                    - (large[1] - clockGroup.getTranslationY() + clockGroup.getHeight() / 2f);
            float startScale = Math.min(1f, headerClock.getWidth() / (float) clockGroup.getWidth());
            clockGroup.setPivotX(clockGroup.getWidth() / 2f);
            clockGroup.setPivotY(clockGroup.getHeight() / 2f);
            clockGroup.animate().cancel();
            header.animate().cancel();
            if (hasFocus) {
                layer.setVisibility(View.VISIBLE);
                texture.animate().alpha(1f).setDuration(animate ? 380 : 0).start();
                if (clockGroup.getAlpha() == 0f) {
                    clockGroup.setTranslationX(startX);
                    clockGroup.setTranslationY(startY);
                    clockGroup.setScaleX(startScale);
                    clockGroup.setScaleY(startScale);
                }
                if (animate) {
                    header.animate().alpha(0f).setDuration(280).start();
                    clockGroup.animate().translationX(0f).translationY(0f).scaleX(1f).scaleY(1f)
                            .alpha(1f).setDuration(380).start();
                } else {
                    header.setAlpha(0f);
                    clockGroup.setTranslationX(0f);
                    clockGroup.setTranslationY(0f);
                    clockGroup.setScaleX(1f);
                    clockGroup.setScaleY(1f);
                    clockGroup.setAlpha(1f);
                }
            } else if (animate) {
                header.animate().alpha(1f).setDuration(280).start();
                texture.animate().alpha(0f).setDuration(300).start();
                clockGroup.animate().translationX(startX).translationY(startY)
                        .scaleX(startScale).scaleY(startScale).alpha(0f).setDuration(300)
                        .withEndAction(() -> { if (!focused) layer.setVisibility(View.INVISIBLE); }).start();
            } else {
                header.setAlpha(1f);
                clockGroup.setAlpha(0f);
                texture.setAlpha(0f);
                layer.setVisibility(View.INVISIBLE);
            }
        });
    }

    /**
     * What makes the photo behind the clock look designed instead of bare: a soft scrim darkening the top
     * where the time sits, a gentle vignette that pulls the eye to the middle, and a film grain over both so
     * the gradients never band on a big panel.
     */
    private static final class Texture extends View {
        private static Bitmap grain;
        private final Paint scrim = new Paint(Paint.DITHER_FLAG);
        private final Paint vignette = new Paint(Paint.DITHER_FLAG);
        private final Paint noise = new Paint(Paint.DITHER_FLAG);

        Texture(Context context) {
            super(context);
            setFocusable(false);
            setClickable(false);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            setAlpha(0f);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldWidth, int oldHeight) {
            scrim.setShader(new LinearGradient(0, 0, 0, h,
                    new int[]{0x99000000, 0x59000000, 0x00000000},
                    new float[]{0f, 0.30f, 0.66f}, Shader.TileMode.CLAMP));
            vignette.setShader(new RadialGradient(w / 2f, h * 0.40f, Math.max(w, h) * 0.78f,
                    new int[]{0x00000000, 0x00000000, 0x40000000},
                    new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
            noise.setShader(new BitmapShader(grain(getContext()), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT));
            noise.setAlpha(16);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), scrim);
            canvas.drawRect(0, 0, getWidth(), getHeight(), vignette);
            canvas.drawRect(0, 0, getWidth(), getHeight(), noise);
        }

        /** One 160px tile of soft luminance noise, made once and repeated: grain, not a visible pattern. */
        private static synchronized Bitmap grain(Context context) {
            if (grain != null) return grain;
            int size = 160;
            int[] pixels = new int[size * size];
            Random random = new Random(7);
            for (int i = 0; i < pixels.length; i++) {
                int value = random.nextInt(256);
                pixels[i] = 0xFF000000 | (value << 16) | (value << 8) | value;
            }
            grain = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888);
            return grain;
        }
    }
}
