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
 * Three gates in Samsung Camera block SmartScan on custom ROMs:
 *
 *  Gate 1 — isSmartScanSupported() in IntelligentManager
 *    Calls y2.d.e(y2.b.j2) → y2.c.e(SUPPORT_SMART_SCAN enum)
 *    → EnumMap populated by SemFloatingFeature.getBoolean("SUPPORT_SMART_SCAN")
 *    Returns false when SemFloatingFeature is absent/empty on custom ROMs.
 *    Fix: hook isSmartScanSupported() → true, and getBoolean() at the source.
 *
 *  Gate 2 — y2.c.Y()
 *    Reads SemFloatingFeature.getString("SEC_FLOATING_FEATURE_CAMERA_CONFIG_VENDOR_LIB_INFO")
 *    and checks .contains("smart_scan.samsung").
 *    If false, SrcbSmartScanNode is never selected — the yellow detection
 *    rectangle never fires even if libSmartScan.camera.samsung.so is present.
 *    Fix: hook getString() to inject "smart_scan.samsung" into that key's value.
 *
 *  Gate 3 — dlopen("libSmartScan.camera.samsung.so") inside libnode-jni.so
 *    SrcbSmartScanNode needs MLCreateSCEngine / MLSCTracking /
 *    MLSCGetSkipCount / MLDestroySCEngine from this vendor library.
 *    Requires the .so to physically exist at /system/lib64/ on the device.
 *    (No hook can substitute for a missing .so.)
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
        hookSemFloatingFeatureBoolean(lpparam.classLoader);
        hookSemFloatingFeatureString(lpparam.classLoader);
    }

    /** Gate 1 (primary): force isSmartScanSupported() -> true */
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
     * Gate 1 (defensive): SemFloatingFeature.getBoolean() for SUPPORT_SMART_SCAN* keys.
     * y2.c populates its EnumMap at construction — intercepting at the source keeps
     * that map correct for any code path that reads it directly.
     */
    private void hookSemFloatingFeatureBoolean(ClassLoader classLoader) {
        try {
            Class<?> semFF = XposedHelpers.findClass(
                    "com.samsung.android.feature.SemFloatingFeature",
                    classLoader);
            SemFloatingFeatureHook hook = new SemFloatingFeatureHook();
            XposedHelpers.findAndHookMethod(semFF, "getBoolean", String.class, hook);
            try {
                XposedHelpers.findAndHookMethod(semFF, "getBoolean",
                        String.class, boolean.class, hook);
            } catch (NoSuchMethodError ignored) { }
            XposedBridge.log(TAG + ": SemFloatingFeature.getBoolean hooks applied");
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": SemFloatingFeature.getBoolean hook skipped: " + e.getMessage());
            Log.w(TAG, "getBoolean hook skipped: " + e.getMessage());
        }
    }

    /**
     * Gate 2: SemFloatingFeature.getString() for vendor-lib-info and document-scan keys.
     *
     * SEC_FLOATING_FEATURE_CAMERA_CONFIG_VENDOR_LIB_INFO must contain "smart_scan.samsung"
     * or y2.c.Y() returns false and SrcbSmartScanNode is never instantiated.
     *
     * SEC_FLOATING_FEATURE_CAMERA_DOCUMENTSCAN_SOLUTIONS must be non-null/non-empty
     * or the entire document-scan pipeline is skipped.
     */
    private void hookSemFloatingFeatureString(ClassLoader classLoader) {
        try {
            Class<?> semFF = XposedHelpers.findClass(
                    "com.samsung.android.feature.SemFloatingFeature",
                    classLoader);
            SemFloatingFeatureStringHook hook = new SemFloatingFeatureStringHook();
            XposedHelpers.findAndHookMethod(semFF, "getString", String.class, hook);
            try {
                XposedHelpers.findAndHookMethod(semFF, "getString",
                        String.class, String.class, hook);
            } catch (NoSuchMethodError ignored) { }
            XposedBridge.log(TAG + ": SemFloatingFeature.getString hooks applied");
        } catch (Throwable e) {
            XposedBridge.log(TAG + ": SemFloatingFeature.getString hook skipped: " + e.getMessage());
            Log.w(TAG, "getString hook skipped: " + e.getMessage());
        }
    }
}