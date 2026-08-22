package com.hellovoid.liquiddock;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

/** Bridges MIUI's real DragView lifecycle into the one shared launcher drag-glass overlay. */
final class MiuixLauncherDragOverlayHook {
    private static final String TAG = "[DC][DragGlassHook]";
    private static final String DRAG_VIEW = "com.miui.home.launcher.DragView";
    private static final int MAX_READY_ATTEMPTS = 4;
    private static final Map<View, DragRecord> ACTIVE =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Class<?>, Boolean> STATIC_SUPPRESSION_HOOKED =
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

    private static final class Metadata {
        final LauncherGlassDragState.Kind kind;
        final View radiusSource;
        final View folderMaterial;

        Metadata(LauncherGlassDragState.Kind kind, View radiusSource, View folderMaterial) {
            this.kind = kind != null ? kind : LauncherGlassDragState.Kind.ICON;
            this.radiusSource = radiusSource;
            this.folderMaterial = folderMaterial;
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
                    View child = (View) args[0];
                    if (child instanceof LauncherGlassSinkView) {
                        installStaticSinkDragSuppression((LauncherGlassSinkView) child);
                    }
                    if (isDragContainer(parent) && isActualDragView(child)) {
                        onDragChildAdded(parent, child, glassConfig);
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
                    View child = (View) args[0];
                    if (isDragContainer(parent) && isActualDragView(child)) {
                        onDragChildRemoved(child);
                    }
                }
                return result;
            });

