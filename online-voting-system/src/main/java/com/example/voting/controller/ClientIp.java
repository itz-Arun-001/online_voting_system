package com.example.voting.controller;

import jakarta.servlet.http.HttpServletRequest;

final class ClientIp {
    private ClientIp() {}

    static String of(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return req.getRemoteAddr();
    }
}
