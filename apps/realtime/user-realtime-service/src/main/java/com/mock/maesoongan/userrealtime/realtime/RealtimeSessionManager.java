package com.mock.maesoongan.userrealtime.realtime;

import com.mock.maesoongan.userrealtime.realtime.RealtimeDtos.HeartbeatEvent;
import com.mock.maesoongan.userrealtime.realtime.RealtimeDtos.StreamConnectedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Component
public class RealtimeSessionManager {

    private final long sseTimeoutMs;
    private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public RealtimeSessionManager(@Value("${app.realtime.sse-timeout-ms:1800000}") long sseTimeoutMs) {
        this.sseTimeoutMs = sseTimeoutMs;
    }

    public SseEmitter connect(long memberId) {
        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        emitters.computeIfAbsent(memberId, ignored -> new CopyOnWriteArraySet<>()).add(emitter);

        emitter.onCompletion(() -> remove(memberId, emitter));
        emitter.onTimeout(() -> remove(memberId, emitter));
        emitter.onError(error -> remove(memberId, emitter));

        send(memberId, emitter, RealtimeEventType.CONNECTED.name(), new StreamConnectedEvent(
                memberId,
                "Realtime stream connected",
                LocalDateTime.now()
        ));
        return emitter;
    }

    public int publish(long memberId, String eventName, Object data) {
        Set<SseEmitter> targets = emitters.get(memberId);
        if (targets == null || targets.isEmpty()) {
            return 0;
        }

        int delivered = 0;
        for (SseEmitter emitter : targets) {
            if (send(memberId, emitter, eventName, data)) {
                delivered++;
            }
        }
        return delivered;
    }

    public int totalConnectionCount() {
        return emitters.values()
                .stream()
                .mapToInt(Set::size)
                .sum();
    }

    @Scheduled(fixedDelayString = "${app.realtime.heartbeat-interval-ms:30000}")
    public void heartbeat() {
        for (Map.Entry<Long, Set<SseEmitter>> entry : emitters.entrySet()) {
            long memberId = entry.getKey();
            for (SseEmitter emitter : entry.getValue()) {
                send(memberId, emitter, RealtimeEventType.HEARTBEAT.name(), new HeartbeatEvent(memberId, LocalDateTime.now()));
            }
        }
    }

    private boolean send(long memberId, SseEmitter emitter, String eventName, Object data) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
            return true;
        } catch (IOException | IllegalStateException exception) {
            remove(memberId, emitter);
            return false;
        }
    }

    private void remove(long memberId, SseEmitter emitter) {
        Set<SseEmitter> memberEmitters = emitters.get(memberId);
        if (memberEmitters == null) {
            return;
        }
        memberEmitters.remove(emitter);
        if (memberEmitters.isEmpty()) {
            emitters.remove(memberId);
        }
    }
}
