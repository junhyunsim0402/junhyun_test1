package com.junhyun.couplegame;

import org.springframework.web.bind.annotation.CrossOrigin; // 이 줄이 추가됐어요!
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin("*") // 이 줄이 추가됐어요! 모든 외부 요청을 허용한다는 뜻이에요.
@RestController
public class GameController {

    // ... (이하 내용은 이전과 동일합니다) ...
    private java.util.Map<String, Object> gameRooms = new java.util.HashMap<>();

    @GetMapping("/api/hello")
    public String sayHello() {
        return "안녕하세요! 우리 커플 게임 서버에 오신 것을 환영합니다!";
    }

    @GetMapping("/api/game/create")
    public String createGameRoom() {
        String inviteCode;
        do {
            inviteCode = generateInviteCode();
        } while (gameRooms.containsKey(inviteCode));
        
        gameRooms.put(inviteCode, "새로운 게임방 정보");
        
        System.out.println("게임방 생성됨. 초대 코드: " + inviteCode);
        
        return inviteCode;
    }
    
    @GetMapping("/api/game/join/{inviteCode}")
    public String joinGame(@PathVariable String inviteCode) {
        // gameRooms(사물함)에 해당 inviteCode(열쇠)가 있는지 확인
        if (gameRooms.containsKey(inviteCode)) {
            System.out.println("참여 성공. 코드: " + inviteCode);
            return "게임방에 성공적으로 참여했습니다!";
        } else {
            System.out.println("참여 실패. 존재하지 않는 코드: " + inviteCode);
            return "존재하지 않는 초대 코드입니다.";
        }
    }
    
    private String generateInviteCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder sb = new StringBuilder();
        java.util.Random random = new java.util.Random();
        for (int i = 0; i < 6; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }
}