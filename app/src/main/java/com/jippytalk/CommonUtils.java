package com.jippytalk;

/**
 * Developer Name: Vidya Sagar
 * Created on: 06-04-2026
 */

import android.util.Log;
import android.util.Patterns;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * CommonUtils - Utility class providing common helper methods used across the application.
 * Contains hashing utilities and message content detection methods.
 */
public class CommonUtils {

    // -------------------- Hashing Methods Starts Here ---------------------

    /**
     * Generates a SHA-256 hash of the given input string.
     * Used for hashing phone numbers before storage in the contacts database.
     *
     * @param input the string to hash
     * @return hex-encoded SHA-256 hash string, or empty string if hashing fails
     */
    public static String sha256Hash(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        try {
            MessageDigest   digest      =   MessageDigest.getInstance("SHA-256");
            byte[]          hashBytes   =   digest.digest(input.getBytes(StandardCharsets.UTF_8));

            // Convert byte array to hex string
            StringBuilder   hexString   =   new StringBuilder();
            for (byte b : hashBytes) {
                String hex  =   Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        }
        catch (NoSuchAlgorithmException e) {
            Log.e(Extras.LOG_MESSAGE, "SHA-256 algorithm not available " + e.getMessage());
            return "";
        }
    }

    // -------------------- Message Content Detection Methods Starts Here ---------------------

    /**
     * Checks whether the given message text contains a URL link.
     * Used to determine the message type (TEXT vs LINK) before sending.
     *
     * @param message the message text to check
     * @return true if the message contains a valid URL, false otherwise
     */
    public static boolean isMessageALink(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        return Patterns.WEB_URL.matcher(message).find();
    }
}
