package com.e2ee.client;

import com.e2ee.crypto.EncryptedPayload;
import com.e2ee.crypto.EcdhUtil;
import com.e2ee.protocol.ChatMessage;
import com.e2ee.protocol.JsonUtil;
import com.e2ee.protocol.MessageType;
import com.e2ee.session.E2eeSession;

import javax.crypto.SecretKey;
import java.net.Socket;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import static com.e2ee.protocol.JsonUtil.toJson;

/**
 * =========================
 *  ClientMain (완성본)
 * =========================
 * - 서버 인증 (회원가입/로그인)
 * - 클라이언트 ↔ 클라이언트 ECDH-X25519 키교환
 * - AES-GCM 암호화/복호화
 * - 상대별 세션 관리
 * - /key 로 상대를 지정하고 대화
 */
public class ClientMain {

    // ===== 클라이언트 고유 키(고정) =====
    private static KeyPair myKeyPair;
    private static PrivateKey myPrivateKey;
    private static PublicKey myPublicKey;

    // 내 채팅 ID (ex. "grag#0001")
    private static String myTag;

    // 현재 대화 상대 ("/key 상대" 입력 후 지정됨)
    private static String currentTarget = null;

    // 상대별 E2EE 세션 저장소 (상대 userTag → AES 세션)
    private static final Map<String, E2eeSession> sessions = new HashMap<>();


    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);

        // ===========================
        // 0) 서버 연결
        // ===========================
        System.out.println("[NET] 서버에 접속 시도 중...");
        Socket socket = new Socket("127.0.0.1", 9000);
        System.out.println("[NET] 서버에 연결되었습니다!");

        PrintWriter writer = new PrintWriter(
                new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8),
                true  // println() 자동 flush
        );

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );


        // ===========================
        // 1) 회원가입 / 로그인
        // ===========================
        System.out.println("1. 회원가입  2. 로그인");
        int choiceMenu = Integer.parseInt(sc.nextLine());

        String id;
        String pw;

        if (choiceMenu == 1) {
            System.out.println("====== 회원가입 ======");
        } else if (choiceMenu == 2) {
            System.out.println("====== 로그인 ======");
        } else {
            System.out.println("잘못된 메뉴입니다.");
            socket.close();
            return;
        }

        System.out.print("아이디를 입력하세요 : ");
        id = sc.nextLine();

        System.out.print("비밀번호를 입력하세요 : ");
        pw = sc.nextLine();

        // 인증 요청 body
        String authBody =
                "{\"id\":\"" + id + "\"," +
                        "\"password\":\"" + pw + "\"," +
                        "\"publicKey\":\"" + EcdhUtil.encodePublicKey(myPublicKey) + "\"}";
        String now = "2025-11-19T00:00:00";

        MessageType authType =
                (choiceMenu == 1) ? MessageType.AUTH_SIGNUP : MessageType.AUTH_LOGIN;

        ChatMessage authMsg = new ChatMessage(
                authType,
                id,
                "server",
                authBody,
                now
        );

        String authJson = toJson(authMsg);
        System.out.println("[SEND AUTH] " + authJson);
        writer.println(authJson);

        // 🚨 인증 결과 1회만 직접 읽음
        String authLine = reader.readLine();
        ChatMessage authRes = JsonUtil.fromJson(authLine, ChatMessage.class);

        System.out.println("[AUTH_RESULT] " + authRes.getBody());

        if (!(authRes.getBody().startsWith("LOGIN_OK") ||
                authRes.getBody().startsWith("SIGNUP_OK"))) {
            System.out.println("인증 실패. 종료합니다.");
            socket.close();
            return;
        }

        System.out.println("[INFO] 인증 성공! 이제 키 교환과 채팅을 시작합니다.");
        System.out.println("[DEBUG] 현재 로그인 사용자 ID: " + id);


        // ===========================
        // 2) ECDH 키쌍 생성
        // ===========================
        myTag = id + "#0001";
        System.out.println("[INFO] 이 클라이언트의 채팅 ID는 " + myTag);

        System.out.println("[INFO] ECDH 키쌍 생성 중...");
        myKeyPair = EcdhUtil.generateKeyPair();
        myPrivateKey = myKeyPair.getPrivate();
        myPublicKey  = myKeyPair.getPublic();
        System.out.println("[OK] 키쌍 생성 완료!");


        // ===========================
        // 3) 서버 수신 전담 쓰레드
        // ===========================
        Thread recvThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {

                    ChatMessage msg = JsonUtil.fromJson(line, ChatMessage.class);

                    // ========== 시스템 메시지 ==========
                    if (msg.getType() == MessageType.SYSTEM) {
                        System.out.println("[SERVER] " + msg.getBody());
                    }

                    // ========== KEY_RES (상대 공개키 수신) ==========
                    else if (msg.getType() == MessageType.KEY_RES) {

                        PublicKey otherPub = EcdhUtil.decodePublicKey(msg.getBody());

                        SecretKey aesKey = EcdhUtil.deriveAesKeyFromSharedSecret(
                                myPrivateKey, otherPub
                        );

                        E2eeSession session = E2eeSession.create(myKeyPair, otherPub);

                        sessions.put(msg.getSender(), session);
                        System.out.println("[INFO] " + msg.getSender() + " 과의 E2EE 세션 생성 완료!");
                    }

                    // ========== KEY_REQ (상대가 키 요청함) ==========
                    else if (msg.getType() == MessageType.KEY_REQ) {

                        PublicKey otherPub = EcdhUtil.decodePublicKey(msg.getBody());

                        SecretKey aesKey = EcdhUtil.deriveAesKeyFromSharedSecret(
                                myPrivateKey, otherPub
                        );

                        E2eeSession session = E2eeSession.create(myKeyPair, otherPub);

                        sessions.put(msg.getSender(), session);
                        System.out.println("[INFO] KEY_REQ: " + msg.getSender() + "과 세션 저장됨");

                        // ★ 내가 상대에게 KEY_RES 응답 보내기
                        String myPubBase64 = EcdhUtil.encodePublicKey(myPublicKey);

                        ChatMessage res = ChatMessage.keyResponse(
                                myTag,                       // sender
                                msg.getSender(),             // receiver
                                myPublicKey,                 // PublicKey 객체
                                "2025-11-19T00:00:00"        // timestamp
                        );

                        writer.println(toJson(res));
                    }

                    // ========== CHAT 메시지 ==========
                    else if (msg.getType() == MessageType.CHAT) {

                        E2eeSession session = sessions.get(msg.getSender());

                        if (session == null) {
                            // 아직 세션 없음 → 암호문이든 평문이든 그대로 보여줌
                            System.out.println("[CHAT:RAW] " + msg.getSender()
                                    + " : " + msg.getBody());
                        } else {
                            // AES-GCM 복호화
                            EncryptedPayload payload =
                                    EncryptedPayload.fromWireString(msg.getBody());

                            String plain = session.decrypt(payload);

                            System.out.println("[CHAT] " + msg.getSender() + " : " + plain);
                        }
                    }

                    // 기타 타입
                    else {
                        System.out.println("[RAW] " + line);
                    }
                }

            } catch (Exception e) {
                System.out.println("[RECV] 서버와의 연결이 끊어졌습니다.");
            }

        }, "recv-thread");

        recvThread.setDaemon(true);
        recvThread.start();


        // ===========================
        // 4) 메인 입력 루프
        // ===========================
        while (true) {

            System.out.print("> ");
            String line = sc.nextLine();

            // 종료
            if (line.equalsIgnoreCase("/quit")) {
                System.out.println("클라이언트를 종료합니다.");
                break;
            }

            // ========== /key target ==========
            if (line.startsWith("/key ")) {

                String target = line.substring(5).trim();
                currentTarget = target;

                ChatMessage keyReq = ChatMessage.keyRequest(
                        myTag,
                        target,
                        myPublicKey,      // 내 공개키
                        "2025-11-19T00:00:00"
                );

                String json = toJson(keyReq);
                System.out.println("[SEND] " + json);
                writer.println(json);
                continue;
            }

            // ========== 일반 채팅 ==========
            if (currentTarget == null) {
                System.out.println("[WARN] 먼저 '/key 상대아이디' 로 세션을 생성하세요.");
                continue;
            }

            String target = currentTarget;
            String timestamp = "2025-11-21T00:00:00";

            E2eeSession session = sessions.get(target);

            ChatMessage chat;

            if (session == null) {
                // 세션 없음 → 평문
                chat = new ChatMessage(
                        MessageType.CHAT,
                        myTag,
                        target,
                        line,
                        timestamp
                );
                System.out.println("[WARN] 세션 없음. 평문 전송합니다.");

            } else {
                // 세션 있음 → 암호문
                chat = ChatMessage.encryptedChat(
                        myTag,
                        target,
                        line,
                        session,
                        timestamp
                );
                System.out.println("[INFO] 암호화 후 전송.");
            }

            String json = toJson(chat);
            System.out.println("[SEND] " + json);
            writer.println(json);
        }

        // 연결 종료
        socket.close();
        System.out.println("[NET] 연결 종료.");
    }
}
