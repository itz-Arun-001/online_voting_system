package com.example.voting.controller;

import com.example.voting.config.AppProperties;
import com.example.voting.dto.Dtos.*;
import com.example.voting.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final AppProperties props;

    @PostMapping("/api/auth/request-otp")
    public MessageResponse requestOtp(@Valid @RequestBody OtpRequest r, HttpServletRequest req) {
        return authService.requestOtp(r.email(), ClientIp.of(req));
    }

    @PostMapping("/api/auth/verify-otp")
    public AuthResponse verifyOtp(@Valid @RequestBody VerifyRequest r, HttpServletRequest req) {
        return authService.verifyOtp(r.email(), r.otp(), ClientIp.of(req));
    }

    @GetMapping("/api/public/config")
    public Map<String, Object> config() {
        return Map.of("collegeDomain", props.domain(), "otpLength", props.getOtp().getLength());
    }
}
