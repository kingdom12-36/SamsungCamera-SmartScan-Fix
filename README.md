# SamsungCamera-SmartScan-Fix

An LSPosed module that enables the **SmartScan / Document Scan** feature in Samsung Camera on custom ROMs (e.g. running S24FE firmware on Galaxy Note 10+).

## Why it breaks

Samsung Camera gates the SmartScan feature behind `SemFloatingFeature.getBoolean("SUPPORT_SMART_SCAN")` — a Samsung-specific system service that returns `false` on any ROM that doesn't carry Samsung's floating feature configuration files. This means the document scan UI is completely hidden even though:

- The native processing libraries (`libsrcb*.so`) are bundled inside the APK itself
- The UI code (SmartScanRect, SmartScanCaptureButton, DocumentScanActivity) is fully present
- The setting (`ADDITIONAL_SCENE_DOCUMENT_SCAN`) defaults to **enabled**

## What the module does

Two hooks, applied only to `com.sec.android.app.camera`:

| Hook | Target | Action |
|------|--------|--------|
| 1 (primary) | `IntelligentManager.isSmartScanSupported()` | Always returns `true` |
| 2 (defensive) | `SemFloatingFeature.getBoolean(String)` | Returns `true` for `SUPPORT_SMART_SCAN*` keys |

Hook 1 is sufficient for most devices. Hook 2 ensures the internal feature-flag EnumMap (populated at construction time in the obfuscated `y2.c` class) is also correct, covering edge cases where the app reads the flag before Hook 1 fires.

## How to use

1. Install [LSPosed](https://github.com/LSPosed/LSPosed)
2. Install this module APK
3. Enable the module in LSPosed → scope it to **Samsung Camera** (`com.sec.android.app.camera`)
4. Reboot
5. Open Samsung Camera → go to **Settings → Scene Optimizer** → enable **Document scan**
6. The SmartScan border will now appear when you point the back camera at a document

## Tested on

| Device | ROM | Camera version |
|--------|-----|----------------|
| Galaxy Note 10+ (SM-N975F) | S24FE-based custom ROM | See releases |

## Building from source

```bash
./gradlew assembleRelease
```

The APK will be at `app/build/outputs/apk/release/app-release-unsigned.apk`. Sign with your key or use debug build for testing.

## Technical notes

- Decompiled from `com.sec.android.app.camera` using apktool 2.9.3
- Feature gate chain: `isSmartScanSupported()` → `y2.d.e(SUPPORT_SMART_SCAN)` → `y2.c.e()` → EnumMap → `SemFloatingFeature.getBoolean("SUPPORT_SMART_SCAN")`
- Native SmartScan runs on SRIB (Samsung Research India/Bangalore) SRCB engine, bundled in the APK
- No hooks needed for `SceneDocumentScanActivity` — it reads `ADDITIONAL_SCENE_DOCUMENT_SCAN` directly from CameraSettings which defaults to `1`

## License

GPL-3.0 — see [LICENSE](./LICENSE)
