package de.robv.android.xposed;

import com.hellovoid.liquiddock.Api101Bridge;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

/** Compatibility facade. All hooks are installed through libxposed API101. */
public final class XposedBridge {
    private XposedBridge() {}

    public static void log(String text) { Api101Bridge.log(text); }
    public static void log(Throwable error) { Api101Bridge.log("Xposed callback error", error); }

    public static Object hookMethod(Member member, XC_MethodHook callback) {
        if (!(member instanceof Executable)) {
            throw new IllegalArgumentException("Only methods/constructors can be hooked");
        }
        return hookExecutable((Executable) member, callback);
    }

    public static Set<Object> hookAllConstructors(Class<?> type, XC_MethodHook callback) {
        Set<Object> handles = new LinkedHashSet<>();
        for (Constructor<?> constructor : type.getDeclaredConstructors()) {
            constructor.setAccessible(true);
            handles.add(hookExecutable(constructor, callback));
        }
        return handles;
    }

    static Object hookExecutable(Executable executable, XC_MethodHook callback) {
        executable.setAccessible(true);
        return Api101Bridge.hook(executable).intercept(chain -> {
            XC_MethodHook.MethodHookParam param = new XC_MethodHook.MethodHookParam();
            param.thisObject = chain.getThisObject();
            param.args = chain.getArgs().toArray(new Object[0]);

            boolean beforeOk = true;
            try {
                callback.beforeHookedMethod(param);
            } catch (Throwable hookError) {
                beforeOk = false;
                log(hookError);
            }

            if (!beforeOk) {
                return chain.proceed();
            }

            if (!param.isReturnEarly()) {
                try {
                    Object result = chain.proceed(param.args);
                    param.setResultFromOriginal(result);
                } catch (Throwable originError) {
                    param.setThrowableFromOriginal(originError);
                }
            }

            try {
                callback.afterHookedMethod(param);
            } catch (Throwable hookError) {
                log(hookError);
            }

            if (param.hasThrowable()) throw param.getThrowable();
            return param.getResult();
        });
    }
}
