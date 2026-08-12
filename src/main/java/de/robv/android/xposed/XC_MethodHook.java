package de.robv.android.xposed;

import java.util.HashMap;
import java.util.Map;

/** Minimal source-compatibility facade backed by libxposed API101. */
public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {}
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {}

    public static class MethodHookParam {
        public Object thisObject;
        public Object[] args;

        private Object result;
        private Throwable throwable;
        private boolean returnEarly;
        private final Map<String, Object> extras = new HashMap<>();

        public Object getResult() { return result; }

        public void setResult(Object value) {
            result = value;
            throwable = null;
            returnEarly = true;
        }

        public Throwable getThrowable() { return throwable; }

        public boolean hasThrowable() { return throwable != null; }

        public void setThrowable(Throwable value) {
            throwable = value;
            returnEarly = true;
        }

        public void setObjectExtra(String key, Object value) {
            if (value == null) extras.remove(key); else extras.put(key, value);
        }

        public Object getObjectExtra(String key) { return extras.get(key); }

        public boolean isReturnEarly() { return returnEarly; }

        public void setResultFromOriginal(Object value) {
            result = value;
            throwable = null;
            returnEarly = false;
        }

        public void setThrowableFromOriginal(Throwable value) {
            throwable = value;
            returnEarly = false;
        }
    }
}
