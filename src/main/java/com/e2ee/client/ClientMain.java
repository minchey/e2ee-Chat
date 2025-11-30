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
 * ===========================
 * ClientMain (최종 리팩토링 버전)
 * ===========================
 * - 회원가입/로그인
 * - 클라이언트 ↔ 클라이언트 키교환
 * - AES-GCM 암호화/복호화 채팅
 * - 상대별 세션 저장 (Map)
 */
public class ClientMain {

    // ===== 클라이언트 고유 키 (ECDH 용) =====
    private static KeyPair myKeyPair;
    private static PrivateKey myPrivateKey;
    private static PublicKey myPublicKey;

    // 내 채팅 ID (ex. grag#0001)
    private static String myTag;

    // 현재 대화 상대 (/key로 설정)
    private static String currentTarget = null;

    // 상대 userTag → 세션
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
                true
        );

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );


        // ===========================
        // 1) 회원가입 / 로그인
        // ===========================
        System.out.println("1. 회원가입  2. 로그인");
        int choiceMenu = Integer.parseInt(sc.nextLine());

        String id, pw;

        if (choiceMenu == 1) System.out.println("====== 회원가입 ======");
        else if (choiceMenu == 2) System.out.println("====== 로그인 ======");
        else {
            System.out.println("잘못된 메뉴입니다.");
            return;
        }

        // ---- 아이디/비번 먼저 받기 ----
        System.out.print("아이디를 입력하세요 : ");
        id = sc.nextLine();

        System.out.print("비밀번호를 입력하세요 : ");
        pw = sc.nextLine();


        // ===========================
        // 🔥 회원가입/로그인 직전에 키쌍 생성
        // ===========================
        // 이유: 아이디/비번을 입력받기 전에 키쌍 생성하면 누구의 키인지 모름
        System.out.println("[INFO] 사용자용 ECDH 키쌍 생성 중...");
        myKeyPair = EcdhUtil.generateKeyPair();
        myPrivateKey = myKeyPair.getPrivate();
        myPublicKey = myKeyPair.getPublic();
        System.out.println("[OK] 키쌍 생성 완료!");

        // 이 클라이언트의 채팅용 태그
        myTag = id + "#0001";
        System.out.println("[INFO] 채팅 ID = " + myTag);


        // ===========================
        // 1-1) 인증 요청 JSON 만들기
        // ===========================
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

        System.out.println("[SEND AUTH] " + toJson(authMsg));
        writer.println(toJson(authMsg));

        // 서버의 AUTH_RESULT 1회만 메인스레드에서 직접 받기
        String authLine = reader.readLine();
        ChatMessage authRes = JsonUtil.fromJson(authLine, ChatMessage.class);

        System.out.println("[AUTH_RESULT] " + authRes.getBody());

        if (!(authRes.getBody().startsWith("LOGIN_OK") ||
                authRes.getBody().startsWith("SIGNUP_OK"))) {
            System.out.println("인증 실패. 종료.");
            socket.close();
            return;
        }

        System.out.println("[INFO] 인증 성공!");


        // ===========================
        // 2) 서버 수신 쓰레드
        // ===========================
        Thread recvThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {

                    ChatMessage msg = JsonUtil.fromJson(line, ChatMessage.class);

                    // ---------- SYSTEM ----------
                    if (msg.getType() == MessageType.SYSTEM) {
                        System.out.println("[SERVER] " + msg.getBody());
                    }

                    // ---------- KEY_RES ----------
                    else if (msg.getType() == MessageType.KEY_RES) {

                        PublicKey otherPub = EcdhUtil.decodePublicKey(msg.getBody());

                        E2eeSession session = E2eeSession.create(myKeyPair, otherPub);

                        sessions.put(msg.getSender(), session);
                        System.out.println("[INFO] " + msg.getSender() + " 과 세션 생성 완료!");
                    }

                    // ---------- KEY_REQ ----------
                    else if (msg.getType() == MessageType.KEY_REQ) {

                        PublicKey otherPub = EcdhUtil.decodePublicKey(msg.getBody());

                        E2eeSession session = E2eeSession.create(myKeyPair, otherPub);
                        sessions.put(msg.getSender(), session);

                        System.out.println("[INFO] KEY_REQ 수신 → " + msg.getSender() + "과 세션 저장됨");

                        // KEY_RES 응답 보내기
                        ChatMessage res = ChatMessage.keyResponse(
                                myTag,
                                msg.getSender(),
                                myPublicKey,
                                "2025-11-19T00:00:00"
                        );

                        writer.println(toJson(res));
                    }

                    // ---------- CHAT ----------
                    else if (msg.getType() == MessageType.CHAT) {

                        E2eeSession session = sessions.get(msg.getSender());

                        if (session == null) {
                            System.out.println("[CHAT:RAW] " + msg.getSender() + " : " + msg.getBody());
                        } else {
                            EncryptedPayload payload =
                                    EncryptedPayload.fromWireString(msg.getBody());
                            String plain = session.decrypt(payload);
                            System.out.println("[CHAT] " + msg.getSender() + " : " + plain);
                        }
                    }

                    // ---------- 기타 ----------
                    else {
                        System.out.println("[RAW] " + line);
                    }
                }

            } catch (Exception e) {
                System.out.println("[RECV] 서버 연결 종료");
            }
        });

        recvThread.setDaemon(true);
        recvThread.start();


        // ===========================
        // 3) 콘솔 입력 루프
        // ===========================
        while (true) {

            System.out.print("> ");
            String line = sc.nextLine();

            if (line.equals("/quit")) break;

            // ----- 키교환 -----
            if (line.startsWith("/key ")) {
                String target = line.substring(5).trim();
                currentTarget = target;

                ChatMessage req = ChatMessage.keyRequest(
                        myTag,
                        target,
                        myPublicKey,
                        "2025-11-19T00:00:00"
                );

                System.out.println("[SEND] " + toJson(req));
                writer.println(toJson(req));
                continue;
            }

            // ----- 일반 채팅 -----
            if (currentTarget == null) {
                System.out.println("[WARN] 먼저 /key 상대 를 입력하세요.");
                continue;
            }

            E2eeSession session = sessions.get(currentTarget);
            String timestamp = "2025-11-21T00:00:00";

            ChatMessage chat;

            if (session == null) {
                // 평문
                chat = new ChatMessage(
                        MessageType.CHAT,
                        myTag,
                        currentTarget,
                        line,
                        timestamp
                );
                System.out.println("[WARN] 세션 없음 → 평문 전송");
            } else {
                // 암호문
                chat = ChatMessage.encryptedChat(
                        myTag,
                        currentTarget,
                        line,
                        session,
                        timestamp
                );
                System.out.println("[INFO] 암호화 전송");
            }

            System.out.println("[SEND] " + toJson(chat));
            writer.println(toJson(chat));
        }

        socket.close();
        System.out.println("[NET] 연결 종료");
    }
}
