package com.example.chatservice.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Getter;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MetricsService {

    private final Counter messagesSentCounter;
    private final AtomicInteger activeSessionsGauge;

    public MetricsService(MeterRegistry meterRegistry) {
        this.messagesSentCounter = Counter.builder("chat.messages.total")
                .description("Total number of chat messages sent")
                .register(meterRegistry);
        this.activeSessionsGauge = new AtomicInteger(0);
        io.micrometer.core.instrument.Gauge.builder("chat.active.sessions", activeSessionsGauge, AtomicInteger::get)
                .description("Number of active WebSocket sessions")
                .register(meterRegistry);
    }

    public void incrementMessageCount() {
        messagesSentCounter.increment();
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        activeSessionsGauge.incrementAndGet();
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        activeSessionsGauge.decrementAndGet();
    }
}
