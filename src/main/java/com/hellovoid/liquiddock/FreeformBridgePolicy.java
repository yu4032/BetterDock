package com.hellovoid.liquiddock;

/** Android-free safety helpers shared by the freeform leash bridge. */
final class FreeformBridgePolicy {
    private static final int FAILURE_LIMIT = 3;

    private FreeformBridgePolicy() {}

    static boolean packageListContains(String[] packages, String expected) {
        if (packages == null || expected == null) return false;
        for (String value : packages) {
            if (expected.equals(value)) return true;
        }
        return false;
    }

    static boolean shouldIncludeFreeformCandidate(
            Integer taskDisplayId, Boolean visible, int requestedDisplayId) {
        if (Boolean.FALSE.equals(visible)) return false;
        return taskDisplayId == null || taskDisplayId == requestedDisplayId;
    }

    static final class CircuitBreaker {
        private int infrastructureFailures;
        private boolean disabled;

        synchronized boolean recordInfrastructureFailure() {
            if (disabled) return true;
            infrastructureFailures++;
            if (infrastructureFailures >= FAILURE_LIMIT) disabled = true;
            return disabled;
        }

        synchronized void disableForProcess() {
            infrastructureFailures = Math.max(infrastructureFailures, FAILURE_LIMIT);
            disabled = true;
        }

        synchronized boolean isDisabled() {
            return disabled;
        }

        synchronized int failureCount() {
            return infrastructureFailures;
        }

        synchronized void resetForTest() {
            infrastructureFailures = 0;
            disabled = false;
        }
    }
}
