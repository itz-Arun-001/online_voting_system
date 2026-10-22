package com.example.voting.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> items, long total, int page, int size) {
    public static <S, T> PageResponse<T> of(Page<S> p, Function<S, T> mapper) {
        return new PageResponse<>(p.getContent().stream().map(mapper).toList(), p.getTotalElements(), p.getNumber(), p.getSize());
    }
}
