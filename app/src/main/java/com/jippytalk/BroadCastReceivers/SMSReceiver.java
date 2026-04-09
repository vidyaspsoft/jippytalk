package com.jippytalk.BroadCastReceivers;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.auth.api.phone.SmsRetriever;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.common.api.Status;
import com.jippytalk.Extras;

/**
 * SMSReceiver - BroadcastReceiver for auto-reading OTP SMS messages during user verification.
 *
 * Uses Google Play Services SMS Retriever API to automatically extract the OTP code
 * from incoming SMS messages without requiring READ_SMS permission.
 *
 * The SMS must match the format expected by SMS Retriever API (containing app hash).
 *
 * Registered in AndroidManifest.xml with:
 * - Action: com.google.android.gms.auth.api.phone.SMS_RETRIEVED
 * - Permission: com.google.android.gms.auth.api.phone.permission.SEND
 */
public class SMSReceiver extends BroadcastReceiver {

    private OTPReceiveListener  otpReceiveListener;

    // -------------------- Listener Setup Starts Here ---------------------

    /**
     * Sets the listener that will receive the extracted OTP code.
     *
     * @param otpReceiveListener the callback to notify when OTP is extracted
     */
    public void setOTPReceiveListener(OTPReceiveListener otpReceiveListener) {
        this.otpReceiveListener =   otpReceiveListener;
    }

    // -------------------- Broadcast Handling Starts Here ---------------------

    /**
     * Called when an SMS message matching the SMS Retriever API format is received.
     * Extracts the OTP from the message and notifies the listener.
     *
     * @param context the context in which the receiver is running
     * @param intent  the Intent containing the SMS message data
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !SmsRetriever.SMS_RETRIEVED_ACTION.equals(intent.getAction())) {
            return;
        }

        Bundle extras   =   intent.getExtras();
        if (extras == null) {
            return;
        }

        Status status   =   (Status) extras.get(SmsRetriever.EXTRA_STATUS);
        if (status == null) {
            return;
        }

        switch (status.getStatusCode()) {
            case CommonStatusCodes.SUCCESS -> {
                // Extract the full SMS message
                String message  =   (String) extras.get(SmsRetriever.EXTRA_SMS_MESSAGE);
                if (message != null && otpReceiveListener != null) {
                    // Extract OTP from the message (typically a 6-digit code)
                    String otp  =   extractOTPFromMessage(message);
                    Log.e(Extras.LOG_MESSAGE, "SMS OTP received successfully");
                    otpReceiveListener.onOTPReceived(otp);
                }
            }
            case CommonStatusCodes.TIMEOUT -> {
                Log.e(Extras.LOG_MESSAGE, "SMS Retriever API timeout");
                if (otpReceiveListener != null) {
                    otpReceiveListener.onOTPTimeOut();
                }
            }
        }
    }

    /**
     * Extracts the numeric OTP code from the full SMS message text.
     * Looks for a sequence of 4-6 consecutive digits in the message.
     *
     * @param message the full SMS message text
     * @return the extracted OTP code, or empty string if not found
     */
    private String extractOTPFromMessage(String message) {
        // Extract a 4-6 digit OTP code from the message
        String otp  =   "";
        try {
            java.util.regex.Pattern pattern =   java.util.regex.Pattern.compile("(\\d{4,6})");
            java.util.regex.Matcher matcher =   pattern.matcher(message);
            if (matcher.find()) {
                otp =   matcher.group(0);
            }
        }
        catch (Exception e) {
            Log.e(Extras.LOG_MESSAGE, "Error extracting OTP from SMS " + e.getMessage());
        }
        return otp;
    }

    // -------------------- Callback Interface ---------------------

    /**
     * Callback interface for OTP extraction results.
     */
    public interface OTPReceiveListener {
        /**
         * Called when OTP is successfully extracted from SMS.
         *
         * @param otp the extracted OTP code
         */
        void onOTPReceived(String otp);

        /**
         * Called when SMS Retriever API times out waiting for the OTP SMS.
         */
        void onOTPTimeOut();
    }
}
