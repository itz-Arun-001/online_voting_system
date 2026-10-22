package com.example.voting.controller;

import com.example.voting.dto.Dtos.ResultsResponse;
import com.example.voting.entity.Election;
import com.example.voting.enums.ResultMode;
import com.example.voting.exception.ApiException;
import com.example.voting.security.AuthUser;
import com.example.voting.service.ElectionService;
import com.example.voting.service.ResultService;
import com.example.voting.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/elections/{id}/results")
@RequiredArgsConstructor
public class ResultController {
    private final ElectionService electionService;
    private final ResultService resultService;
    private final SseService sse;

    @GetMapping
    public ResultsResponse results(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        Election e = allowed(id, me);
        if (!electionService.canViewResults(e)) throw ApiException.forbidden(electionService.hiddenMessage(e));
        return resultService.compute(e);
    }

    /** Live stream: only for elections in LIVE mode. */
    @GetMapping(path = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable Long id, @AuthenticationPrincipal AuthUser me) {
        Election e = allowed(id, me);
        if (e.getResultMode() != ResultMode.LIVE) {
            throw ApiException.forbidden("Live results are not turned on for this election.");
        }
        return sse.subscribe(e);
    }

    private Election allowed(Long id, AuthUser me) {
        Election e = electionService.get(id);
        if (!me.isAdmin() && !electionService.isEligible(e, me.email())) {
            throw ApiException.forbidden("You are not on the voter list for this election.");
        }
        return e;
    }
}
