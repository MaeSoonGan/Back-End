package com.mock.maesoongan.userrealtime.realtime;

import com.mock.maesoongan.userrealtime.auth.CurrentMemberProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "Realtime", description = "User realtime SSE API")
@SecurityRequirement(name = "bearerAuth")
@RestController
@RequestMapping("/api/realtime")
public class RealtimeController {

    private final RealtimeSessionManager sessionManager;
    private final CurrentMemberProvider currentMemberProvider;

    public RealtimeController(RealtimeSessionManager sessionManager, CurrentMemberProvider currentMemberProvider) {
        this.sessionManager = sessionManager;
        this.currentMemberProvider = currentMemberProvider;
    }

    @Operation(summary = "Subscribe user realtime event stream")
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return sessionManager.connect(currentMemberProvider.memberId());
    }
}
