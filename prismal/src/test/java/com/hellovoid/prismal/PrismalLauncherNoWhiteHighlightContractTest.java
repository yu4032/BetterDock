package com.hellovoid.prismal;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Locks the verified compact-launcher defaults while allowing every component to be re-enabled. */
public class PrismalLauncherNoWhiteHighlightContractTest {
    @Test
    public void launcherDefaultsKeepUpstreamWhiteComponentsDisabled() {
        PrismalComponentControls.Profile profile =
                PrismalComponentControls.forMode(PrismalRenderer.Mode.LAUNCHER_COMPACT);
        assertFalse(profile.skyHaze);
        assertFalse(profile.specular);
        assertFalse(profile.litRim);
        assertFalse(profile.oppositeRim);
        assertFalse(profile.cornerRim);
        assertFalse(profile.faceSheen);
        assertFalse(profile.plainHighlight);
        assertFalse(profile.caustics);
        assertFalse(profile.pressGlow);
        assertTrue(profile.compactSafeHighlight);
    }

    @Test
    public void transformedLauncherFragmentGatesEveryFormerlySuppressedOutput() {
        String shader = PrismalLauncherCompactShader.apply(PrismalShaderSources.FRAGMENT);

        assertTrue(shader.contains("if (u_componentSkyHaze > 0.5)"));
        assertTrue(shader.contains("if (u_componentSpecular > 0.5)"));
        assertTrue(shader.contains("if (u_componentLitRim > 0.5)"));
        assertTrue(shader.contains("if (u_componentOppositeRim > 0.5)"));
        assertTrue(shader.contains("if (u_componentCornerRim > 0.5)"));
        assertTrue(shader.contains("if (u_componentFaceSheen > 0.5)"));
        assertTrue(shader.contains("if (u_componentPlainHighlight > 0.5)"));
        assertTrue(shader.contains("if (u_componentCaustics > 0.5)"));
        assertTrue(shader.contains("if (u_componentPressGlow > 0.5)"));

        // Component gates must not disable the actual background/refraction path.
        assertTrue(shader.contains("texture2D(u_backgroundTexture"));
        assertTrue(shader.contains("vec2 baseOffset = (dLens * lensDir) / u_resolution;"));
    }
}
