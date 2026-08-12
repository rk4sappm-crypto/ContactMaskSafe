package com.example.contactmasksafe;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PhoneMasker {
    public static final String MASK = "Masked";

    private static final Pattern PHONE_CANDIDATE = Pattern.compile(
            "(?<![\\p{L}\\p{Nd}])(?:\\+?\\p{Nd}[\\p{Nd}\\s\\u00A0().\\-–—/\\u200E\\u200F\\u202A-\\u202E]{4,}\\p{Nd})(?![\\p{L}\\p{Nd}])"
    );

    private PhoneMasker() { }

    public static boolean containsSavedNumber(CharSequence text, SavedNumberRepository repository) {
        if (text == null || text.length() == 0 || repository == null) return false;
        Matcher matcher = PHONE_CANDIDATE.matcher(text);
        while (matcher.find()) {
            String candidate = matcher.group();
            int digitCount = digitsOnly(candidate).length();
            if (digitCount >= 7 && digitCount <= 16 && repository.isSavedNumber(candidate)) return true;
        }
        return false;
    }

    public static String redactSavedNumbers(CharSequence text, SavedNumberRepository repository) {
        if (text == null) return "";
        Matcher matcher = PHONE_CANDIDATE.matcher(text);
        StringBuffer output = new StringBuffer();
        boolean changed = false;
        while (matcher.find()) {
            String candidate = matcher.group();
            int digitCount = digitsOnly(candidate).length();
            if (digitCount >= 7 && digitCount <= 16 && repository != null && repository.isSavedNumber(candidate)) {
                matcher.appendReplacement(output, Matcher.quoteReplacement(MASK));
                changed = true;
            }
        }
        if (!changed) return text.toString();
        matcher.appendTail(output);
        return output.toString();
    }

    public static String digitsOnly(String value) {
        if (value == null) return "";
        StringBuilder digits = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isDigit(c)) {
                int numeric = Character.getNumericValue(c);
                if (numeric >= 0 && numeric <= 9) digits.append((char) ('0' + numeric));
            }
        }
        return digits.toString();
    }
}
