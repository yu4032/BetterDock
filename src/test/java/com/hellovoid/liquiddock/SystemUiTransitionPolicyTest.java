package com.hellovoid.liquiddock;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public class SystemUiTransitionPolicyTest {
    private static SystemUiTransitionPolicy.Change homeFront(boolean showWallpaper) {
        return new SystemUiTransitionPolicy.Change(0, true, false, false,
                true, false, showWallpaper);
    }

    private static SystemUiTransitionPolicy.Change homeBack(boolean showWallpaper) {
        return new SystemUiTransitionPolicy.Change(0, true, false, false,
                false, true, showWallpaper);
    }

    private static SystemUiTransitionPolicy.Change appFront() {
        return new SystemUiTransitionPolicy.Change(0, false, true, false,
                true, false, false);
    }

    private static SystemUiTransitionPolicy.Change appBack() {
        return new SystemUiTransitionPolicy.Change(0, false, true, false,
                false, true, false);
    }

    private static SystemUiTransitionPolicy.Change wallpaperFront() {
        return new SystemUiTransitionPolicy.Change(0, false, false, true,
                true, false, false);
    }

    @Test public void appToLauncherRequiresHomeFrontAppBackAndWallpaper() {
        assertEquals(SystemUiTransitionPolicy.Kind.APP_TO_LAUNCHER,
                SystemUiTransitionPolicy.classify(Arrays.asList(
                        homeFront(true), appBack(), wallpaperFront())));
    }

    @Test public void launcherToAppRequiresAppFrontAndHomeBack() {
        assertEquals(SystemUiTransitionPolicy.Kind.LAUNCHER_TO_APP,
                SystemUiTransitionPolicy.classify(Arrays.asList(
                        appFront(), homeBack(true))));
    }

    @Test public void homeVisibilityAloneIsNotAppToLauncher() {
        assertEquals(SystemUiTransitionPolicy.Kind.NONE,
                SystemUiTransitionPolicy.classify(Collections.singletonList(homeFront(true))));
    }

    @Test public void appFrontAloneIsNotLauncherToApp() {
        assertEquals(SystemUiTransitionPolicy.Kind.NONE,
                SystemUiTransitionPolicy.classify(Collections.singletonList(appFront())));
    }

    @Test public void evidenceMustBelongToSameDisplay() {
        SystemUiTransitionPolicy.Change appBackDisplayOne = new SystemUiTransitionPolicy.Change(
                1, false, true, false, false, true, false);
        assertEquals(SystemUiTransitionPolicy.Kind.NONE,
                SystemUiTransitionPolicy.classify(Arrays.asList(
                        homeFront(true), appBackDisplayOne, wallpaperFront())));
    }
}
