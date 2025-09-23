package com.junhyun.couplegame;

import com.fasterxml.jackson.databind.ObjectMapper; // JSON 처리를 위한 import
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    // 게임방 별로 어떤 세션(플레이어)들이 있는지 저장하는 공간
    // Key: 초대 코드(String), Value: 해당 방에 접속한 세션들(Set<WebSocketSession>)
    private final Map<String, Set<WebSocketSession>> gameSessions = new ConcurrentHashMap<>();

    // JSON <-> Java 객체 변환을 도와주는 도구
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("새로운 유저 접속 시도! 세션 ID: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        System.out.println("메시지 수신 [" + session.getId() + "]: " + payload);

        // 받은 메시지(JSON 형식)를 Map 형태로 변환
        Map<String, String> messageMap = objectMapper.readValue(payload, Map.class);
        String type = messageMap.get("type");
        String code = messageMap.get("code");

        // 메시지 타입이 "JOIN" 이라면, 해당 게임방에 플레이어를 추가
        if ("JOIN".equals(type)) {
            // 해당 코드를 가진 방이 없으면 새로 만들고, 있으면 기존 방을 가져옴
            Set<WebSocketSession> sessions = gameSessions.computeIfAbsent(code, k -> ConcurrentHashMap.newKeySet());
            sessions.add(session); // 해당 방에 현재 세션(플레이어)을 추가

            System.out.println("플레이어 [" + session.getId() + "]가 방 [" + code + "]에 참여했습니다.");

            // 방에 2명이 모이면, 두 명 모두에게 "START" 메시지를 보냄
            if (sessions.size() == 2) {
                System.out.println("방 [" + code + "]에 2명이 모두 모였습니다. 게임을 시작합니다.");
                TextMessage startMessage = new TextMessage("{\"type\":\"START\"}");
                for (WebSocketSession s : sessions) {
                    s.sendMessage(startMessage);
                }
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 플레이어가 접속을 끊으면, 모든 게임방을 뒤져서 해당 플레이어를 삭제
        gameSessions.values().forEach(sessions -> sessions.remove(session));
        System.out.println("유저 접속 해제! 세션 ID: " + session.getId());
    }
}