package com.example.voting.service;

import com.example.voting.entity.Election;
import com.example.voting.enums.ResultMode;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
@RequiredArgsConstructor
public class SseService {
    private static final Logger log = LoggerFactory.getLogger(SseService.class);
    private static final long TIMEOUT_MS = 30 * 60 * 1000L;

    private final ResultService resultService;
    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Election e) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        List<SseEmitter> list = emitters.computeIfAbsent(e.getId(), k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        emitter.onCompletion(() -> list.remove(emitter));
        emitter.onTimeout(() -> list.remove(emitter));
        emitter.onError(ex -> list.remove(emitter));
        try {
            emitter.send(SseEmitter.event().name("results").data(resultService.compute(e), MediaType.APPLICATION_JSON));
        } catch (Exception ex) {
            list.remove(emitter);
        }
        return emitter;
    }

    /** Pushes fresh counts to everyone watching a LIVE election. Never throws. */
    public void broadcast(Election e) {
        try {
            if (e.getResultMode() != ResultMode.LIVE) return;
            List<SseEmitter> list = emitters.get(e.getId());
            if (list == null || list.isEmpty()) return;
            Object payload = resultService.compute(e);
            for (SseEmitter em : list) {
                try {
                    em.send(SseEmitter.event().name("results").data(payload, MediaType.APPLICATION_JSON));
                } catch (Exception ex) {
                    list.remove(em);
                }
            }
        } catch (Exception ex) {
            log.warn("SSE broadcast failed: {}", ex.getMessage());
        }
    }

    /** Keeps idle connections alive through proxies. */
    @Scheduled(fixedRate = 25000)
    public void heartbeat() {
        emitters.values().forEach(list -> {
            for (SseEmitter em : list) {
                try {
                    em.send(SseEmitter.event().comment("keep-alive"));
                } catch (Exception ex) {
                    list.remove(em);
                }
            }
        });
    }
}
