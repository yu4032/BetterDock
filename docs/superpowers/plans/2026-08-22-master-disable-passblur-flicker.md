# Master-disable and PassBlur flicker repair

Scope is intentionally limited to the device-observed Dock flicker and the master-switch lifecycle.

1. Gate ModuleMain before any runtime hook installation when the master switch is disabled.
2. Keep MainHook direct-call safe by loading/gating config before the workstation hook.
3. Make the Compose master switch restart Launcher after its preference write; abort restart if Remote Preferences cannot be synchronized.
4. Remove perpetual pre-draw compositor suppression; keep suppression on explicit material bind/geometry lifecycle callbacks only.
5. Preserve zero-copy PassBlur/OES/Prismal and do not add any capture/Bitmap fallback.
6. Verify RED contracts, full unit tests, assembleDebug, artifact upload. Produce a test APK before any merge decision.
