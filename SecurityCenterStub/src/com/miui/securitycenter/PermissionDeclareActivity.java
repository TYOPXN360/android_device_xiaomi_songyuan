/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.miui.securitycenter;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;

/**
 * Answers MIUICamera's privacy declaration request immediately.
 *
 * The camera starts miui.intent.action.SYSTEM_PERMISSION_DECLARE(_NEW) with
 * setPackage("com.miui.securitycenter") and accepts result code 1 or 0x904 as
 * "the user agreed". Returning 1 from here lets it finish its permission check
 * without any interaction, which is what keeps setupCamera from being skipped.
 */
public class PermissionDeclareActivity extends Activity {

    private static final String TAG = "SecurityCenterStub";

    /** RESULT_OK for the declaration request. */
    private static final int RESULT_CTA_ACCEPTED = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "declaring permissions for " + getCallingPackage()
                + " action=" + (getIntent() != null ? getIntent().getAction() : null));
        setResult(RESULT_CTA_ACCEPTED);
        finish();
    }
}
