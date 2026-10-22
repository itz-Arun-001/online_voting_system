package com.example.voting.util;

import java.util.Locale;
import java.util.regex.Pattern;

/** Every email in the system goes through normalize() first. */
public final class EmailUtil {
    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9._%+'-]+@[a-z0-9.-]+\\.[a-z]{2,}$");

    private EmailUtil() {}

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Expects an already-normalized address. */
    public static boolean isValid(String email) {
        return email != null && email.length() <= 254 && FORMAT.matcher(email).matches();
    }

    /** Expects an already-normalized address. Domain may be given with or without a leading @. */
    public static boolean hasDomain(String email, String domain) {
        if (email == null || domain == null) return false;
        String d = normalize(domain);
        if (d.startsWith("@")) d = d.substring(1);
        return email.endsWith("@" + d);
    }
}
