// junhyun.js 파일 전체 내용

document.addEventListener("DOMContentLoaded", () => {
    // 1. HTML 요소들에게 별명 붙여주기 (업데이트)
    const nameScreen = document.querySelector("#name-screen");
    const nameInput = document.querySelector("#name-input");
    const nameConfirmBtn = document.querySelector("#name-confirm-btn");

    const lobbyScreen = document.querySelector("#lobby-screen");
    const playerNameDisplay = document.querySelector("#player-name-display");
    const infoText = document.querySelector("#info-text");
    const createBtn = document.querySelector("#create-btn");
    const joinBtn = document.querySelector("#join-btn");
    const copyBtn = document.querySelector("#copy-btn");
    const codeInput = document.querySelector("#code-input");

    const gameModeScreen = document.querySelector("#game-mode-screen");
    const coupleTestBtn = document.querySelector("#couple-test-btn");
    const friendTestBtn = document.querySelector("#friend-test-btn");

    const gameScreen = document.querySelector("#game-screen");
    const questionP = document.querySelector("#question-p");
    const choice1Btn = document.querySelector("#choice1-btn");
    const choice2Btn = document.querySelector("#choice2-btn");
    const resultP = document.querySelector("#result-p");

    let playerName = "";
    let socket;

    // 2. '이름 확인' 버튼 기능 (신규)
    nameConfirmBtn.addEventListener("click", () => {
        const name = nameInput.value.trim();
        if (!name) {
            return alert("이름을 입력해주세요!");
        }
        playerName = name;
        playerNameDisplay.textContent = playerName;
        nameScreen.style.display = "none";
        lobbyScreen.style.display = "block";
        infoText.innerHTML = `환영합니다, <b id="player-name-display">${playerName}</b>님! <br> 방을 만들거나 참여해주세요.`;
    });

    // 3. '게임방 만들기' 버튼 기능
    createBtn.addEventListener("click", () => {
        fetch("http://localhost:8080/api/game/create")
            .then(response => response.text())
            .then(inviteCode => {
                infoText.textContent = "초대 코드: " + inviteCode;
                copyBtn.style.display = "inline-block"; // '복사하기' 버튼 보이기

                copyBtn.addEventListener("click", () => {
                    navigator.clipboard.writeText(inviteCode).then(() => {
                        alert("초대 코드가 복사되었습니다!");
                    }).catch(err => {
                        console.error("복사 실패: ", err);
                    });
                });
            });
    });

    // 4. '참여하기' 버튼 기능
    joinBtn.addEventListener("click", () => {
        const code = codeInput.value;
        if (!code) { return alert("초대 코드를 입력해주세요!"); }

        fetch("http://localhost:8080/api/game/join/" + code)
            .then(response => response.text())
            .then(message => {
                if (message.includes("성공")) {
                    connectWebSocket();
                } else {
                    infoText.textContent = message;
                    infoText.style.color = "red";
                }
            });
    });

    // 5. 웹소켓 연결 함수
    function connectWebSocket() {
        socket = new WebSocket("ws://localhost:8080/ws/game");

        socket.onopen = function() {
            // 이름과 함께 JOIN 메시지 전송
            socket.send(JSON.stringify({ 
                type: "JOIN", 
                code: codeInput.value,
                name: playerName 
            }));
            infoText.textContent = "상대방을 기다리는 중입니다...";
        };

        socket.onmessage = function(event) {
            const message = JSON.parse(event.data);
            
            // START 신호 받으면 게임 모드 선택 화면으로 전환
            if (message.type === "START") {
                lobbyScreen.style.display = "none";
                gameModeScreen.style.display = "block";
            } 
            else if (message.type === "QUESTION") {
                questionP.textContent = message.question;
                choice1Btn.textContent = message.choice1;
                choice2Btn.textContent = message.choice2;
                resultP.textContent = "";
                choice1Btn.disabled = false;
                choice2Btn.disabled = false;
            }
            // 다른 사람이 게임 모드를 선택했을 때 (신규)
            else if (message.type === "MODE_SELECTED") {
                gameModeScreen.style.display = "none";
                gameScreen.style.display = "block";
            }
            else if (message.type === "RESULT") {
                // 결과 표시에 이름 추가
                resultP.innerHTML = `<b>${message.myName}</b>: ${message.myChoice}번 선택<br><b>${message.opponentName}</b>: ${message.opponentChoice}번 선택`;
                choice1Btn.disabled = true;
                choice2Btn.disabled = true;

                // 두 플레이어 중 한 명만 다음 질문을 요청하도록 수정
                // (이름을 비교하여 사전순으로 앞서는 사람이 요청)
                if (message.myName < message.opponentName) {
                    setTimeout(() => {
                        console.log("다음 질문을 요청합니다.");
                        socket.send(JSON.stringify({ type: "NEXT_QUESTION", code: codeInput.value }));
                    }, 3000);
                }
            }
            else if (message.type === "GAME_OVER") {
                // AI 분석 결과 표시
                gameScreen.innerHTML = `<h2>💖 최종 결과 💖</h2>
                                        <p style="font-size: 22px;">${message.resultText}</p>
                                        <p style="font-size: 28px; color: #ff69b4; font-weight: bold;">${message.aiMessage}</p>`;
            }
        };
    }

    // 6. 게임 모드 선택 버튼 기능 (신규)
    coupleTestBtn.addEventListener("click", () => {
        selectGameMode("couple");
    });

    friendTestBtn.addEventListener("click", () => {
        selectGameMode("friend");
    });

    function selectGameMode(mode) {
        gameModeScreen.style.display = "none";
        gameScreen.style.display = "block";
        socket.send(JSON.stringify({ type: "CHOOSE_MODE", code: codeInput.value, mode: mode }));
    }

    // 7. 선택지 버튼에 클릭 기능 추가
    choice1Btn.addEventListener("click", () => {
        sendAnswer(1);
    });

    choice2Btn.addEventListener("click", () => {
        sendAnswer(2);
    });

    function sendAnswer(choiceNumber) {
        socket.send(JSON.stringify({
            type: "ANSWER",
            code: codeInput.value,
            choice: choiceNumber
        }));
        resultP.textContent = `당신은 ${choiceNumber}번을 선택했습니다. 상대방의 선택을 기다리는 중...`;
    }
});