package com.hellovoid.liquiddock;

import java.util.LinkedHashSet;

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

    static int[] deduplicateTaskIds(int[] taskIds) {
        if (taskIds == null || taskIds.length == 0) return new int[0];
        LinkedHashSet<Integer> ids = new LinkedHashSet<>();
        for (int taskId : taskIds) ids.add(taskId);
        int[] result = new int[ids.size()];
        int i = 0;
        for (Integer taskId : ids) result[i++] = taskId;
        return result;
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
