// junhyun.js 파일 전체 내용

document.addEventListener("DOMContentLoaded", () => {
    // 1. HTML 요소들에게 별명 붙여주기
    const lobbyScreen = document.querySelector("#lobby-screen");
    const infoText = document.querySelector("#info-text");
    const createBtn = document.querySelector("#create-btn");
    const joinBtn = document.querySelector("#join-btn");
    const codeInput = document.querySelector("#code-input");

    const gameScreen = document.querySelector("#game-screen");
    const questionP = document.querySelector("#question-p");
    const choice1Btn = document.querySelector("#choice1-btn");
    const choice2Btn = document.querySelector("#choice2-btn");
    const resultP = document.querySelector("#result-p");

    let socket;

    // 2. '게임방 만들기' 버튼 기능
    createBtn.addEventListener("click", () => {
        fetch("http://localhost:8080/api/game/create")
            .then(response => response.text())
            .then(inviteCode => { infoText.textContent = "초대 코드: " + inviteCode; });
    });

    // 3. '참여하기' 버튼 기능
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

    // 4. 웹소켓 연결 함수
    function connectWebSocket() {
        socket = new WebSocket("ws://localhost:8080/ws/game");

        socket.onopen = function() {
            socket.send(JSON.stringify({ type: "JOIN", code: codeInput.value }));
            infoText.textContent = "상대방을 기다리는 중입니다...";
        };

        socket.onmessage = function(event) {
            const message = JSON.parse(event.data);
            
            // 핵심 로직: START 신호 받으면 화면 전환
            if (message.type === "START") {
                lobbyScreen.style.display = "none"; // 로비 화면 숨기기
                gameScreen.style.display = "block"; // 게임 화면 보여주기
            } 
            else if (message.type === "QUESTION") {
                questionP.textContent = message.question;
                choice1Btn.textContent = message.choice1;
                choice2Btn.textContent = message.choice2;
                resultP.textContent = "";
                choice1Btn.disabled = false;
                choice2Btn.disabled = false;
            }
            else if (message.type === "RESULT") {
                resultP.textContent = `당신: ${message.myChoice}번, 상대방: ${message.opponentChoice}번`;
                choice1Btn.disabled = true;
                choice2Btn.disabled = true;

                setTimeout(() => {
                    socket.send(JSON.stringify({
                        type: "NEXT_QUESTION",
                        code: codeInput.value
                    }));
                }, 3000);
            }
            else if (message.type === "GAME_OVER") {
                gameScreen.innerHTML = `<h2>게임 종료!</h2><p>${message.text}</p>`;
            }
        };
    }

    // 5. 선택지 버튼에 클릭 기능 추가
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
        resultP.textContent = "당신은 " + choiceNumber + "번을 선택했습니다. 상대방의 선택을 기다리는 중...";
    }
});