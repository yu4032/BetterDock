package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** Bridges MIUI DragContainer child lifecycle into the one shared launcher drag-glass overlay. */
final class MiuixLauncherDragOverlayHook {
    private static final String TAG = "[DC][DragGlassHook]";
    private static final int MAX_SOURCE_SEARCH_DEPTH = 8;
    private static final Map<View, DragRecord> ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static boolean installed;

    private static final class DragRecord {
        final WeakReference<View> sourceRef;
        final WeakReference<LauncherGlassSinkView> staticSinkRef;

        DragRecord(View source, LauncherGlassSinkView staticSink) {
            sourceRef = new WeakReference<>(source);
            staticSinkRef = new WeakReference<>(staticSink);
        }
    }

    private static final class ResolvedSource {
        final View source;
        final LauncherGlassDragState.Kind kind;
        final float cornerRadiusPx;
        final LauncherGlassSinkView staticSink;

        ResolvedSource(
                View source,
                LauncherGlassDragState.Kind kind,
                float cornerRadiusPx,
                LauncherGlassSinkView staticSink) {
            this.source = source;
            this.kind = kind;
            this.cornerRadiusPx = cornerRadiusPx;
            this.staticSink = staticSink;
        }
    }

    private MiuixLauncherDragOverlayHook() {}

    static boolean install(ClassLoader classLoader, LiquidDockConfig runtimeConfig) {
        if (installed) return true;
        if (runtimeConfig == null || !runtimeConfig.enabled || !runtimeConfig.glass.enabled
                || !runtimeConfig.glass.folderEnabled) {
            return false;
        }
        LiquidDockConfig.Glass glassConfig = runtimeConfig.glass;
        try {
            Method onViewAdded = ViewGroup.class.getDeclaredMethod("onViewAdded", View.class);
            onViewAdded.setAccessible(true);
            HookUtil.hook(onViewAdded, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object owner = chain.getThisObject();
                if (owner instanceof ViewGroup && args.length > 0 && args[0] instanceof View) {
                    ViewGroup parent = (ViewGroup) owner;
                    if (parent.getClass().getName().contains("DragContainer")) {
                        onDragChildAdded(parent, (View) args[0], glassConfig);
                    }
                }
                return result;
            });

            Method onViewRemoved = ViewGroup.class.getDeclaredMethod("onViewRemoved", View.class);
            onViewRemoved.setAccessible(true);
            HookUtil.hook(onViewRemoved, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object owner = chain.getThisObject();
                if (owner instanceof ViewGroup && args.length > 0 && args[0] instanceof View) {
                    ViewGroup parent = (ViewGroup) owner;
                    if (parent.getClass().getName().contains("DragContainer")) {
                        onDragChildRemoved((View) args[0]);
                    }
                }
                return result;
            });

