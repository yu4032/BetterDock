# HOME/APP Ownership — Provider Recovery Addendum

**Date:** 2026-08-16  
**Branch:** `fix/home-app-ownership-convergence`  
**Code HEAD before this note:** `256b36dc54279f3493a22c2965c4764059037e55`  
**Status:** Pre-wrap transport fix; requires exact-HEAD build/device revalidation.

## Problem found during pre-wrap review

The first production implementation correctly failed closed when the SystemUI provider Binder died, but provider rediscovery was request-driven through `GET_PROVIDER`.

If SystemUI alone restarted while the module broker service remained alive, the sequence could be:

1. Launcher notices provider death and publishes ownership `UNKNOWN`.
2. Launcher performs one immediate provider refresh before the restarted SystemUI has registered its new provider Binder.
3. The broker returns `null`.
4. No further Launcher focus/configuration event occurs because the user remains in the same stable scene.
5. The new SystemUI later registers a provider, but the broker had no way to push that event to Launcher.

The ordinary baseline could therefore remain fail-closed `UNKNOWN` longer than necessary even though SystemUI had recovered. This violated the production design requirement that provider recovery trigger a fresh baseline request.

## Fix

Provider-token recovery is now event-driven.

`FreeformLeashProtocol` adds a broker-local watcher transaction and callback descriptor. These codes live on the broker/callback Binders and do not reuse the provider HOME/freeform transaction namespace.

`FreeformLeashBrokerService` now:

- accepts a watcher only from the authorized Launcher UID/package;
- stores only the watcher Binder/death recipient, never HOME/task/surface state;
- immediately publishes the current provider token when a watcher registers;
- pushes provider token replacement;
- pushes `null` when the registered SystemUI provider dies;
- clears the watcher if the Launcher callback Binder dies.

`FreeformLeashBrokerClient` now:

- registers the watcher whenever the Launcher broker connection is established;
- consumes provider-change callbacks without polling;
- continues to use the existing provider Binder death link locally;
- preserves the old one-shot `GET_PROVIDER` path only as a mixed-generation fallback if an older broker rejects the watcher transaction.

No delayed provider-discovery polling loop was added.

## Ownership effect

`HomeOwnershipResolver` already listens to process-local provider changes. Therefore the new transport callback produces the intended behavior without changing the classifier:

```text
SystemUI provider dies
  -> provider listener(null)
  -> HOME/APP baseline UNKNOWN
  -> wallpaper fail-closed

SystemUI restarts and registers a new provider Binder
  -> broker watcher pushes new Binder
  -> provider listener(newBinder)
  -> request(lastDisplayId, "provider-ready")
  -> HOME / APP / bounded conflict confirmation
```

No Launcher task query, focus inference, last-good HOME/APP capture fallback, or additional task-state machine is introduced.

## Regression boundary

The fix changes only transport token recovery. It does not modify:

- `HomeOwnershipPolicy` classification;
- `SystemUiHomeOwnershipSource` task reads;
- `CaptureSceneState` precedence;
- `CaptureSourcePolicy`;
- `MainHook` ownership cleanup;
- `DockLiquidGlassView`;
- freeform SurfaceControl snapshot enumeration;
- the final freeform capture gate/breaker.

## Test evidence

`BrokerProviderRecoveryContractTest` was added before the implementation and requires:

- a broker provider-watcher protocol;
- broker push on provider registration/death;
- Launcher watcher registration/consumption;
- absence of a delayed `refreshLauncherProviderAsync` polling loop.

The pre-wrap pure-Java ownership/scene smoke was rerun after this change and printed:

```text
HOME_CONVERGENCE_PREWRAP_SMOKE_PASS
```

Android/Gradle tests are not claimed here because their terminal output is not available in the assistant environment.

## Required device gate

For the exact final implementation build, keep Launcher stationary on a stable HOME or APP scene and restart **SystemUI only**, leaving the broker/Launcher alive.

Acceptance evidence should show, without requiring an additional user focus/configuration transition:

1. baseline becomes `UNKNOWN` when the old provider dies;
2. SystemUI module/source/executor/repository hooks reinstall;
3. a `provider-ready-pending` ownership query appears automatically after the new provider registers;
4. the baseline returns to the same stable `HOME` or `APP` (or uses the normal single conflict confirmation for APP);
5. no timeout loop, repeated provider polling, SystemUI crash, or freeform breaker failure occurs.
