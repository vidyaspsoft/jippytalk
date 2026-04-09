package com.jippytalk.Privacy.APIHandler;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.util.Log;

import com.jippytalk.Extras;
import com.jippytalk.Privacy.CommonCodes.PrivacyHttpsUtils;
import com.jippytalk.TokenRefreshAPI;

public class PrivacyAPIHandler {

    // ---- Fields ----

    private final PrivacyHttpsUtils    privacyHttpsUtils;
    private final TokenRefreshAPI      tokenRefreshAPI;

    // ---- Constructor ----

    public PrivacyAPIHandler(PrivacyHttpsUtils privacyHttpsUtils, TokenRefreshAPI tokenRefreshAPI) {
        this.privacyHttpsUtils  =   privacyHttpsUtils;
        this.tokenRefreshAPI    =   tokenRefreshAPI;
        Log.e(Extras.LOG_MESSAGE, "PrivacyAPIHandler initialized");
    }
}
