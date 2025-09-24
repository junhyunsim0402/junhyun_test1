package com.junhyun.couplegame;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

class Question {
    public String question;
    public String choice1;
    public String choice2;

    public Question(String question, String choice1, String choice2) {
        this.question = question;
        this.choice1 = choice1;
        this.choice2 = choice2;
    }
}

class GameRoom {
    public int currentQuestionIndex = 0;
    public Map<WebSocketSession, Integer> answers = new ConcurrentHashMap<>();
}

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private final Map<String, Set<WebSocketSession>> gameSessions = new ConcurrentHashMap<>();
    private final Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Question> questions = List.of(
            new Question("더 선호하는 데이트는?", "사람 많은 시내 데이트", "조용한 근교 데이트"),
            new Question("둘이 볼 영화를 고른다면?", "스트레스 풀리는 액션 영화", "눈물 쏙 빼는 멜로 영화"),
            new Question("여행을 간다면?", "편안한 5성급 호텔", "감성 넘치는 에어비앤비")
    );

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        System.out.println("새로운 유저 접속 시도! 세션 ID: " + session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws IOException {
        String payload = message.getPayload();
        Map<String, Object> messageMap = objectMapper.readValue(payload, new TypeReference<>() {});
        String type = (String) messageMap.get("type");
        String code = (String) messageMap.get("code");

        if ("JOIN".equals(type)) {
            Set<WebSocketSession> sessions = gameSessions.computeIfAbsent(code, k -> ConcurrentHashMap.newKeySet());
            sessions.add(session);
            gameRooms.putIfAbsent(code, new GameRoom());
            System.out.println("플레이어 [" + session.getId() + "]가 방 [" + code + "]에 참여했습니다.");

            if (sessions.size() == 2) {
                System.out.println("방 [" + code + "]에 2명이 모두 모였습니다. 게임을 시작합니다.");
                sendMessageToRoom(sessions, Map.of("type", "START"));
                sendQuestionToRoom(code, sessions, 0);
            }
        } 
        else if ("ANSWER".equals(type)) {
            Integer choice = (Integer) messageMap.get("choice");
            GameRoom room = gameRooms.get(code);
            room.answers.put(session, choice);

            if (room.answers.size() == 2) {
                Set<WebSocketSession> sessions = gameSessions.get(code);
                for (WebSocketSession s : sessions) {
                    Integer myChoice = room.answers.get(s);
                    Integer opponentChoice = room.answers.entrySet().stream()
                            .filter(entry -> !entry.getKey().equals(s))
                            .findFirst().get().getValue();
                    
                    sendMessageToSession(s, Map.of(
                        "type", "RESULT",
                        "myChoice", myChoice.toString(),
                        "opponentChoice", opponentChoice.toString()
                    ));
                }
            }
        }
        else if ("NEXT_QUESTION".equals(type)) {
            GameRoom room = gameRooms.get(code);
            if (session.equals(gameSessions.get(code).iterator().next())) {
                int nextQuestionIndex = room.currentQuestionIndex + 1;
                if (nextQuestionIndex < questions.size()) {
                    sendQuestionToRoom(code, gameSessions.get(code), nextQuestionIndex);
                } else {
                    sendMessageToRoom(gameSessions.get(code), Map.of(
                        "type", "GAME_OVER",
                        "text", "모든 질문에 답했습니다. 즐거운 시간 되셨나요?"
                    ));
                }
            }
        }
    }

    private void sendQuestionToRoom(String code, Set<WebSocketSession> sessions, int questionIndex) {
        GameRoom room = gameRooms.get(code);
        room.currentQuestionIndex = questionIndex;
        room.answers.clear();

        if (questionIndex < questions.size()) {
            Question q = questions.get(questionIndex);
            sendMessageToRoom(sessions, Map.of(
                    "type", "QUESTION",
                    "question", q.question,
                    "choice1", q.choice1,
                    "choice2", q.choice2
            ));
        }
    }
    
    private void sendMessageToSession(WebSocketSession session, Map<String, String> message) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(message)));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendMessageToRoom(Set<WebSocketSession> sessions, Map<String, ?> message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            for (WebSocketSession s : sessions) {
                s.sendMessage(new TextMessage(jsonMessage));
            }
        } catch (IOException e) {
            System.err.println("메시지 전송 중 오류 발생: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        gameSessions.values().forEach(sessions -> sessions.remove(session));
        System.out.println("유저 접속 해제! 세션 ID: " + session.getId());
    }
}