            installed = true;
            MainHook.log(TAG + " DragContainer overlay hook installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook unavailable: " + error);
            return false;
        }
    }

    private static void onDragChildAdded(
            ViewGroup dragContainer, View child, LiquidDockConfig.Glass glassConfig) {
        if (!isDragContainer(dragContainer) || child == null || ACTIVE.containsKey(child)) return;
        child.postOnAnimation(() -> beginWhenReady(child, glassConfig, 0));
    }

    private static void beginWhenReady(
            View child, LiquidDockConfig.Glass glassConfig, int attempt) {
        if (child == null || ACTIVE.containsKey(child) || attempt > 4) return;
        ResolvedSource resolved = resolveSource(child);
        if (resolved == null || resolved.source == null || !resolved.source.isAttachedToWindow()
                || resolved.source.getWidth() <= 0 || resolved.source.getHeight() <= 0) {
            child.postOnAnimation(() -> beginWhenReady(child, glassConfig, attempt + 1));
            return;
        }
        boolean active = LauncherGlassDragOverlay.begin(
                resolved.source,
                glassConfig,
                child,
                resolved.kind,
                resolved.cornerRadiusPx);
        if (!active) {
            child.postOnAnimation(() -> beginWhenReady(child, glassConfig, attempt + 1));
            return;
        }
        if (resolved.staticSink != null) resolved.staticSink.setSuppressedByDrag(true);
        ACTIVE.put(child, new DragRecord(resolved.source, resolved.staticSink));
        MainHook.log(TAG + " begin kind=" + resolved.kind
                + " child=" + child.getClass().getSimpleName()
                + " source=" + resolved.source.getClass().getSimpleName());
    }

    private static void onDragChildRemoved(View child) {
        if (child == null) return;
        DragRecord record = ACTIVE.remove(child);
        if (record == null) return;
        View source = record.sourceRef.get();
        LauncherGlassDragOverlay.end(source, child);
        LauncherGlassSinkView staticSink = record.staticSinkRef.get();
        if (staticSink != null) staticSink.setSuppressedByDrag(false);
        MainHook.log(TAG + " end child=" + child.getClass().getSimpleName());
    }

    private static ResolvedSource resolveSource(View child) {
        View folder = findFolderIcon(child, 0);
        if (folder != null) {
            View material = readFolderMaterial(folder);
            if (material != null) {
                return new ResolvedSource(
                        material,
                        LauncherGlassDragState.Kind.FOLDER,
                        resolveCornerRadius(material, LauncherGlassDragState.Kind.FOLDER),
                        findStaticSink(material));
            }
        }

        View widget = findWidgetView(child, 0);
        if (widget != null) {
            return new ResolvedSource(
                    widget,
                    LauncherGlassDragState.Kind.WIDGET,
                    resolveCornerRadius(widget, LauncherGlassDragState.Kind.WIDGET),
                    null);
        }

        return new ResolvedSource(
                child,
                LauncherGlassDragState.Kind.ICON,
                resolveCornerRadius(child, LauncherGlassDragState.Kind.ICON),
                null);
    }

    private static View findFolderIcon(View view, int depth) {
        if (view == null || depth > MAX_SOURCE_SEARCH_DEPTH) return null;
        if (view.getClass().getName().endsWith(".FolderIcon")) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            View found = findFolderIcon(group.getChildAt(index), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private static View findWidgetView(View view, int depth) {
        if (view == null || depth > MAX_SOURCE_SEARCH_DEPTH) return null;
        String name = view.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        if (name.contains("appwidgethostview") || name.contains("widget")) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int index = 0; index < group.getChildCount(); index++) {
            View found = findWidgetView(group.getChildAt(index), depth + 1);
            if (found != null) return found;
        }
        return null;
    }

    private static View readFolderMaterial(View folder) {
        try {
            Object value = HookUtil.getField(folder, "mIconImageView");
            return value instanceof View ? (View) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static LauncherGlassSinkView findStaticSink(View material) {
        if (material == null || !(material.getParent() instanceof ViewGroup)) return null;
        ViewGroup parent = (ViewGroup) material.getParent();
        for (int index = 0; index < parent.getChildCount(); index++) {
            View child = parent.getChildAt(index);
            if (child instanceof LauncherGlassSinkView
                    && ((LauncherGlassSinkView) child).materialHost() == material) {
                return (LauncherGlassSinkView) child;
            }
        }
        return null;
    }

    private static float resolveCornerRadius(View source, LauncherGlassDragState.Kind kind) {
        float radius = readCornerRadius(source);
        if (Float.isFinite(radius) && radius > 0f) return radius;
        float min = Math.min(Math.max(1, source.getWidth()), Math.max(1, source.getHeight()));
        return kind == LauncherGlassDragState.Kind.WIDGET ? min * 0.08f : min * 0.22f;
    }

    private static float readCornerRadius(View source) {
        if (source == null) return Float.NaN;
        try {
            Field field = findField(source.getClass(), "mCornerRadius");
            field.setAccessible(true);
            Object value = field.get(source);
            if (value instanceof Number) return Math.max(0f, ((Number) value).floatValue());
        } catch (Throwable ignored) {}
        Drawable background = source.getBackground();
        if (background instanceof GradientDrawable) {
            return Math.max(0f, ((GradientDrawable) background).getCornerRadius());
        }
        return Float.NaN;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException ignored) { current = current.getSuperclass(); }
        }
        throw new NoSuchFieldException(name);
    }

    private static boolean isDragContainer(ViewGroup parent) {
        return parent != null && parent.getClass().getName().contains("DragContainer");
    }
}
