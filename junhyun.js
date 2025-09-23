// 1. HTML 요소들에게 별명 붙여주기
const infoText = document.querySelector("#info-text");
const createBtn = document.querySelector("#create-btn");
const joinBtn = document.querySelector("#join-btn");
const codeInput = document.querySelector("#code-input");

// 1-1. 웹소켓 변수 미리 만들어두기
let socket;

// 2. '게임방 만들기' 버튼 기능
createBtn.addEventListener("click", () => {
    infoText.textContent = "초대 코드를 만드는 중...";
    fetch("http://localhost:8080/api/game/create")
        .then(response => response.text())
        .then(inviteCode => {
            infoText.textContent = "초대 코드: " + inviteCode;
            infoText.style.color = "blue";
            infoText.style.fontWeight = "bold";
        })
        .catch(error => {
            console.error("Error:", error);
            infoText.textContent = "서버에 연결할 수 없어요. 서버가 켜져 있는지 확인해주세요!";
            infoText.style.color = "red";
        });
});

// 3. '참여하기' 버튼 기능
joinBtn.addEventListener("click", () => {
    const code = codeInput.value;
    if (!code) {
        alert("초대 코드를 입력해주세요!");
        return;
    }

    fetch("http://localhost:8080/api/game/join/" + code)
        .then(response => response.text())
        .then(message => {
            infoText.textContent = message;
            if (message.includes("성공")) {
                infoText.style.color = "green";
                // --- 이 부분이 추가됐어요! ---
                // 게임방 참여에 성공하면, 웹소켓 연결 시작
                connectWebSocket();
                // -----------------------------
            } else {
                infoText.style.color = "red";
            }
        })
        .catch(error => {
            console.error("Error:", error);
            infoText.textContent = "서버 연결에 실패했습니다.";
            infoText.style.color = "red";
        });
});


// 4. 서버와 워크토키(웹소켓) 연결을 시도하는 새로운 함수
// junhyun.js 파일의 connectWebSocket 함수

function connectWebSocket() {
    socket = new WebSocket("ws://localhost:8080/ws/game");

    // 워크토키 채널이 성공적으로 열렸을 때 실행할 일
    socket.onopen = function() {
        console.log("워크토키 채널 연결 성공!");
        
        // --- 이 부분이 추가됐어요! ---
        // 서버에 "저 이 방에 참여할래요!" 라는 JOIN 메시지를 보냄
        const joinMessage = {
            type: "JOIN",
            code: codeInput.value // 입력창에 있던 초대 코드
        };
        socket.send(JSON.stringify(joinMessage)); // JSON 형태로 변환해서 전송
        // -----------------------------

        infoText.textContent = "상대방을 기다리는 중입니다...";
    };

    // 워크토키로 서버로부터 메시지가 도착했을 때 실행할 일
    socket.onmessage = function(event) {
        console.log("서버로부터 메시지 수신:", event.data);
        const message = JSON.parse(event.data); // 받은 JSON 메시지를 분석

        // --- 이 부분이 추가됐어요! ---
        // 서버로부터 "START" 신호를 받으면,
        if (message.type === "START") {
            infoText.textContent = "게임 시작!";
            // 실제 게임 화면으로 넘어가는 로직을 여기에 추가할 거예요.
        }
        // -----------------------------
    };

    // 워크토키 연결 중 에러가 발생했을 때 실행할 일
    socket.onerror = function(error) {
        console.error("웹소켓 에러:", error);
    };
}