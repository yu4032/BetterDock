package com.hellovoid.liquiddock;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.Test;

/** Regression contract for workstation/fullscreen transitions that replace the Dock ViewRoot. */
public class Miuix307PassBlurRootRebindContractTest {
    private static String source() throws Exception {
        return Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquiddock/Miuix307PassBlurTextureView.java"),
                StandardCharsets.UTF_8);
    }

    @Test
    public void changedViewRootRebindsPassBlurInsteadOfKeepingStaleFrame() throws Exception {
        String src = source();
        int refresh = src.indexOf("private void refreshProducerGeometryInPlace()");
        int next = src.indexOf("private int horizontalOverscanPx()", refresh);
        assertTrue("missing producer geometry refresh path", refresh >= 0 && next > refresh);
        String refreshBody = src.substring(refresh, next);

        assertFalse("root replacement must not silently retain the stale PassBlur binding",
                refreshBody.contains(
                        "if (!isSameSurface(binding.rootSurface, geometry.rootSurface)) return;"));
        assertTrue("root replacement must enter an explicit producer rebind path",
                refreshBody.contains("rebindProducerForRootChange(geometry)"));

        int rebind = src.indexOf("private void rebindProducerForRootChange(");
        int observer = src.indexOf("private void installGeometryObserver()", rebind);
        assertTrue("missing bounded root-rebind implementation", rebind >= 0 && observer > rebind);
        String rebindBody = src.substring(rebind, observer);

        assertTrue("old binding must lose ownership before a new bind can start",
                rebindBody.contains("binding = null"));
        assertTrue("old compositor binding must be explicitly released",
                rebindBody.contains("Miuix307PassBlurBridge.unbind(staleBinding)"));
        assertTrue("last consumed OES frame must not remain authoritative after root replacement",
                rebindBody.contains("hasConsumedFrame = false"));
        assertTrue("pending old-root frame signal must be cleared",
                rebindBody.contains("frameAvailable.set(false)"));
        assertTrue("the surviving TextureView must bind its producer to the replacement ViewRoot",
                rebindBody.contains("bindProducerWhenReady(0)"));
    }
}
