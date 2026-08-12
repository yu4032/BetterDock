package com.hellovoid.liquiddock;

import android.graphics.Color;
import android.graphics.PorterDuff;
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
                                lp.width = Math.round(cfg.dividerWidthDp * scale);
                            }

                            if (cfg.dividerHeightScale != 0f) {
                                int parentH = ((View) line.getParent()).getHeight();
                                if (parentH <= 0) {
                                    parentH = lp.height > 0 ? lp.height : line.getHeight();
                                }
                                if (parentH > 0) {
                                    int targetH = Math.round(parentH * cfg.dividerHeightScale);
                                    lp.topMargin = (parentH - targetH) / 2;
                                    lp.height = targetH;
                                }
                            }

                            line.setLayoutParams(lp);
                        }

                        // --- color / alpha ---
                        int color = cfg.dividerColor;
                        int alpha = cfg.dividerAlpha;
                        if (color != -1 || alpha != -1) {
                            View itemView = (View) chain.getThisObject();
                            View content = (View) HookUtil.invoke(
                                    chain.getThisObject(), "getContent");
                            View target = content != null ? content : itemView;
                            int actualColor = color != -1 ? color
                                    : Color.WHITE;
                            int actualAlpha = alpha != -1 ? alpha : 255;
                            actualColor = Color.argb(actualAlpha,
                                    Color.red(actualColor),
                                    Color.green(actualColor),
                                    Color.blue(actualColor));
                            target.getBackground().setColorFilter(
                                    actualColor, PorterDuff.Mode.SRC_ATOP);
                        }

                        return result;
                    });
            MainHook.log("[DC] dock divider hook installed");
        } catch (Throwable e) {
            MainHook.log("[DC] dock divider hook unavailable: " + e);
        }
    }
}
