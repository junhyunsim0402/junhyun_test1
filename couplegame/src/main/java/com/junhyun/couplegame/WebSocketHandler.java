package com.junhyun.couplegame;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
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

// 플레이어 정보를 담는 클래스
class Player {
    public final WebSocketSession session;
    public final String name;
    public Integer choice;

    public Player(WebSocketSession session, String name) {
        this.session = session;
        this.name = name;
    }
}

class GameRoom {
    public String gameMode; // "couple" or "friend"
    public List<Question> questions;
    public int currentQuestionIndex = 0;
    public final Map<WebSocketSession, Player> players = new ConcurrentHashMap<>();
    public int matchCount = 0;
    public boolean isNextQuestionRequested = false; // 다음 질문 요청 여부 플래그

    public void addPlayer(Player player) {
        players.put(player.session, player);
    }

    public void clearAnswers() {
        players.values().forEach(p -> p.choice = null);
    }

    public boolean allAnswered() {
        return players.values().stream().allMatch(p -> p.choice != null);
    }
}

@Component
public class WebSocketHandler extends TextWebSocketHandler {

    private final Map<String, GameRoom> gameRooms = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, List<Question>> questionsByMode = Map.of(
        "couple", List.of(
            new Question("더 선호하는 데이트는?", "사람 많은 시내 데이트", "조용한 근교 데이트"),
            new Question("둘이 볼 영화를 고른다면?", "스트레스 풀리는 액션 영화", "눈물 쏙 빼는 멜로 영화"),
            new Question("여행을 간다면?", "편안한 5성급 호텔", "감성 넘치는 에어비앤비"),
            new Question("연인에게 더 서운한 순간은?", "내 얘기를 건성으로 들을 때", "중요한 약속을 잊었을 때"),
            new Question("다시 태어나도 지금 연인과?", "무조건 다시 만나기", "한 번쯤은 다른 사람도..?")
        ),
        "friend", List.of(
            new Question("더 좋아하는 음식은?", "치킨", "피자"),
            new Question("선호하는 여행 스타일은?", "모든 것을 계획하는 J형 여행", "발길 닿는 대로 P형 여행"),
            new Question("주말에 뭐할까?", "집에서 뒹굴뒹굴", "나가서 액티비티 즐기기"),
            new Question("돈벼락을 맞는다면?", "같이 플렉스하기", "일단 저축부터"),
            new Question("더 힘든 것은?", "배고픔 참기", "졸음 참기")
        )
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
        GameRoom room = gameRooms.get(code);

        if ("JOIN".equals(type)) {
            String name = (String) messageMap.get("name");
            GameRoom newOrExistingRoom = gameRooms.computeIfAbsent(code, k -> new GameRoom());
            newOrExistingRoom.addPlayer(new Player(session, name));
            System.out.println("플레이어 [" + session.getId() + ", " + name + "]가 방 [" + code + "]에 참여했습니다.");

            if (newOrExistingRoom.players.size() == 2) {
                System.out.println("방 [" + code + "]에 2명이 모두 모였습니다. 게임을 시작합니다.");
                sendMessageToRoom(newOrExistingRoom, Map.of("type", "START"));
            }
        }
        else if ("CHOOSE_MODE".equals(type)) {
            if (room == null) return;
            String mode = (String) messageMap.get("mode");
            room.gameMode = mode;
            room.questions = questionsByMode.get(mode);

            // 다른 플레이어에게도 모드가 선택되었음을 알림
            sendMessageToRoom(room, Map.of("type", "MODE_SELECTED"));

            // 잠시 후 첫 질문 전송
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    sendQuestionToRoom(room, 0);
                }
            }, 1000); // 1초 딜레이
        }
        else if ("ANSWER".equals(type)) {
            if (room == null) return;
            Integer choice = (Integer) messageMap.get("choice");
            Player currentPlayer = room.players.get(session);
            if (currentPlayer != null) {
                currentPlayer.choice = choice;
                
                System.out.println("## 테스트 ## 플레이어 [" + session.getId() + "]의 선택: " + choice.toString());
            }

            if (room.allAnswered()) {
                System.out.println("## 테스트 ## 모든 플레이어의 선택이 완료되었습니다.");

                // 답변 일치 여부 확인
                List<Integer> choices = room.players.values().stream().map(p -> p.choice).toList();
                if (choices.get(0).equals(choices.get(1))) {
                    room.matchCount++;
                }

                // 각 플레이어에게 상대방의 선택과 이름을 포함하여 결과 전송
                List<Player> playerList = new ArrayList<>(room.players.values());
                Player p1 = playerList.get(0);
                Player p2 = playerList.get(1);

                for (Player player : playerList) {
                    WebSocketSession s = player.session;
                    Player opponent = (player == p1) ? p2 : p1;

                    sendMessageToSession(s, Map.of(
                        "type", "RESULT",
                        "myName", player.name,
                        "myChoice", player.choice.toString(),
                        "opponentName", opponent.name,
                        "opponentChoice", opponent.choice.toString()
                    ));
                }
            }
        }
        else if ("NEXT_QUESTION".equals(type)) {
            if (room == null) return;
            // 한 명의 클라이언트만 다음 질문 요청을 보내도록 프론트에서 제어하므로, 첫번째 플레이어인지 확인할 필요가 줄어듦
            // (하지만 여러 요청이 동시에 들어오는 것을 막기 위해 동기화 처리)
            synchronized (room) { 
                if (room.isNextQuestionRequested) return; // 이미 다음 질문이 요청되었으면 무시
                room.isNextQuestionRequested = true; // 요청 플래그 설정
                int nextQuestionIndex = room.currentQuestionIndex + 1;
                if (nextQuestionIndex < room.questions.size()) {
                    sendQuestionToRoom(room, nextQuestionIndex);
                } else {
                    // 게임 종료 및 결과 계산
                    int totalQuestions = room.questions.size();
                    int percentage = (int) Math.round((double) room.matchCount / totalQuestions * 100);
                    String resultText = String.format("총 %d문제 중 %d개를 맞혔어요! 두 분의 일치율은 %d%% 입니다!", totalQuestions, room.matchCount, percentage);
                    String aiMessage = generateAiMessage(percentage, room.gameMode);

                    sendMessageToRoom(room, Map.of(
                        "type", "GAME_OVER",
                        "resultText", resultText,
                        "aiMessage", aiMessage
                    ));
                    // 게임방 정보 삭제
                    cleanupGameRoom(code);
                }
            }
        }
    }

    private String generateAiMessage(int percentage, String mode) {
        if ("couple".equals(mode)) {
            if (percentage >= 80) return "천생연분! 서로를 너무 잘 아는 커플이시네요!";
            if (percentage >= 50) return "아주 좋아요! 조금 더 알아가면 완벽한 커플이 될 거예요.";
            return "아직은 서로 알아가는 단계! 같이 시간을 보내며 더 가까워져 보세요.";
        } else { // friend
            if (percentage >= 80) return "말이 필요 없는 베프! 평생 함께할 친구 사이네요.";
            if (percentage >= 50) return "쿵짝이 잘 맞네요! 취향이 비슷한 좋은 친구 사이입니다.";
            return "서로 다른 매력을 가진 친구들! 달라서 더 재밌는 사이가 될 수 있어요.";
        }
    }

    private void sendQuestionToRoom(GameRoom room, int questionIndex) {
        room.currentQuestionIndex = questionIndex;
        room.clearAnswers();
        room.isNextQuestionRequested = false; // 다음 질문으로 넘어가므로 요청 플래그 초기화

        if (questionIndex < room.questions.size()) {
            Question q = room.questions.get(questionIndex);
            sendMessageToRoom(room, Map.of(
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

    private void sendMessageToRoom(GameRoom room, Map<String, ?> message) {
        try {
            String jsonMessage = objectMapper.writeValueAsString(message);
            for (WebSocketSession s : room.players.keySet()) {
                s.sendMessage(new TextMessage(jsonMessage));
            }
        } catch (IOException e) {
            System.err.println("메시지 전송 중 오류 발생: " + e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        // 플레이어가 연결을 끊었을 때 해당 방을 찾아서 정리
        gameRooms.forEach((code, room) -> {
            if (room.players.containsKey(session)) {
                room.players.remove(session);
                System.out.println("플레이어 [" + session.getId() + "]가 방 [" + code + "]에서 나갔습니다.");
                // 다른 플레이어에게 연결이 끊겼음을 알림
                sendMessageToRoom(room, Map.of("type", "GAME_OVER", "resultText", "상대방이 연결을 끊었습니다.", "aiMessage", "게임이 종료되었습니다."));
                cleanupGameRoom(code);
            }
        });
    }

    private void cleanupGameRoom(String code) {
        if (gameRooms.remove(code) != null) {
            System.out.println("게임방 [" + code + "]의 정보를 삭제했습니다.");
        }
    }
}