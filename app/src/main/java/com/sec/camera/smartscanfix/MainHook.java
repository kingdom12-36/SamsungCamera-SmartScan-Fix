package com.sec.camera.smartscanfix;

import android.util.Log;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * LSPosed module entry point.
 *
 * Root cause (decompiled from Samsung Camera smali):
 *   isSmartScanSupported() in IntelligentManager calls:
 *     y2.d.e(y2.b.j2)  →  y2.c.e(SUPPORT_SMART_SCAN enum)
 *     → EnumMap lookup populated by SemFloatingFeature.getBoolean("SUPPORT_SMART_SCAN")
 *     → returns false on custom ROMs (SemFloatingFeature not present / empty)
 *   ADDITIONAL_SCENE_DOCUMENT_SCAN setting defaults to 1 (enabled) in AbstractCameraSettings,
 *   so no user action is required once the support gate is bypassed.
 *
 * Fix: hook both the high-level gate (isSmartScanSupported) and the low-level
 *      SemFloatingFeature source so the EnumMap is also correct on first load.
 */
public class MainHook implements IXposedHookLoadPackage {

    private static final String TAG = "SmartScanFix";
    private static final String TARGET_PACKAGE = "com.sec.android.app.camera";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!TARGET_PACKAGE.equals(lpparam.packageName)) {
            return;
        }
        XposedBridge.log(TAG + ": Samsung Camera loaded — applying SmartScan hooks");
        Log.i(TAG, "Samsung Camera loaded — applying SmartScan hooks");

        hookIntelligentManager(lpparam.classLoader);
        hookSemFloatingFeature(lpparam.classLoader);
    }

    /**
     * Hook 1 (primary): IntelligentManager.isSmartScanSupported()
     *
     * This is the high-level gate checked before showing the scan UI and before
     * registering native SmartScan callbacks. Returning true unconditionally:
     *   - Makes the SmartScan border/button appear in the viewfinder
     *   - Enables the Document Scan toggle in Camera Settings
     *   - Allows isSmartScanAvailable() to proceed to check the user pref
     *     (which defaults to enabled, so no extra action needed)
     */
    private void hookIntelligentManager(ClassLoader classLoader) {
        try {
            Class<?> intelligentManager = XposedHelpers.findClass(
                    "com.sec.android.app.camera.shootingmode.photo.intelligentui.IntelligentManager",
                    classLoader);

            XposedHelpers.findAndHookMethod(
                    intelligentManager,
                    "isSmartScanSupported",
                    new XC_MethodReplacement() {
                        @Override
                        protected Object replaceHookedMethod(MethodHookParam param) {
                            Log.d(TAG, "isSmartScanSupported() -> true (hooked)");
                            return true;
                        }
                    });

            XposedBridge.log(TAG + ": isSmartScanSupported hook applied");
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": Failed to hook isSmartScanSupported: " + e.getMessage());
            Log.e(TAG, "Failed to hook isSmartScanSupported", e);
        }
    }

    /**
     * Hook 2 (defensive): SemFloatingFeature.getBoolean(String)
     *
     * The y2.c class populates an EnumMap at construction time using
     * SemFloatingFeature.getBoolean("SUPPORT_SMART_SCAN"). On custom ROMs the
     * SemFloatingFeature service is absent or returns false for all Samsung keys.
     *
     * We intercept getBoolean() at the source and return true for the specific
     * SmartScan key. This ensures the EnumMap is also correct if the app reads it
     * through any path other than isSmartScanSupported().
     *
     * All other feature keys pass through unmodified.
     */
    private void hookSemFloatingFeature(ClassLoader classLoader) {
        try {
            Class<?> semFloatingFeature = XposedHelpers.findClass(
                    "com.samsung.android.feature.SemFloatingFeature",
                    classLoader);

            SemFloatingFeatureHook hook = new SemFloatingFeatureHook();

            // Hook the single-arg overload: getBoolean(String)
            XposedHelpers.findAndHookMethod(
                    semFloatingFeature,
                    "getBoolean",
                    String.class,
                    hook);

            // Hook the two-arg overload: getBoolean(String, boolean) — for forward compatibility
            // Some camera paths use this variant with a default value fallback.
            try {
                XposedHelpers.findAndHookMethod(
                        semFloatingFeature,
                        "getBoolean",
                        String.class,
                        boolean.class,
                        hook);
            } catch (NoSuchMethodError ignored) {
                // Overload may not exist in all SemFloatingFeature versions — safe to skip
            }

            XposedBridge.log(TAG + ": SemFloatingFeature.getBoolean hooks applied");
        } catch (Throwable e) {
            // SemFloatingFeature may not be visible to the app's classLoader on all
            // ROMs — Hook 1 is sufficient in that case; log and continue.
            XposedBridge.log(TAG + ": SemFloatingFeature hook skipped (not accessible): " + e.getMessage());
            Log.w(TAG, "SemFloatingFeature hook skipped: " + e.getMessage());
        }
    }
}
