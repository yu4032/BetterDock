from pathlib import Path

path = Path("src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java")
text = path.read_text()

old_touch = '''        // HyperLight observes View.dispatchTouchEvent and filters inside the callback instead of
        // relying on FolderIcon's pressed drawable state. Keep the original event path untouched.
        Method dispatchTouchEvent = View.class.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
        dispatchTouchEvent.setAccessible(true);
        HookUtil.hook(dispatchTouchEvent, chain -> {
            Object owner = chain.getThisObject();
            Object[] args = chain.getArgs().toArray(new Object[0]);
            if (folderIcon.isInstance(owner)
                    && owner instanceof ViewGroup
                    && args.length > 0
                    && args[0] instanceof MotionEvent) {
                updateFolderPressFromMotionEvent((ViewGroup) owner, (MotionEvent) args[0]);
            }
            return chain.proceed(args);
        });
'''
new_touch = '''        // FolderIcon's own dispatch path is sufficient on this HyperOS build. Observe only that
        // declaration so we do not add process-wide touch-hook overhead.
        Method dispatchTouchEvent = folderIcon.getDeclaredMethod("dispatchTouchEvent", MotionEvent.class);
        dispatchTouchEvent.setAccessible(true);
        HookUtil.hook(dispatchTouchEvent, chain -> {
            Object[] args = chain.getArgs().toArray(new Object[0]);
            Object result = chain.proceed(args);
            Object owner = chain.getThisObject();
            if (owner instanceof ViewGroup && args.length > 0 && args[0] instanceof MotionEvent) {
                updateFolderPressAfterDispatch((ViewGroup) owner, (MotionEvent) args[0]);
            }
            return result;
        });

        // Long-press / drag rendering can bypass ItemIcon.setIconImageView and write a folder
        // drawable straight into mIconImageView from FolderIcon2x2.drawChild. Once that ImageView
        // is claimed by LiquidDock its material plate must stay transparent permanently; the
        // sibling LauncherGlassSinkView owns the actual glass rendering.
        Method setImageDrawable = ImageView.class.getDeclaredMethod("setImageDrawable", Drawable.class);
        setImageDrawable.setAccessible(true);
        HookUtil.hook(setImageDrawable, chain -> {
            Object target = chain.getThisObject();
            Object[] drawableArgs = chain.getArgs().toArray(new Object[0]);
            if (target instanceof View
                    && drawableArgs.length > 0
                    && claimedSink((View) target) != null) {
                Drawable requested = drawableArgs[0] instanceof Drawable
                        ? (Drawable) drawableArgs[0] : null;
                if (!isTransparentColorDrawable(requested)) {
                    Drawable current = ((ImageView) target).getDrawable();
                    drawableArgs[0] = isTransparentColorDrawable(current)
                            ? current : new ColorDrawable(Color.TRANSPARENT);
                }
            }
            return chain.proceed(drawableArgs);
        });
'''

old_update = '''    private static void updateFolderPressFromMotionEvent(ViewGroup owner, MotionEvent event) {
        if (owner == null || event == null) return;
        int action = event.getActionMasked();
        boolean pressed;
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_MOVE:
                pressed = true;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                pressed = false;
                break;
            default:
                return;
        }

        LauncherGlassSinkView sink = resolveOwnerSink(owner);
        if (sink == null) return;
        try {
            Object value = HookUtil.getField(owner, "mIconImageView");
            if (!(value instanceof View)) return;
            View material = (View) value;
            int width = material.getWidth();
            int height = material.getHeight();
            if (width <= 0 || height <= 0) return;
            int[] location = new int[2];
            material.getLocationOnScreen(location);
            float x = (event.getRawX() - location[0]) / width;
            // Android local Y grows downward; Prismal glow coordinates grow upward.
            float y = 1f - (event.getRawY() - location[1]) / height;
            sink.setPressInteraction(pressed, x, y);
        } catch (Throwable error) {
            MainHook.log(TAG + " press bridge failed: " + error);
        }
    }
'''
new_update = '''    private static void updateFolderPressAfterDispatch(ViewGroup owner, MotionEvent event) {
        if (owner == null || event == null) return;
        LauncherGlassSinkView sink = resolveOwnerSink(owner);
        if (sink == null) return;
        try {
            Object value = HookUtil.getField(owner, "mIconImageView");
            if (!(value instanceof View)) return;
            View material = (View) value;
            int width = material.getWidth();
            int height = material.getHeight();
            if (width <= 0 || height <= 0) return;
            int[] location = new int[2];
            material.getLocationOnScreen(location);
            float x = (event.getRawX() - location[0]) / width;
            // Android local Y grows downward; Prismal glow coordinates grow upward.
            float y = 1f - (event.getRawY() - location[1]) / height;
            sink.setPressInteraction(owner.isPressed(), x, y);
        } catch (Throwable error) {
            MainHook.log(TAG + " press bridge failed: " + error);
        }
    }
'''

helper_anchor = '''    private static void makeMaterialTransparent(View material) {
'''
helper = '''    private static boolean isTransparentColorDrawable(Drawable drawable) {
        return drawable instanceof ColorDrawable
                && ((ColorDrawable) drawable).getColor() == Color.TRANSPARENT;
    }

'''

for label, old, new in [
    ("touch hook", old_touch, new_touch),
    ("press callback", old_update, new_update),
]:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    text = text.replace(old, new, 1)

if helper not in text:
    count = text.count(helper_anchor)
    if count != 1:
        raise SystemExit(f"helper anchor: expected one, found {count}")
    text = text.replace(helper_anchor, helper + helper_anchor, 1)

path.write_text(text)
print("Restored scoped FolderIcon press input and fenced claimed material drawable writes")
