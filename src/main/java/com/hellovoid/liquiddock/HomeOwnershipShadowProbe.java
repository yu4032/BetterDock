package com.hellovoid.liquiddock;

import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Launcher-only diagnostic probe. Results are logged and never feed production scene state. */
final class HomeOwnershipShadowProbe {
    private static final Object LOCK = new Object();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicLong REQUEST_IDS = new AtomicLong();
    private static final LinkedHashMap<Long, PendingSample> PENDING = new LinkedHashMap<>();
    private static final long UNAVAILABLE_LOG_INTERVAL_NS = 1_000_000_000L;

    private static volatile boolean overviewActive;
    private static volatile boolean allAppsActive;
    private static volatile long lastUnavailableLogNanos;

    private HomeOwnershipShadowProbe() {}

    static void setOverviewActive(boolean active) {
        overviewActive = active;
    }

    static void setAllAppsActive(boolean active) {
        allAppsActive = active;
    }

    static void sample(String reason, int displayId, Boolean focus,
                       int topWindowingMode, boolean launcherHome,
                       boolean workstation) {
        PendingSample sample = PendingSample.initial(
                reason, displayId, focus, topWindowingMode, launcherHome,
                overviewActive, allAppsActive, workstation);
        submit(sample);
    }

    private static void submit(PendingSample template) {
        IBinder provider = FreeformLeashRuntime.providerBinderForDiagnostics();
        if (provider == null || template.displayId < 0) {
            logUnavailable(template, template.phase == Phase.RECHECK
                    ? "UNAVAILABLE_RECHECK" : "UNAVAILABLE", "provider");
            return;
        }

        long requestId = REQUEST_IDS.incrementAndGet();
        long now = System.nanoTime();
        PendingSample pending = template.withRequest(requestId, now);
        registerPending(pending, now);

        Parcel data = Parcel.obtain();
        try {
            data.writeInterfaceToken(FreeformLeashProtocol.PROVIDER_DESCRIPTOR);
            data.writeLong(requestId);
            data.writeInt(pending.displayId);
            data.writeStrongBinder(CALLBACK);
            boolean accepted = provider.transact(
                    HomeOwnershipShadowProtocol.TRANSACTION_REQUEST_HOME_OWNERSHIP_SHADOW,
                    data, null, IBinder.FLAG_ONEWAY);
            if (!accepted) {
                removePending(requestId);
                logUnavailable(pending, pending.phase == Phase.RECHECK
                        ? "UNAVAILABLE_RECHECK" : "UNAVAILABLE", "transaction-rejected");
                return;
            }
        } catch (RemoteException providerGone) {
            removePending(requestId);
            logUnavailable(pending, pending.phase == Phase.RECHECK
                    ? "UNAVAILABLE_RECHECK" : "UNAVAILABLE", "provider-dead");
            return;
        } catch (Throwable error) {
            removePending(requestId);
            logUnavailable(pending, pending.phase == Phase.RECHECK
                    ? "UNAVAILABLE_RECHECK" : "UNAVAILABLE", "request-error");
            return;
        } finally {
            data.recycle();
        }

        MAIN.postDelayed(() -> expirePending(requestId),
                HomeOwnershipShadowProtocol.PENDING_TTL_MS);
    }

