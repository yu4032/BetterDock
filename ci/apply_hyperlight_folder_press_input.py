from pathlib import Path

path = Path("src/main/java/com/hellovoid/liquiddock/MiuixFolderGlassHook.java")
text = path.read_text()

old_hook = '''        // The inspected HyperOS build declares this override on FolderIcon itself. Hook only that
        // declaration so LiquidDock can never intercept View.dispatchTouchEvent process-wide.
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
'''
new_hook = '''        // HyperLight observes View.dispatchTouchEvent and filters inside the callback instead of
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

old_update = '''    private static void updateFolderPressAfterDispatch(ViewGroup owner, MotionEvent event) {
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
new_update = '''    private static void updateFolderPressFromMotionEvent(ViewGroup owner, MotionEvent event) {
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

for label, old, new in [
    ("dispatch hook", old_hook, new_hook),
    ("motion event state", old_update, new_update),
]:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one anchor, found {count}")
    text = text.replace(old, new, 1)

path.write_text(text)
print("Applied HyperLight-style direct MotionEvent folder press input")
