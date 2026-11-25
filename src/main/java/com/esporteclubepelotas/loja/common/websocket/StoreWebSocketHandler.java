package com.esporteclubepelotas.loja.common.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class StoreWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public void broadcastAfterCommit(String topic, String action, String referenceId) {
        Runnable task = () -> sendEvent(topic, action, referenceId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    task.run();
                }
            });
        } else {
            task.run();
        }
    }

    private void sendEvent(String topic, String action, String referenceId) {
        if (sessions.isEmpty()) return;
        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "topic", topic,
                    "action", action,
                    "referenceId", referenceId != null ? referenceId : "",
                    "timestamp", Instant.now().toString()
            ));
            TextMessage message = new TextMessage(json);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(message);
                    } catch (Exception ex) {
                        log.debug("Erro ao enviar WebSocket para sessão {}: {}", session.getId(), ex.getMessage());
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Falha ao serializar evento WebSocket: {}", ex.getMessage());
        }
    }
}