    private static final Binder CALLBACK = new Binder() {
        @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code != HomeOwnershipShadowProtocol.TRANSACTION_HOME_OWNERSHIP_SHADOW_RESULT) {
                return false;
            }
            try {
                data.enforceInterface(HomeOwnershipShadowProtocol.CALLBACK_DESCRIPTOR);
                long requestId = data.readLong();
                int status = data.readInt();
                boolean homeVisible = data.readInt() != 0;
                int homeTaskId = data.readInt();
                int topFullscreenTaskId = data.readInt();
                int topFullscreenWindowingMode = data.readInt();
                long sampleElapsedNanos = data.readLong();

                if (status != HomeOwnershipShadowProtocol.STATUS_OK
                        && status != HomeOwnershipShadowProtocol.STATUS_UNAVAILABLE
                        && status != HomeOwnershipShadowProtocol.STATUS_STRUCTURE_FAILURE) {
                    PendingSample malformed = removePending(requestId);
                    if (malformed != null) {
                        logUnavailable(malformed, malformed.phase == Phase.RECHECK
                                ? "UNAVAILABLE_RECHECK" : "UNAVAILABLE", "malformed-status");
                    }
                    return true;
                }

                PendingSample pending = removePending(requestId);
                if (pending == null) return true;
                if (status != HomeOwnershipShadowProtocol.STATUS_OK) {
                    logUnavailable(pending, pending.phase == Phase.RECHECK
                            ? "UNAVAILABLE_RECHECK" : "UNAVAILABLE",
                            status == HomeOwnershipShadowProtocol.STATUS_STRUCTURE_FAILURE
                                    ? "structure" : "remote-unavailable");
                    return true;
                }

                handleResult(pending, homeVisible, homeTaskId,
                        topFullscreenTaskId, topFullscreenWindowingMode,
                        sampleElapsedNanos);
                return true;
            } catch (Throwable error) {
                Api101Bridge.log("[DC-SHADOW] HOME ownership callback malformed", error);
                return true;
            }
        }
    };

    private static void handleResult(PendingSample sample, boolean rawHomeVisible,
                                     int homeTaskId, int topFullscreenTaskId,
                                     int topFullscreenWindowingMode,
                                     long sampleElapsedNanos) {
        boolean eligible = HomeOwnershipShadowPolicy.baselineEligible(
                sample.overview, sample.allApps, sample.workstation);
        boolean rawMatch = HomeOwnershipShadowPolicy.matches(
                sample.launcherHome, rawHomeVisible);
        HomeOwnershipShadowPolicy.SystemUiBaseline systemUiBaseline =
                HomeOwnershipShadowPolicy.systemUiBaseline(
                        rawHomeVisible, homeTaskId, topFullscreenTaskId);
        boolean combinedMatch = HomeOwnershipShadowPolicy.matchesLauncher(
                sample.launcherHome, systemUiBaseline);
        long latencyMs = Math.max(0L,
                (System.nanoTime() - sample.requestElapsedNanos) / 1_000_000L);

        if (sample.phase == Phase.IMMEDIATE) {
            if (systemUiBaseline == HomeOwnershipShadowPolicy.SystemUiBaseline.UNKNOWN) {
                logResult("UNKNOWN", sample, rawHomeVisible, rawMatch,
                        systemUiBaseline, combinedMatch, eligible, latencyMs,
                        homeTaskId, topFullscreenTaskId, topFullscreenWindowingMode,
                        sampleElapsedNanos);
                return;
            }
            if (combinedMatch) {
                logResult("MATCH", sample, rawHomeVisible, rawMatch,
                        systemUiBaseline, true, eligible, latencyMs,
                        homeTaskId, topFullscreenTaskId, topFullscreenWindowingMode,
                        sampleElapsedNanos);
                return;
            }

            logResult("MISMATCH", sample, rawHomeVisible, rawMatch,
                    systemUiBaseline, false, eligible, latencyMs,
                    homeTaskId, topFullscreenTaskId, topFullscreenWindowingMode,
                    sampleElapsedNanos);
            PendingSample recheck = sample.forRecheck();
            MAIN.postDelayed(() -> submit(recheck),
                    HomeOwnershipShadowProtocol.RECHECK_DELAY_MS);
            return;
        }

        String result;
        if (systemUiBaseline == HomeOwnershipShadowPolicy.SystemUiBaseline.UNKNOWN) {
            result = "UNKNOWN_RECHECK";
        } else {
            result = combinedMatch ? "TRANSIENT_MISMATCH" : "PERSISTENT_MISMATCH";
        }
        logResult(result, sample, rawHomeVisible, rawMatch,
                systemUiBaseline, combinedMatch, eligible, latencyMs,
                homeTaskId, topFullscreenTaskId, topFullscreenWindowingMode,
                sampleElapsedNanos);
    }

    private static void registerPending(PendingSample sample, long nowNanos) {
        synchronized (LOCK) {
            pruneExpiredLocked(nowNanos);
            while (PENDING.size() >= HomeOwnershipShadowProtocol.MAX_PENDING) {
                Iterator<Map.Entry<Long, PendingSample>> iterator = PENDING.entrySet().iterator();
                if (!iterator.hasNext()) break;
                iterator.next();
                iterator.remove();
            }
            PENDING.put(sample.requestId, sample);
        }
    }

    private static void pruneExpiredLocked(long nowNanos) {
        long ttlNanos = HomeOwnershipShadowProtocol.PENDING_TTL_MS * 1_000_000L;
        Iterator<Map.Entry<Long, PendingSample>> iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            PendingSample sample = iterator.next().getValue();
            if (nowNanos - sample.requestElapsedNanos >= ttlNanos) iterator.remove();
        }
    }

    private static PendingSample removePending(long requestId) {
        synchronized (LOCK) {
            return PENDING.remove(requestId);
        }
    }

    private static void expirePending(long requestId) {
        PendingSample expired = removePending(requestId);
        if (expired == null) return;
        logUnavailable(expired, expired.phase == Phase.RECHECK
                ? "UNAVAILABLE_RECHECK" : "UNAVAILABLE", "timeout");
    }

    private static void logResult(String result, PendingSample sample,
                                  boolean rawHomeVisible, boolean rawMatch,
                                  HomeOwnershipShadowPolicy.SystemUiBaseline systemUiBaseline,
                                  boolean combinedMatch, boolean eligible, long latencyMs,
                                  int homeTaskId, int topFullscreenTaskId,
                                  int topFullscreenWindowingMode, long sampleElapsedNanos) {
        Api101Bridge.log("[DC-SHADOW] home-ownership result=" + result
                + " phase=" + (sample.phase == Phase.IMMEDIATE ? "immediate" : "recheck")
                + " reason=" + sample.reason
                + " launcherHome=" + sample.launcherHome
                + " rawHomeVisible=" + rawHomeVisible
                + " rawMatch=" + rawMatch
                + " systemUiBaseline=" + systemUiBaseline
                + " combinedMatch=" + combinedMatch
                + " focus=" + sample.focus
                + " topMode=" + sample.topWindowingMode
                + " overview=" + sample.overview
                + " allApps=" + sample.allApps
                + " workstation=" + sample.workstation
                + " eligible=" + eligible
                + " homeTaskId=" + homeTaskId
                + " topFullscreenTaskId=" + topFullscreenTaskId
                + " topFullscreenMode=" + topFullscreenWindowingMode
                + " latencyMs=" + latencyMs
                + " sampleNs=" + sampleElapsedNanos);
    }

    private static void logUnavailable(PendingSample sample, String result, String detail) {
        if ("UNAVAILABLE_RECHECK".equals(result)) {
            Api101Bridge.log("[DC-SHADOW] home-ownership result=" + result
                    + " phase=recheck reason=" + sample.reason
                    + " detail=" + detail
                    + " launcherHome=" + sample.launcherHome);
            return;
        }
        long now = System.nanoTime();
        long last = lastUnavailableLogNanos;
        if (now - last < UNAVAILABLE_LOG_INTERVAL_NS) return;
        lastUnavailableLogNanos = now;
        Api101Bridge.log("[DC-SHADOW] home-ownership result=UNAVAILABLE"
                + " phase=immediate reason=" + sample.reason
                + " detail=" + detail
                + " launcherHome=" + sample.launcherHome);
    }

    private enum Phase { IMMEDIATE, RECHECK }

    private static final class PendingSample {
        final long requestId;
        final String reason;
        final int displayId;
        final Boolean focus;
        final int topWindowingMode;
        final boolean launcherHome;
        final boolean overview;
        final boolean allApps;
        final boolean workstation;
        final long requestElapsedNanos;
        final Phase phase;

        private PendingSample(long requestId, String reason, int displayId, Boolean focus,
                              int topWindowingMode, boolean launcherHome,
                              boolean overview, boolean allApps, boolean workstation,
                              long requestElapsedNanos, Phase phase) {
            this.requestId = requestId;
            this.reason = reason;
            this.displayId = displayId;
            this.focus = focus;
            this.topWindowingMode = topWindowingMode;
            this.launcherHome = launcherHome;
            this.overview = overview;
            this.allApps = allApps;
            this.workstation = workstation;
            this.requestElapsedNanos = requestElapsedNanos;
            this.phase = phase;
        }

        static PendingSample initial(String reason, int displayId, Boolean focus,
                                     int topWindowingMode, boolean launcherHome,
                                     boolean overview, boolean allApps, boolean workstation) {
            return new PendingSample(0L, reason, displayId, focus, topWindowingMode,
                    launcherHome, overview, allApps, workstation, 0L, Phase.IMMEDIATE);
        }

        PendingSample forRecheck() {
            return new PendingSample(0L, reason, displayId, focus, topWindowingMode,
                    launcherHome, overview, allApps, workstation, 0L, Phase.RECHECK);
        }

        PendingSample withRequest(long requestId, long requestElapsedNanos) {
            return new PendingSample(requestId, reason, displayId, focus, topWindowingMode,
                    launcherHome, overview, allApps, workstation,
                    requestElapsedNanos, phase);
        }
    }
}
