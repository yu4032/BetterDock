package com.hellovoid.liquiddock;

import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WorkstationGridMarginPolicyTest {
    @Test
    public void positiveValuesAddSymmetricMarginsInsteadOfTranslatingTheGrid() throws Exception {
        Class<?> policy = Class.forName("com.hellovoid.liquiddock.WorkstationGridMarginPolicy");
        Method apply = policy.getDeclaredMethod("apply",
                int.class, int.class, int.class, int.class, int.class, int.class);
        apply.setAccessible(true);

        assertArrayEquals(new int[]{22, 32, 13, 8},
                (int[]) apply.invoke(null, 10, 20, 5, 0, 12, 8));
    }

    @Test
    public void negativeMarginAdjustmentsClampAtZeroWithoutMovingTheOppositeEdge() throws Exception {
        Class<?> policy = Class.forName("com.hellovoid.liquiddock.WorkstationGridMarginPolicy");
        Method apply = policy.getDeclaredMethod("apply",
                int.class, int.class, int.class, int.class, int.class, int.class);
        apply.setAccessible(true);

        assertArrayEquals(new int[]{0, 5, 0, 0},
                (int[]) apply.invoke(null, 10, 20, 5, 7, -15, -10));
    }

    @Test
    public void allAppsDetectionDoesNotDependOnOnePrivateLauncherMethod() throws Exception {
        Class<?> classifier = Class.forName("com.hellovoid.liquiddock.WorkstationLayoutClassifier");
        Method matches = classifier.getDeclaredMethod(
                "matches", boolean.class, String.class, String.class);
        matches.setAccessible(true);

        assertTrue((Boolean) matches.invoke(null, true, "", ""));
        assertTrue((Boolean) matches.invoke(null, false,
                "GRID_TYPE_IN_ALL_APPS_WORKSPACE", ""));
        assertTrue((Boolean) matches.invoke(null, false, "",
                "com.miui.home.launcher.laptop.AllAppsContainer>android.widget.FrameLayout"));
        assertFalse((Boolean) matches.invoke(null, false,
                "GRID_TYPE_WORKSPACE", "com.miui.home.launcher.Workspace"));
    }
}
