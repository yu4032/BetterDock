package com.hellovoid.liquiddock;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

/**
 * Controls the vertical divider lines in the workstation/laptop Dock.
 *
 * HyperOS 3 draws each divider as an independent RecyclerView item via
 * {@code HotSeatsListContentAdapter$LineViewHolder.bindView()}.  After the
 * system sizes the line, we override the parameters that matter.
 */
final class DockDividerHook {

    private DockDividerHook() {}

    private static int channel(int v) { return Math.max(0, Math.min(v, 255)); }

    static void install(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.hotseats.HotSeatsListContentAdapter$LineViewHolder",
                    "bindView",
                    chain -> {
                        Object result = chain.proceed(
                                chain.getArgs().toArray(new Object[0]));

                        LiquidDockConfig.Dock cfg = LiquidDockConfig.load().dock;

                        // --- line width / height / margin ---
                        View line = (View) HookUtil.invoke(
                                chain.getThisObject(), "getContent");
                        if (line != null) {
                            ViewGroup.MarginLayoutParams lp =
                                    (ViewGroup.MarginLayoutParams) line.getLayoutParams();
                            float scale = line.getResources().getDisplayMetrics().density;

                            if (cfg.dividerWidthDp != 0) {
                                // GUI stores tenths of dp (range 0-160 → 0-16 dp)
                                lp.width = Math.round(cfg.dividerWidthDp / 10f * scale);
                            }

                            if (cfg.dividerHeightScale != 0f) {
                                int parentH = ((View) line.getParent()).getHeight();
                                if (parentH <= 0) {
                                    parentH = lp.height > 0 ? lp.height : line.getHeight();
                                }
                                if (parentH > 0) {
                                    float frac = cfg.dividerHeightScale / 100f;
                                    int targetH = Math.round(parentH * frac);
                                    lp.topMargin = (parentH - targetH) / 2;
                                    lp.height = targetH;
                                }
                            }

                            line.setLayoutParams(lp);

                            // Y offset (apply after height/margin calc, independent of scale)
                            if (cfg.dividerYOffset != 0f) {
                                int yPx = Math.round(cfg.dividerYOffset / 10f * scale);
                                lp.topMargin += yPx;
                                line.setLayoutParams(lp);
                            }
                        }

                        // --- color / alpha ---
                        int r = cfg.dividerColorR;
                        int g = cfg.dividerColorG;
                        int b = cfg.dividerColorB;
                        int alpha = cfg.dividerAlpha;
                        boolean hasColor = r != 0 || g != 0 || b != 0;
                        boolean hasAlpha = alpha != 0;
                        if (hasColor || hasAlpha) {
                            int actualColor = Color.rgb(
                                    hasColor ? channel(r) : 255,
                                    hasColor ? channel(g) : 255,
                                    hasColor ? channel(b) : 255);
                            if (hasAlpha) {
                                actualColor = Color.argb(
                                        channel(alpha),
                                        Color.red(actualColor),
                                        Color.green(actualColor),
                                        Color.blue(actualColor));
                            }
                            // Prefer setBackgroundColor (works on any View with
                            // any drawable), fall back to tinting the background.
                            line.setBackgroundColor(actualColor);
                        }

                        return result;
                    });
            MainHook.log("[DC] dock divider hook installed");
        } catch (Throwable e) {
            MainHook.log("[DC] dock divider hook unavailable: " + e);
        }
    }
}
