package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class WorkstationCaptureBurstTest {
    private static Object newBurst() throws Exception {
        try {
            Class<?> type = Class.forName("com.hellovoid.liquiddock.WorkstationCaptureBurst");
            java.lang.reflect.Constructor<?> ctor = type.getDeclaredConstructor();
            ctor.setAccessible(true);
            return ctor.newInstance();
        } catch (ClassNotFoundException e) {
            fail("WorkstationCaptureBurst must exist");
            return null;
        }
    }

    private static void start(Object burst) throws Exception {
        Method m = burst.getClass().getDeclaredMethod("start");
        m.setAccessible(true);
        m.invoke(burst);
    }

    private static void stop(Object burst) throws Exception {
        Method m = burst.getClass().getDeclaredMethod("stop");
        m.setAccessible(true);
        m.invoke(burst);
    }

    private static boolean active(Object burst) throws Exception {
        Method m = burst.getClass().getDeclaredMethod("isActive");
        m.setAccessible(true);
        return (Boolean) m.invoke(burst);
    }

    private static boolean onFrame(Object burst, long signature) throws Exception {
        Method m = burst.getClass().getDeclaredMethod("onFrame", long.class);
        m.setAccessible(true);
        return (Boolean) m.invoke(burst, signature);
    }

    @Test public void startRequiresThreeStaticSamplesBeforeStopping() throws Exception {
        Object burst = newBurst();
        start(burst);
        assertTrue(active(burst));
        assertTrue(onFrame(burst, 0x1111L));
        assertTrue(onFrame(burst, 0x1111L));
        assertFalse(onFrame(burst, 0x1111L));
        assertFalse(active(burst));
    }

    @Test public void visualChangeResetsStableConvergence() throws Exception {
        Object burst = newBurst();
        start(burst);
        assertTrue(onFrame(burst, 0x1111L));
        assertTrue(onFrame(burst, 0x1111L));
        assertTrue(onFrame(burst, 0x2222L));
        assertTrue(onFrame(burst, 0x2222L));
        assertFalse(onFrame(burst, 0x2222L));
    }

    @Test public void explicitStopPreventsContinuation() throws Exception {
        Object burst = newBurst();
        start(burst);
        stop(burst);
        assertFalse(active(burst));
        assertFalse(onFrame(burst, 0x3333L));
    }
}
