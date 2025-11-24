package com.e2ee.client;

import com.e2ee.crypto.EncryptedPayload;
import com.e2ee.crypto.EcdhUtil;
import com.e2ee.crypto.AesGcmUtil;
import com.e2ee.protocol.ChatMessage;
import com.e2ee.protocol.JsonUtil;
import com.e2ee.protocol.MessageType;
import com.e2ee.session.E2eeSession;

import java.net.Socket;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import java.security.KeyPair;
import java.util.Map;
import java.util.Scanner;

import static com.e2ee.protocol.JsonUtil.toJson;

public class ClientMain {
    private static KeyPair myKeyPair;
    //private static Map<String, E2eeSession> sessions = new java.util.HashMap<>();
    private static final String ROOM_ALL = "ALL";

    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);

        myKeyPair = EcdhUtil.generateKeyPair();


        // ==== 0) 서버 먼저 연결 ====
        System.out.println("[NET] 서버에 접속 시도 중...");
        Socket socket = new Socket("127.0.0.1", 9000);
        System.out.println("[NET] 서버에 연결되었습니다!");

        OutputStream out = socket.getOutputStream();
        PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(out, StandardCharsets.UTF_8),
                true   // println() 할 때마다 자동 flush
        );

        // ★ 서버에서 오는 메시지를 읽을 reader + 쓰레드
        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );
        // ★ 상대별 세션을 저장할 Map (상대 userTag -> E2eeSession)
        Map<String, E2eeSession> sessions = new java.util.HashMap<>();

        Thread recvThread = new Thread(() -> {

            try {
                String line;

                while ((line = reader.readLine()) != null) {

                    // 1) JSON → ChatMessage 객체로 변환
                    ChatMessage msg = JsonUtil.fromJson(line, ChatMessage.class);

                    // 2) 타입에 따라 다르게 출력
                    if (msg.getType() == MessageType.SYSTEM) {
                        System.out.println("[SERVER] " + msg.getBody());
                    } else if (msg.getType() == MessageType.KEY_RES) {
                        System.out.println("[KEY_RES] from=" + msg.getSender()
                                + " body=" + msg.getBody());
                        // 1) 서버 공개키 복원
                        java.security.PublicKey serverPub =
                                EcdhUtil.decodePublicKey(msg.getBody());

                        // 2) E2EE 세션 생성 (공유비밀 → AES키까지 내부에서 해줌)
                        E2eeSession session = E2eeSession.create(myKeyPair, serverPub);

                        // 3) 세션을 Map에 저장
                        sessions.put(ROOM_ALL, session);   // 지금은 ALL 방과의 세션으로 저장

                        System.out.println("[INFO] 서버와 E2EE 세션 생성 완료! 이제부터 ALL은 암호화해서 보냄.");                    } else if (msg.getType() == MessageType.CHAT) {
                        System.out.println("[CHAT] " + msg.getSender()
                                + " -> " + msg.getReceiver()
                                + " : " + msg.getBody());
                    } else if (msg.getType() == MessageType.CHAT) {

                        // 1) 세션 찾기 (지금은 ALL 방만 사용)
                        E2eeSession session = sessions.get("ALL");

                        if (session == null) {
                            // 아직 세션 없으면 그냥 원문(암호문)을 보여주자
                            System.out.println("[CHAT:RAW] " + msg.getSender()
                                    + " : " + msg.getBody());
                        } else {
                            // 2) body(Base64 문자열) → EncryptedPayload
                            EncryptedPayload payload =
                                    EncryptedPayload.fromWireString(msg.getBody());

                            // 3) 세션으로 복호화
                            String plain = session.decrypt(payload);

                            // 4) 사람 눈에 보이는 평문 출력
                            System.out.println("[CHAT] " + msg.getSender()
                                    + " : " + plain);
                        }
                    }else {
                        System.out.println("[FROM SERVER RAW] " + line);
                    }
                }
            } catch (Exception e) {
                System.out.println("[RECV] 서버와의 연결이 끊어졌습니다.");
            }
        }, "recv-thread");


        recvThread.setDaemon(true);
        recvThread.start();
        //여기까지


        System.out.println("1. 회원가입 2. 로그인");

        int choiceMenu = sc.nextInt();
        sc.nextLine();

        String id = null;
        String pw = null;

        if (choiceMenu == 1) {

            System.out.println("====== 회원가입 ======");
            System.out.println("아이디를 입력하세요 : ");
            id = sc.nextLine();

            System.out.println("비밀번호를 입력하세요 : ");
            pw = sc.nextLine();

        } else if (choiceMenu == 2) {
            System.out.println("==== 로그인 ====");
            System.out.println("아이디를 입력하세요 : ");
            id = sc.nextLine();
            System.out.println("비밀번호를 입력하세요 : ");
            pw = sc.nextLine();
        } else {
            System.out.println("잘못된 메뉴입니다. 프로그램을 종료합니다.");
            return;
        }

        // 여기까지 오면 id, pw 입력은 끝난 상태
        System.out.println("\n[DEBUG] 현재 로그인 사용자 ID: " + id);


        // ==== 여기부터는 E2EE 클라이언트 공통 준비 ====

        // (1) 채팅에서 사용할 "고정 식별자" 만들기 (예: foo#0001)
        //     나중에는 서버가 #숫자 부분을 내려줄 예정이라고 가정하고,
        //     지금은 임시값으로 "#0001"을 사용.
        String userTag = id + "#0001";
        System.out.println("[INFO] 이 클라이언트의 채팅 ID는 " + userTag + " 입니다.");

        // (2) ECDH 키쌍 생성
        System.out.println("[INFO] ECDH 키쌍 생성 중...");
        KeyPair myKeyPair = EcdhUtil.generateKeyPair();
        System.out.println("[OK] 키쌍 생성 완료! 이제 이 키로 세션을 만들 수 있습니다.");




        // (3) 이제부터는 콘솔 명령 루프
        while (true) {
            System.out.println("> ");
            String line = sc.nextLine();

            if (line.equalsIgnoreCase("/quit")) {
                System.out.println("클라이언트를 종료합니다.");
                break;
            }
            if (line.startsWith("/key ")) {
                String target = line.substring(5).trim(); // 예: /key foo#0001
                // 1) KEY_REQ 메시지 객체 만들기
                ChatMessage keyReq = ChatMessage.keyRequest(
                        userTag,                  // sender: 나 (id#0001 형태)
                        target,                   // receiver: 상대 id#xxxx
                        myKeyPair.getPublic(),    // 내 공개키
                        "2025-11-19T00:00:00"     // 임시 timestamp (나중에 LocalDateTime로 바꿀 수 있음)
                );
                // 2) JSON 문자열로 변환
                String json = toJson(keyReq);

                // 3) 콘솔에 출력 (나중엔 이걸 서버에 보내게 됨)
                System.out.println("[SEND] " + json);

                // 4) 실제 서버로 전송
                writer.println(json);   // ← 이 줄 추가

            } else if (line.startsWith("/history ")) {
                String target = line.substring(9).trim(); // 예: /history foo#0001
                System.out.println("[DEBUG] /history 명령 입력됨. 대상: " + target);
            } else {
                // 일반 채팅 메시지
                String target = ROOM_ALL; // 일단은 전체방 (나중에 상대 userTag로 바꿀 수 있음)
                String timestamp = "2025-11-21T00:00:00"; // 임시 시간 문자열

                E2eeSession session = sessions.get(target);
                ChatMessage chat;

                if (session == null) {
                    // 아직 target과의 세션이 없으면 "그냥 평문"으로 보냄 (임시)
                    chat = new ChatMessage(
                            MessageType.CHAT,
                            userTag,
                            target,
                            line,      // body = 평문
                            timestamp
                    );
                    System.out.println("[WARN] " + target + " 과의 세션이 없어, 일단 평문으로 보냅니다(임시).");
                } else {
                    // 세션이 있으면 암호화해서 CHAT 메시지 생성
                    chat = ChatMessage.encryptedChat(
                            userTag,
                            target,
                            line,      // 평문
                            session,   // 여기서 내부적으로 encrypt() 호출
                            timestamp
                    );
                    System.out.println("[INFO] " + target + " 과의 E2EE 세션 사용해서 암호화했습니다.");
                }

                String json = toJson(chat);
                System.out.println("[SEND] " + json);

                // 실제 서버로 전송
                writer.println(json);
            }
        }
    }


}
