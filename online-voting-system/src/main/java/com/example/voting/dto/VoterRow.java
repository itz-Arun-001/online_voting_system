package com.example.voting.dto;

import java.time.Instant;

public record VoterRow(String email, String name, Instant votedAt) {}
