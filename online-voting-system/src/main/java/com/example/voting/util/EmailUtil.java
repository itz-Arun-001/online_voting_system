package com.example.voting.util;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/** Every email in the system goes through normalize() first. */
public final class EmailUtil {
    private static final Pattern FORMAT = Pattern.compile("^[a-z0-9._%+'-]+@[a-z0-9.-]+\\.[a-z]{2,}$");
    private static final Map<String, Boolean> DOMAIN_MX_CACHE = new ConcurrentHashMap<>();

    private EmailUtil() {}

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Expects an already-normalized address. */
    public static boolean isValid(String email) {
        return email != null && email.length() <= 254 && FORMAT.matcher(email).matches();
    }

    public static String getDomain(String email) {
        if (email == null || !email.contains("@")) return null;
        return email.substring(email.indexOf("@") + 1).trim().toLowerCase(Locale.ROOT);
    }

    /** Expects an already-normalized address. Domain may be given with or without a leading @. */
    public static boolean hasDomain(String email, String domain) {
        if (email == null || domain == null) return false;
        String d = normalize(domain);
        if (d.startsWith("@")) d = d.substring(1);
        return email.endsWith("@" + d);
    }

    /** Checks DNS to verify that the domain has active MX (mail exchange) or A records. */
    public static boolean hasValidMailServer(String domain) {
        if (domain == null || domain.isBlank()) return false;
        String d = domain.toLowerCase(Locale.ROOT).trim();
        if (d.startsWith("@")) d = d.substring(1);
        if (DOMAIN_MX_CACHE.containsKey(d)) return DOMAIN_MX_CACHE.get(d);

        try {
            java.util.Hashtable<String, String> env = new java.util.Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            javax.naming.directory.DirContext ictx = new javax.naming.directory.InitialDirContext(env);
            javax.naming.directory.Attributes attrs = ictx.getAttributes(d, new String[] { "MX", "A" });
            boolean valid = (attrs.get("MX") != null && attrs.get("MX").size() > 0)
                    || (attrs.get("A") != null && attrs.get("A").size() > 0);
            DOMAIN_MX_CACHE.put(d, valid);
            return valid;
        } catch (Exception ex) {
            DOMAIN_MX_CACHE.put(d, false);
            return false;
        }
    }
}
