package com.e2ee.client;

import com.e2ee.crypto.EncryptedPayload;
import com.e2ee.crypto.EcdhUtil;
import com.e2ee.crypto.AesGcmUtil; // (지금은 안 쓰지만, 나중에 디버깅에 쓸 수 있어서 일단 둠)
import com.e2ee.protocol.ChatMessage;
import com.e2ee.protocol.JsonUtil;
import com.e2ee.protocol.MessageType;
import com.e2ee.session.E2eeSession;

import java.net.Socket;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import java.security.KeyPair;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static com.e2ee.protocol.JsonUtil.toJson;

public class ClientMain {

    // 🔑 이 클라이언트가 ECDH용으로 사용할 개인키/공개키 쌍 (프로그램 동안 고정)
    private static KeyPair myKeyPair;

    // 지금 내가 대화하려는 상대 (예: "foo#0001")
    // 기본값은 null → /key 치기 전에는 세션 없음
    private static String currentTarget = null;

    // 🔐 상대와의 E2EE 세션을 저장 (지금은 "ALL" 방 하나만 사용)
    private static final Map<String, E2eeSession> sessions = new HashMap<>();

    // 방 이름 상수 (나중에 1:1 채팅으로 확장하면 key를 userTag로 바꾸면 됨)
    private static final String ROOM_ALL = "ALL";

    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);

        // ==== 0) 서버 TCP 연결 ====
        System.out.println("[NET] 서버에 접속 시도 중...");
        Socket socket = new Socket("127.0.0.1", 9000);
        System.out.println("[NET] 서버에 연결되었습니다!");

        // 서버로 데이터를 보낼 출력 스트림(Writer)
        PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                true   // println() 할 때마다 자동 flush
        );

        // 서버에서 오는 데이터를 읽을 입력 스트림(Reader)
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );

        // ==== 1) 인증 단계 (회원가입 / 로그인) ====
        System.out.println("1. 회원가입  2. 로그인");
        int choiceMenu = Integer.parseInt(sc.nextLine());

        String id;
        String pw;

        if (choiceMenu == 1) {
            System.out.println("====== 회원가입 ======");
        } else if (choiceMenu == 2) {
            System.out.println("====== 로그인 ======");
        } else {
            System.out.println("잘못된 메뉴입니다. 프로그램을 종료합니다.");
            socket.close();
            return;
        }

        // 아이디/비밀번호 입력
        System.out.print("아이디를 입력하세요 : ");
        id = sc.nextLine();

        System.out.print("비밀번호를 입력하세요 : ");
        pw = sc.nextLine();

        // 인증 요청용 body JSON 만들기 (아주 단순한 형태)
        // 예: {"id":"grag","password":"1234"}
        String authBody = "{\"id\":\"" + id + "\",\"password\":\"" + pw + "\"}";

        // 타임스탬프는 일단 임시 문자열 (나중에 LocalDateTime으로 바꿀 수 있음)
        String now = "2025-11-19T00:00:00";

        // 어떤 타입으로 보낼지 결정 (회원가입 or 로그인)
        MessageType authType = (choiceMenu == 1)
                ? MessageType.AUTH_SIGNUP
                : MessageType.AUTH_LOGIN;

        // AUTH_* 요청 메시지 만들기
        ChatMessage authMsg = new ChatMessage(
                authType,
                id,          // sender: 지금은 id 자체를 사용
                "server",    // receiver: 서버에게 보내므로 "server"
                authBody,
                now
        );

        // JSON으로 변환해서 서버에 전송
        String authJson = toJson(authMsg);
        System.out.println("[SEND AUTH] " + authJson);
        writer.println(authJson);

        // ★ 여기서 메인 스레드가 한 줄만 직접 읽어서 AUTH_RESULT를 확인한다
        String authLine = reader.readLine();
        if (authLine == null) {
            System.out.println("[ERROR] 서버와의 연결이 끊어졌습니다. (AUTH 단계)");
            socket.close();
            return;
        }

        ChatMessage authRes = JsonUtil.fromJson(authLine, ChatMessage.class);

        if (authRes.getType() != MessageType.AUTH_RESULT) {
            System.out.println("[ERROR] 예상과 다른 메시지를 받았습니다: " + authRes.getType());
            socket.close();
            return;
        }

        String result = authRes.getBody();
        System.out.println("[AUTH_RESULT] " + result);

        // 결과 문자열이 SIGNUP_OK 또는 LOGIN_OK로 시작하는지 확인
        if (!(result.startsWith("SIGNUP_OK") || result.startsWith("LOGIN_OK"))) {
            System.out.println("인증 실패. 프로그램을 종료합니다.");
            socket.close();
            return;
        }

        // 여기까지 왔으면 "인증 성공"
        System.out.println("[INFO] 인증 성공! 이제 키 교환과 채팅을 시작합니다.");
        System.out.println("\n[DEBUG] 현재 로그인 사용자 ID: " + id);

        // ==== 2) E2EE 클라이언트 공통 준비 ====

        // (1) 채팅에서 사용할 고정 식별자 만들기 (예: grag#0001)
        String userTag = id + "#0001";
        System.out.println("[INFO] 이 클라이언트의 채팅 ID는 " + userTag + " 입니다.");

        // (2) ECDH 키쌍 생성 (이제부터 이 키쌍으로 세션을 만든다)
        System.out.println("[INFO] ECDH 키쌍 생성 중...");
        myKeyPair = EcdhUtil.generateKeyPair();
        System.out.println("[OK] 키쌍 생성 완료! 이제 이 키로 세션을 만들 수 있습니다.");

        // ==== 3) 서버 수신 전담 쓰레드 시작 ====
        Thread recvThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {

                    // 1) JSON → ChatMessage 객체로 변환
                    ChatMessage msg = JsonUtil.fromJson(line, ChatMessage.class);

                    // 2) 타입에 따라 분기
                    if (msg.getType() == MessageType.SYSTEM) {

                        // 일반 시스템 메시지 (ex. 서버 알림)
                        System.out.println("[SERVER] " + msg.getBody());

                    } else if (msg.getType() == MessageType.AUTH_RESULT) {

                        // (현재는 인증을 메인 스레드에서만 처리하므로,
                        //  여기에 AUTH_RESULT가 오지는 않을 예정이지만,
                        //  혹시 나중에 재인증 기능 등을 넣을 때를 대비해 그대로 출력)
                        System.out.println("[AUTH_RESULT] " + msg.getBody());

                    } else if (msg.getType() == MessageType.KEY_RES) {

                        // 서버(또는 상대)로부터 공개키 응답 받음
                        System.out.println("[KEY_RES] from=" + msg.getSender()
                                + " body=" + msg.getBody());

                        // 1) 서버 공개키 복원
                        java.security.PublicKey serverPub =
                                EcdhUtil.decodePublicKey(msg.getBody());

                        // 2) E2EE 세션 생성 (공유비밀 → AES키까지 내부에서 해줌)
                        E2eeSession session = E2eeSession.create(myKeyPair, serverPub);

                        // ★ ALL이 아니라, currentTarget 기준으로 저장
                        if (currentTarget != null) {
                            sessions.put(currentTarget, session);
                            System.out.println("[INFO] " + currentTarget + " 과의 E2EE 세션 생성 완료!");
                        } else {
                            System.out.println("[WARN] currentTarget 이 없어 세션을 저장하지 못했습니다.");
                        }

                    } else if (msg.getType() == MessageType.CHAT) {

                        // CHAT 메시지 도착: 암호문일 수도, 평문일 수도 있다.
                        String target = msg.getReceiver(); // 이 메시지가 향하는 대상
                        E2eeSession session = sessions.get(target);

                        if (session == null) {
                            // 아직 세션이 없으면 복호화를 못 하므로 RAW로 보여준다.
                            System.out.println("[CHAT:RAW] " + msg.getSender()
                                    + " -> " + msg.getReceiver()
                                    + " : " + msg.getBody());
                        } else {
                            // 세션이 있으면: body 문자열을 EncryptedPayload로 복원 후 복호화
                            EncryptedPayload payload =
                                    EncryptedPayload.fromWireString(msg.getBody());

                            String plain = session.decrypt(payload);

                            System.out.println("[CHAT] " + msg.getSender()
                                    + " -> " + msg.getReceiver()
                                    + " : " + plain);
                        }
                    } else {
                        // 정의되지 않은 타입은 RAW로 출력
                        System.out.println("[FROM SERVER RAW] " + line);
                    }
                }
            } catch (Exception e) {
                System.out.println("[RECV] 서버와의 연결이 끊어졌습니다.");
            }
        }, "recv-thread");

        recvThread.setDaemon(true);
        recvThread.start();

        // ==== 4) 콘솔 명령 루프 (/key, /history, 일반 채팅) ====
        while (true) {
            System.out.print("> ");
            String line = sc.nextLine();

            if (line.equalsIgnoreCase("/quit")) {
                System.out.println("클라이언트를 종료합니다.");
                break;
            }

            if (line.startsWith("/key ")) {
                // 예: /key ALL  또는 /key 상대아이디#0001
                String target = line.substring(5).trim();

                // ★ 지금부터 대화할 상대를 기록해 둔다
                currentTarget = target;

                ChatMessage keyReq = ChatMessage.keyRequest(
                        userTag,                  // sender: 나 (id#0001 형태)
                        target,                   // receiver: 상대 id#xxxx (서버가 해석)
                        myKeyPair.getPublic(),    // 내 공개키
                        "2025-11-19T00:00:00"     // 임시 timestamp
                );

                String json = toJson(keyReq);
                System.out.println("[SEND] " + json);
                writer.println(json);   // 실제 서버로 전송

            } else if (line.startsWith("/history ")) {
                // 예: /history ALL, /history foo#0001
                String target = line.substring(9).trim();
                System.out.println("[DEBUG] /history 명령 입력됨. 대상: " + target);
                // TODO: 나중에 HISTORY_REQ 메시지 프로토콜 정의 후 구현

            } else {
                // 일반 채팅 메시지

                if (currentTarget == null) {
                    System.out.println("[WARN] 아직 /key 로 상대를 지정하지 않았습니다. 먼저 /key 상대아이디 를 실행하세요.");
                    continue;
                }

                String target = currentTarget;
                String timestamp = "2025-11-21T00:00:00"; // 임시 시간

                E2eeSession session = sessions.get(target);
                ChatMessage chat;

                if (session == null) {
                    // 세션이 없으면: 아직 /key를 안 했다는 뜻 → 평문으로 전송(임시)
                    chat = new ChatMessage(
                            MessageType.CHAT,
                            userTag,
                            target,
                            line,      // body = 평문
                            timestamp
                    );
                    System.out.println("[WARN] " + target + " 과의 세션이 없어, 일단 평문으로 보냅니다(임시).");
                } else {
                    // 세션이 있으면: AES-GCM으로 암호화된 CHAT 전송
                    chat = ChatMessage.encryptedChat(
                            userTag,
                            target,
                            line,      // 평문
                            session,   // 내부에서 encrypt() 호출
                            timestamp
                    );
                    System.out.println("[INFO] " + target + " 과의 E2EE 세션 사용해서 암호화했습니다.");
                }

                String json = toJson(chat);
                System.out.println("[SEND] " + json);
                writer.println(json);
            }
        }

        // 루프를 빠져나오면 소켓 정리
        socket.close();
        System.out.println("[NET] 연결을 종료했습니다.");
    }
}
