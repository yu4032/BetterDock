package com.hellovoid.liquiddock;

import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;

/** Controls workstation/laptop Dock divider lines independently from Dock geometry. */
final class DockDividerHook {
    private DockDividerHook() {}
    private static int channel(int v) { return Math.max(0, Math.min(v, 255)); }

    static void install(ClassLoader classLoader) {
        try {
            HookUtil.hookMethod(classLoader,
                    "com.miui.home.launcher.hotseats.HotSeatsListContentAdapter$LineViewHolder",
                    "bindView",
                    chain -> {
                        Object result = chain.proceed(chain.getArgs().toArray(new Object[0]));
                        LiquidDockConfig.Divider cfg = LiquidDockConfig.load().divider;
                        if (!cfg.enabled) return result;

                        View line = (View) HookUtil.invoke(chain.getThisObject(), "getContent");
                        if (line == null) return result;
                        ViewGroup.MarginLayoutParams lp =
                                (ViewGroup.MarginLayoutParams) line.getLayoutParams();
                        float density = line.getResources().getDisplayMetrics().density;

                        if (cfg.explicitMode) {
                            // New mode: every configured value is literal; zero is valid.
                            lp.width = Math.max(0, Math.round(cfg.widthDp * density));
                            int parentH = ((View) line.getParent()).getHeight();
                            if (parentH <= 0) parentH = lp.height > 0 ? lp.height : line.getHeight();
                            if (parentH > 0) {
                                int targetH = Math.round(parentH * cfg.heightPercent / 100f);
                                lp.topMargin = (parentH - targetH) / 2
                                        + Math.round(cfg.yOffsetDp * density);
                                lp.height = targetH;
                            }
                            line.setLayoutParams(lp);
                            line.setBackgroundColor(Color.argb(channel(cfg.alpha),
                                    channel(cfg.colorR), channel(cfg.colorG), channel(cfg.colorB)));
                        } else {
                            // Compatibility mode: pre-split configs used zero as "system default".
                            if (cfg.widthDp != 0f)
                                lp.width = Math.round(cfg.widthDp * density);
                            if (cfg.heightPercent != 0f) {
                                int parentH = ((View) line.getParent()).getHeight();
                                if (parentH <= 0) parentH = lp.height > 0 ? lp.height : line.getHeight();
                                if (parentH > 0) {
                                    int targetH = Math.round(parentH * cfg.heightPercent / 100f);
                                    lp.topMargin = (parentH - targetH) / 2;
                                    lp.height = targetH;
                                }
                            }
                            if (cfg.yOffsetDp != 0f)
                                lp.topMargin += Math.round(cfg.yOffsetDp * density);
                            line.setLayoutParams(lp);
                            boolean hasColor = cfg.colorR != 0 || cfg.colorG != 0 || cfg.colorB != 0;
                            boolean hasAlpha = cfg.alpha != 0;
                            if (hasColor || hasAlpha) {
                                int color = Color.rgb(hasColor ? channel(cfg.colorR) : 255,
                                        hasColor ? channel(cfg.colorG) : 255,
                                        hasColor ? channel(cfg.colorB) : 255);
                                if (hasAlpha) color = Color.argb(channel(cfg.alpha),
                                        Color.red(color), Color.green(color), Color.blue(color));
                                line.setBackgroundColor(color);
                            }
                        }
                        return result;
                    });
            MainHook.log("[DC] dock divider hook installed");
        } catch (Throwable e) {
            MainHook.log("[DC] dock divider hook unavailable: " + e);
        }
    }
}
