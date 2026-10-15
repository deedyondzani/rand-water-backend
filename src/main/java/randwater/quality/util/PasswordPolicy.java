package randwater.quality.util;

import java.util.regex.Pattern;

/**
 * Central password policy. All password-setting paths must call validate().
 * Policy: min 8 chars, at least 1 uppercase, 1 lowercase, 1 digit, 1 special.
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final Pattern UPPER   = Pattern.compile("[A-Z]");
    private static final Pattern LOWER   = Pattern.compile("[a-z]");
    private static final Pattern DIGIT   = Pattern.compile("[0-9]");
    private static final Pattern SPECIAL = Pattern.compile("[^A-Za-z0-9]");

    private PasswordPolicy() {}

    /**
     * @return null if valid, or a human-readable error message describing what's missing.
     */
    public static String validate(String password) {
        if (password == null || password.isEmpty()) {
            return "Password is required.";
        }
        if (password.length() < MIN_LENGTH) {
            return "Password must be at least " + MIN_LENGTH + " characters.";
        }
        if (!UPPER.matcher(password).find()) {
            return "Password must contain at least one uppercase letter (A-Z).";
        }
        if (!LOWER.matcher(password).find()) {
            return "Password must contain at least one lowercase letter (a-z).";
        }
        if (!DIGIT.matcher(password).find()) {
            return "Password must contain at least one number (0-9).";
        }
        if (!SPECIAL.matcher(password).find()) {
            return "Password must contain at least one special character (e.g. @ # $ ! % ^ & *).";
        }
        return null; // valid
    }
}
