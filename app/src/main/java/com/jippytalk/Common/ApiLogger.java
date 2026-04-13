package com.jippytalk.Common;

/**
 * Developer Name: Vidya Sagar
 * Created on: 10-04-2026
 */

import android.util.Log;

import com.jippytalk.Extras;

/**
 * ApiLogger - Unified HTTP request/response logger.
 *
 * All network calls in the app should use this to produce consistent, filterable
 * logcat output. Filter by tag "JippyTalkAPI" in logcat to see every HTTP call
 * the app makes — URL, method, headers, body, status code, response, duration.
 *
 * Usage:
 *   long startTime = ApiLogger.logRequest("POST", API.LOGIN_URL, requestBody, null);
 *   // ... make HTTP call ...
 *   ApiLogger.logResponse("POST", API.LOGIN_URL, statusCode, responseBody, startTime);
 *
 * For errors:
 *   ApiLogger.logError("POST", API.LOGIN_URL, errorMessage, startTime);
 *
 * For long bodies (file uploads, key bundles), use:
 *   ApiLogger.logRequestMeta("POST", url, "body size: 12345 bytes", null);
 */
public class ApiLogger {

    private static final int    MAX_BODY_LOG_LENGTH     =   2000;

    private ApiLogger() {}

    // -------------------- Request Logging Starts Here ---------------------

    /**
     * Logs an outgoing HTTP request with its full body.
     *
     * @param method    HTTP method (GET, POST, PUT, DELETE)
     * @param url       the full URL
     * @param body      the request body (can be null for GET)
     * @param bearer    the Bearer token (may be null if no auth)
     * @return start timestamp in millis — pass to logResponse() for duration tracking
     */
    public static long logRequest(String method, String url, String body, String bearer) {
        long    startTime   =   System.currentTimeMillis();

        Log.e(Extras.LOG_API, "════════════════════ API SEND ═══════════════════");
        Log.e(Extras.LOG_API, "→ " + method + " " + url);
        if (bearer != null && !bearer.isEmpty()) {
            Log.e(Extras.LOG_API, "  Authorization: Bearer " + maskToken(bearer));
        }
        if (body != null && !body.isEmpty()) {
            Log.e(Extras.LOG_API, "  Body: " + truncate(body));
        }
        Log.e(Extras.LOG_API, "══════════════════════════════════════════════════");

        return startTime;
    }

    /**
     * Logs an outgoing HTTP request with metadata only (no full body).
     * Use this for large uploads (files, key bundles) where logging the full body
     * would pollute logcat.
     *
     * @param method    HTTP method
     * @param url       the full URL
     * @param bodyMeta  short description of the body (e.g. "23KB of file bytes")
     * @param bearer    the Bearer token (may be null)
     * @return start timestamp in millis
     */
    public static long logRequestMeta(String method, String url, String bodyMeta, String bearer) {
        long    startTime   =   System.currentTimeMillis();

        Log.e(Extras.LOG_API, "════════════════════ API SEND ═══════════════════");
        Log.e(Extras.LOG_API, "→ " + method + " " + url);
        if (bearer != null && !bearer.isEmpty()) {
            Log.e(Extras.LOG_API, "  Authorization: Bearer " + maskToken(bearer));
        }
        if (bodyMeta != null && !bodyMeta.isEmpty()) {
            Log.e(Extras.LOG_API, "  Body: [" + bodyMeta + "]");
        }
        Log.e(Extras.LOG_API, "══════════════════════════════════════════════════");

        return startTime;
    }

    // -------------------- Response Logging Starts Here ---------------------

    /**
     * Logs an HTTP response with its body and call duration.
     *
     * @param method        HTTP method (from the original request)
     * @param url           the full URL (from the original request)
     * @param statusCode    HTTP response code
     * @param body          the response body text (can be null)
     * @param startTime     the start time returned by logRequest()
     */
    public static void logResponse(String method, String url, int statusCode, String body, long startTime) {
        long    duration    =   System.currentTimeMillis() - startTime;
        String  statusLabel =   statusCode >= 200 && statusCode < 300 ? "OK" :
                                statusCode >= 400 && statusCode < 500 ? "CLIENT-ERR" :
                                statusCode >= 500 ? "SERVER-ERR" : "INFO";

        Log.e(Extras.LOG_API, "════════════════════ API RECV ═══════════════════");
        Log.e(Extras.LOG_API, "← " + statusCode + " " + statusLabel + "  " + method + " " + url
                + "  (" + duration + " ms)");
        if (body != null && !body.isEmpty()) {
            Log.e(Extras.LOG_API, "  Body: " + truncate(body));
        }
        Log.e(Extras.LOG_API, "══════════════════════════════════════════════════");
    }

    /**
     * Logs an HTTP response metadata only (no full body).
     * Use for file downloads where logging MBs of data would be catastrophic.
     */
    public static void logResponseMeta(String method, String url, int statusCode, String bodyMeta, long startTime) {
        long    duration    =   System.currentTimeMillis() - startTime;
        String  statusLabel =   statusCode >= 200 && statusCode < 300 ? "OK" :
                                statusCode >= 400 && statusCode < 500 ? "CLIENT-ERR" :
                                statusCode >= 500 ? "SERVER-ERR" : "INFO";

        Log.e(Extras.LOG_API, "════════════════════ API RECV ═══════════════════");
        Log.e(Extras.LOG_API, "← " + statusCode + " " + statusLabel + "  " + method + " " + url
                + "  (" + duration + " ms)");
        if (bodyMeta != null && !bodyMeta.isEmpty()) {
            Log.e(Extras.LOG_API, "  Body: [" + bodyMeta + "]");
        }
        Log.e(Extras.LOG_API, "══════════════════════════════════════════════════");
    }

    // -------------------- Error Logging Starts Here ---------------------

    /**
     * Logs an API exception or network-level failure (timeout, DNS, SSL, etc).
     *
     * @param method        HTTP method
     * @param url           the full URL
     * @param errorMessage  the error description
     * @param startTime     the start time returned by logRequest()
     */
    public static void logError(String method, String url, String errorMessage, long startTime) {
        long    duration    =   System.currentTimeMillis() - startTime;

        Log.e(Extras.LOG_API, "════════════════════ API FAIL ═══════════════════");
        Log.e(Extras.LOG_API, "✗ FAILED  " + method + " " + url + "  (" + duration + " ms)");
        Log.e(Extras.LOG_API, "  Error: " + errorMessage);
        Log.e(Extras.LOG_API, "══════════════════════════════════════════════════");
    }

    // -------------------- Helpers ---------------------

    /**
     * Truncates a body string to MAX_BODY_LOG_LENGTH characters. Appends a note showing
     * how much was cut so the reader knows the log isn't the full payload.
     */
    private static String truncate(String body) {
        if (body == null) return "";
        if (body.length() <= MAX_BODY_LOG_LENGTH) return body;
        return body.substring(0, MAX_BODY_LOG_LENGTH)
                + "... [truncated " + (body.length() - MAX_BODY_LOG_LENGTH) + " chars]";
    }

    /**
     * Masks a JWT so only the prefix is visible in logs.
     * Never log the full token — it's a credential.
     */
    private static String maskToken(String token) {
        if (token == null || token.length() < 12) return "***";
        return token.substring(0, 8) + "..." + token.substring(token.length() - 4);
    }
}
