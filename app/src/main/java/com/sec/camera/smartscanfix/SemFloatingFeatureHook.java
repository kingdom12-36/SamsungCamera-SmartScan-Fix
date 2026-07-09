package com.sec.camera.smartscanfix;

import android.util.Log;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;

/**
 * Hooks SemFloatingFeature.getBoolean(String) and returns true for the
 * SUPPORT_SMART_SCAN key while passing everything else through unchanged.
 *
 * Why a separate class: keeps MainHook clean and makes it easy to add more
 * feature-key overrides in future without cluttering the entry point.
 */
public class SemFloatingFeatureHook extends XC_MethodHook {

    private static final String TAG = "SmartScanFix";

    /**
     * Keys that must return true to enable SmartScan on custom ROMs.
     * "SUPPORT_SMART_SCAN_EXTRACT_TEXT" and "SUPPORT_SMART_SCAN_MANUAL_CROP"
     * are companion flags also read by Samsung Camera (y2/b enum entries k2, l2).
     */
    private static final String[] SMART_SCAN_KEYS = {
            "SUPPORT_SMART_SCAN",
            "SUPPORT_SMART_SCAN_EXTRACT_TEXT",
            "SUPPORT_SMART_SCAN_MANUAL_CROP",
    };

    @Override
    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
        String key = (String) param.args[0];
        if (key == null) return;

        for (String target : SMART_SCAN_KEYS) {
            if (target.equals(key)) {
                Log.d(TAG, "SemFloatingFeature.getBoolean(" + key + ") -> true (hooked)");
                param.setResult(true);
                return;
            }
        }
        // All other keys: leave the original result untouched
    }
}
