package com.e2ee.client;

import com.e2ee.crypto.EncryptedPayload;
import com.e2ee.crypto.EcdhUtil;
import com.e2ee.protocol.ChatMessage;
import com.e2ee.protocol.JsonUtil;
import com.e2ee.protocol.MessageType;
import com.e2ee.session.E2eeSession;
import com.e2ee.client.store.KeyVault;   // 🔥 KeyVault 임포트

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
 *  ClientMain (KeyVault 적용 버전)
 * =========================
 */
public class ClientMain {

    // ===== 클라이언트 로컬 저장 키 =====
    private static KeyPair myKeyPair;
    private static PrivateKey myPrivateKey;
    private static PublicKey myPublicKey;

    // 내 채팅 태그 (예: minchey#0001)
    private static String myTag;

    // 현재 대화 상대
    private static String currentTarget = null;

    // 상대 userTag → 세션 객체
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


        // ===========================
        // 🔥 2) 로컬 KeyVault에서 키 불러오기 or 자동 생성
        // ===========================
        System.out.println("[KEYVAULT] 키 로드 또는 생성 중...");
        myKeyPair = KeyVault.loadOrCreate(id, pw);     // 🔥 핵심
        myPrivateKey = myKeyPair.getPrivate();
        myPublicKey  = myKeyPair.getPublic();

        System.out.println("[KEYVAULT] 공개키/개인키 준비 완료.");

        myTag = id + "#0001";


        // ===========================
        // 3) 인증 요청 (공개키 포함!)
        // ===========================
        String authBody =
                "{\"id\":\"" + id + "\"," +
                        "\"password\":\"" + pw + "\"," +
                        "\"publicKey\":\"" + EcdhUtil.encodePublicKey(myPublicKey) + "\"}";

        ChatMessage authMsg = new ChatMessage(
                (choiceMenu == 1) ? MessageType.AUTH_SIGNUP : MessageType.AUTH_LOGIN,
                id,
                "server",
                authBody,
                "2025-11-19T00:00:00"
        );

        writer.println(toJson(authMsg));
        System.out.println("[SEND AUTH] " + toJson(authMsg));

        // 인증 결과 읽기
        String authLine = reader.readLine();
        ChatMessage authRes = JsonUtil.fromJson(authLine, ChatMessage.class);

        System.out.println("[AUTH_RESULT] " + authRes.getBody());

        if (!(authRes.getBody().startsWith("LOGIN_OK") ||
                authRes.getBody().startsWith("SIGNUP_OK"))) {
            System.out.println("인증 실패. 종료합니다.");
            socket.close();
            return;
        }

        System.out.println("[INFO] 인증 성공! 이제 키 교환/채팅 가능합니다.");



        // ===========================
        // 4) 서버 수신 전용 스레드
        // ===========================
        Thread recvThread = new Thread(() -> {
            try {
                String line;
                while ((line = reader.readLine()) != null) {

                    ChatMessage msg = JsonUtil.fromJson(line, ChatMessage.class);

                    // 🔹 시스템 메시지
                    if (msg.getType() == MessageType.SYSTEM) {
                        System.out.println("[SERVER] " + msg.getBody());
                    }

                    // 🔹 KEY_RES (상대 공개키 수신)
                    else if (msg.getType() == MessageType.KEY_RES) {

                        PublicKey otherPub = EcdhUtil.decodePublicKey(msg.getBody());

                        E2eeSession session = E2eeSession.create(myKeyPair, otherPub);
                        sessions.put(msg.getSender(), session);

                        System.out.println("[INFO] " + msg.getSender() + " 과의 세션 생성 완료!");
                    }

                    // 🔹 KEY_REQ (상대가 먼저 요청함)
                    else if (msg.getType() == MessageType.KEY_REQ) {

                        PublicKey otherPub = EcdhUtil.decodePublicKey(msg.getBody());

                        E2eeSession session = E2eeSession.create(myKeyPair, otherPub);
                        sessions.put(msg.getSender(), session);

                        System.out.println("[INFO] KEY_REQ: " + msg.getSender() + " 세션 저장됨");

                        // 상대에게 KEY_RES 보내기
                        ChatMessage res = ChatMessage.keyResponse(
                                myTag,
                                msg.getSender(),
                                myPublicKey,
                                "2025-11-19T00:00:00"
                        );

                        writer.println(toJson(res));
                    }

                    // 🔹 CHAT 메시지
                    else if (msg.getType() == MessageType.CHAT) {

                        E2eeSession session = sessions.get(msg.getSender());

                        if (session == null) {
                            System.out.println("[CHAT:RAW] " + msg.getSender() + " : " + msg.getBody());
                        } else {
                            EncryptedPayload payload = EncryptedPayload.fromWireString(msg.getBody());
                            String plain = session.decrypt(payload);
                            System.out.println("[CHAT] " + msg.getSender() + " : " + plain);
                        }
                    }

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
        // 5) 메인 입력 루프
        // ===========================
        while (true) {

            System.out.print("> ");
            String line = sc.nextLine();

            if (line.equalsIgnoreCase("/quit")) {
                System.out.println("클라이언트를 종료합니다.");
                break;
            }

            // -------------------------
            // /key target
            // -------------------------
            if (line.startsWith("/key ")) {

                String target = line.substring(5).trim();
                currentTarget = target;

                ChatMessage keyReq = ChatMessage.keyRequest(
                        myTag,
                        target,
                        myPublicKey,
                        "2025-11-19T00:00:00"
                );

                writer.println(toJson(keyReq));
                System.out.println("[SEND] " + toJson(keyReq));
                continue;
            }

            // -------------------------
            // 일반 메시지
            // -------------------------
            if (currentTarget == null) {
                System.out.println("[WARN] '/key 상대아이디' 먼저 실행하세요.");
                continue;
            }

            String target = currentTarget;
            String timestamp = "2025-11-21T00:00:00";

            E2eeSession session = sessions.get(target);

            ChatMessage chat;

            if (session == null) {
                // 평문 전송
                chat = new ChatMessage(
                        MessageType.CHAT,
                        myTag,
                        target,
                        line,
                        timestamp
                );
                System.out.println("[WARN] 세션 없음. 평문 전송.");
            } else {
                // 암호문 전송
                chat = ChatMessage.encryptedChat(
                        myTag,
                        target,
                        line,
                        session,
                        timestamp
                );
                System.out.println("[INFO] 암호화 후 전송.");
            }

            writer.println(toJson(chat));
            System.out.println("[SEND] " + toJson(chat));
        }

        socket.close();
        System.out.println("[NET] 연결 종료.");
    }
}