            installed = true;
            MainHook.log(TAG + " DragView overlay hook installed");
            return true;
        } catch (Throwable error) {
            MainHook.log(TAG + " hook unavailable: " + error);
            return false;
        }
    }

    private static void onDragChildAdded(
            ViewGroup dragContainer, View child, LiquidDockConfig.Glass glassConfig) {
        if (!isDragContainer(dragContainer) || !isActualDragView(child)
                || ACTIVE.containsKey(child)) return;
        child.postOnAnimation(() -> beginWhenReady(child, glassConfig, 0));
    }

    private static void beginWhenReady(
            View child, LiquidDockConfig.Glass glassConfig, int attempt) {
        if (child == null || !isActualDragView(child) || ACTIVE.containsKey(child)
                || attempt > MAX_READY_ATTEMPTS) return;
        ResolvedSource resolved = resolveSource(child);
        if (resolved == null || !child.isAttachedToWindow()
                || child.getWidth() <= 0 || child.getHeight() <= 0) {
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
                + " child=" + child.getClass().getSimpleName());
    }

    private static void onDragChildRemoved(View child) {
        if (child == null || !isActualDragView(child)) return;
        DragRecord record = ACTIVE.remove(child);
        if (record == null) return;
        View source = record.sourceRef.get();
        LauncherGlassDragOverlay.end(source, child);
        LauncherGlassSinkView staticSink = record.staticSinkRef.get();
        if (staticSink != null) staticSink.setSuppressedByDrag(false);
        MainHook.log(TAG + " end child=" + child.getClass().getSimpleName());
    }

    /**
     * DragView is always the geometry authority. Metadata only chooses optics/source type and,
     * when MIUI exposes the original FolderIcon, gives us a second suppression path for its
     * static sink. No original workspace View is used as the moving geometry source.
     */
    private static ResolvedSource resolveSource(View child) {
        if (!isActualDragView(child)) return null;
        Metadata metadata = resolveMetadata(child);
        View radiusSource = metadata.radiusSource != null ? metadata.radiusSource : child;
        LauncherGlassSinkView staticSink = metadata.folderMaterial != null
                ? findStaticSink(metadata.folderMaterial) : null;
        return new ResolvedSource(
                child,
                metadata.kind,
                resolveCornerRadius(radiusSource, metadata.kind, child),
                staticSink);
    }

    private static Metadata resolveMetadata(View dragView) {
        Metadata fromTag = classifyMetadata(dragView.getTag());
        if (fromTag != null) return fromTag;

        // Classification happens once when a DragView is added, never in the frame loop.
        Class<?> current = dragView.getClass();
        while (current != null && current != View.class) {
            Field[] fields;
            try { fields = current.getDeclaredFields(); }
            catch (Throwable ignored) { break; }
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    Metadata metadata = classifyMetadata(field.get(dragView));
                    if (metadata != null) return metadata;
                } catch (Throwable ignored) {}
            }
            current = current.getSuperclass();
        }
        return new Metadata(LauncherGlassDragState.Kind.ICON, null, null);
    }

    private static Metadata classifyMetadata(Object value) {
        if (value == null) return null;
        if (value instanceof View) {
            View view = (View) value;
            String viewName = view.getClass().getName();
            if (viewName.endsWith(".FolderIcon")) {
                View material = readFolderMaterial(view);
                return new Metadata(LauncherGlassDragState.Kind.FOLDER,
                        material != null ? material : view, material);
            }
            String lowerView = viewName.toLowerCase(Locale.ROOT);
            if (lowerView.contains("appwidgethostview") || lowerView.contains("widget")
                    || lowerView.contains("gadget")) {
                return new Metadata(LauncherGlassDragState.Kind.WIDGET, view, null);
            }
            Object tag = view.getTag();
            if (tag != null && tag != value) {
                Metadata tagged = classifyMetadata(tag);
                if (tagged != null) return tagged;
            }
        }

        String name = value.getClass().getName().toLowerCase(Locale.ROOT);
        if (name.contains("folderinfo")) {
            return new Metadata(LauncherGlassDragState.Kind.FOLDER, null, null);
        }
        if (name.contains("gadgetinfo") || name.contains("appwidgetinfo")
                || name.contains("widgetinfo") || name.contains("widgetprovider")
                || name.contains("miuiwidget")) {
            return new Metadata(LauncherGlassDragState.Kind.WIDGET, null, null);
        }
        if (name.contains("shortcutinfo") || name.contains("applicationinfo")
                || name.endsWith(".iteminfo")) {
            return new Metadata(LauncherGlassDragState.Kind.ICON, null, null);
        }
        return null;
    }

    private static void installStaticSinkDragSuppression(LauncherGlassSinkView sink) {
        if (sink == null) return;
        View material = sink.materialHost();
        if (material == null) return;
        Class<?> materialClass = material.getClass();
        synchronized (STATIC_SUPPRESSION_HOOKED) {
            if (STATIC_SUPPRESSION_HOOKED.containsKey(materialClass)) return;
            STATIC_SUPPRESSION_HOOKED.put(materialClass, Boolean.TRUE);
        }
        try {
            Method dragAlpha = HookUtil.findMethodExact(materialClass,
                    "onDragContainerBgAnimAlpha",
                    new Class<?>[]{Boolean.TYPE, Boolean.TYPE});
            HookUtil.hook(dragAlpha, chain -> {
                Object[] args = chain.getArgs().toArray(new Object[0]);
                Object result = chain.proceed(args);
                Object target = chain.getThisObject();
                if (target instanceof View && args.length > 1 && args[1] instanceof Boolean) {
                    boolean normalState = (Boolean) args[1];
                    LauncherGlassSinkView current = findStaticSink((View) target);
                    if (current != null) current.setSuppressedByDrag(!normalState);
                }
                return result;
            });
            MainHook.log(TAG + " static drag suppression hooked "
                    + materialClass.getSimpleName());
        } catch (NoSuchMethodException ignored) {
            // Not every material exposes this callback. DragView remains the moving overlay source.
        } catch (Throwable error) {
            MainHook.log(TAG + " static drag suppression unavailable for "
                    + materialClass.getSimpleName() + ": " + error);
        }
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

    private static float resolveCornerRadius(
            View preferredSource, LauncherGlassDragState.Kind kind, View dragView) {
        float radius = readCornerRadius(preferredSource);
        if (Float.isFinite(radius) && radius > 0f) return radius;
        View source = dragView != null ? dragView : preferredSource;
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

    private static boolean isActualDragView(View child) {
        if (child == null) return false;
        Class<?> current = child.getClass();
        while (current != null && current != View.class) {
            if (DRAG_VIEW.equals(current.getName())) return true;
            current = current.getSuperclass();
        }
        return false;
    }

    private static boolean isDragContainer(ViewGroup parent) {
        return parent != null && parent.getClass().getName().contains("DragContainer");
    }
}
