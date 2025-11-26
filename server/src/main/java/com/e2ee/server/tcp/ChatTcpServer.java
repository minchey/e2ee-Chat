package com.e2ee.server.tcp;

import com.e2ee.server.crypto.EcdhUtil;
import com.e2ee.server.protocol.AuthPayload;
import com.e2ee.server.protocol.ChatMessage;
import com.e2ee.server.protocol.MessageType;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatTcpServer {

    // ★ TCP 서버 포트 (클라이언트가 여기에 접속함)
    private static final int PORT = 9000;

    // ★ JSON <-> 자바 객체 변환기 (Gson)
    private final Gson gson = new Gson();

    // ★ 서버 자신의 ECDH 키쌍 (공개키/개인키)
    private KeyPair serverKeyPair;

    // ★ "아주 간단한" 유저 저장소 (실제 서비스면 DB로 가야 함)
    //    key: 아이디, value: 비밀번호
    private final Map<String, String> users = new ConcurrentHashMap<>();

    // ----------------------------------------------------
    // 1) 서버 시작 준비: ECDH 키쌍 만들기
    // ----------------------------------------------------
    @PostConstruct
    public void init() throws Exception {
        // 서버가 사용할 X25519 키쌍 생성
        serverKeyPair = EcdhUtil.generateKeyPair();
        System.out.println("[INIT] 서버 ECDH 키쌍 생성 완료");
    }

    // ----------------------------------------------------
    // 2) 스프링이 뜰 때 TCP 서버 스레드 하나를 같이 올린다
    // ----------------------------------------------------
    @PostConstruct   // 스프링 부트가 시작될 때 자동으로 실행
    public void start() {
        Thread t = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("[TCP] ChatServer started on port " + PORT);

                // ★ 여기서는 "접속만 받는다"
                //   실제 클라이언트 처리는 handleClient() 스레드에게 맡김
                while (true) {
                    Socket client = serverSocket.accept();
                    System.out.println("[TCP] 클라이언트가 연결을 시도했어요: " + client);

                    // 클라이언트 1명당 스레드 1개
                    new Thread(
                            () -> handleClient(client),
                            "client-" + client.getPort()
                    ).start();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "chat-tcp-server");

        t.setDaemon(true);  // 메인 스레드가 종료되면 같이 내려가도록
        t.start();
    }

    // ----------------------------------------------------
    // 3) 실제 클라이언트 1명을 담당하는 메서드
    //    - 여기서 AUTH / KEY_REQ / CHAT 등을 처리한다
    // ----------------------------------------------------
    private void handleClient(Socket client) {
        System.out.println("[CLIENT] 핸들러 스레드 시작: " + Thread.currentThread().getName());

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8),
                     true   // println() 때마다 자동 flush
             )) {

            String line;
            while ((line = br.readLine()) != null) {
                System.out.println("[서버] RAW: " + line);

                // 1) JSON 문자열 → ChatMessage 객체
                ChatMessage msg = gson.fromJson(line, ChatMessage.class);

                // 2) 타입에 따라 분기 처리
                if (msg.getType() == MessageType.AUTH_SIGNUP) {
                    handleSignup(msg, out);

                } else if (msg.getType() == MessageType.AUTH_LOGIN) {
                    handleLogin(msg, out);

                } else if (msg.getType() == MessageType.KEY_REQ) {
                    handleKeyRequest(msg, out);

                } else if (msg.getType() == MessageType.CHAT) {
                    handleChatRelay(msg, out);

                } else {
                    System.out.println("[서버] 알 수 없는 타입: " + msg.getType());
                }
            }

        } catch (Exception e) {
            System.out.println("[CLIENT] 연결 종료: " + client);
            //e.printStackTrace(); // 필요하면 자세한 로그
        }
    }

    // ----------------------------------------------------
    // 3-1) 회원가입 처리
    // ----------------------------------------------------
    private void handleSignup(ChatMessage msg, PrintWriter out) {
        // body: {"id":"aaa","password":"1111"} 라고 가정
        AuthPayload p = gson.fromJson(msg.getBody(), AuthPayload.class);
        String id = p.getId();
        String pw = p.getPassword();

        String result;
        if (users.containsKey(id)) {
            result = "SIGNUP_FAIL:ID_ALREADY_EXISTS";
        } else {
            users.put(id, pw);
            result = "SIGNUP_OK";
            System.out.println("[AUTH] 새 회원가입: " + id);
        }

        // 결과를 AUTH_RESULT 타입으로 응답
        ChatMessage res = new ChatMessage(
                MessageType.AUTH_RESULT,
                "server",          // 서버가 보냄
                msg.getSender(),   // 로그인 요청한 클라이언트
                result,            // 결과 문자열
                msg.getTimestamp()
        );
        out.println(gson.toJson(res));
    }

    // ----------------------------------------------------
    // 3-2) 로그인 처리
    // ----------------------------------------------------
    private void handleLogin(ChatMessage msg, PrintWriter out) {
        AuthPayload p = gson.fromJson(msg.getBody(), AuthPayload.class);
        String id = p.getId();
        String pw = p.getPassword();

        String result;
        if (!users.containsKey(id)) {
            result = "LOGIN_FAIL:ID_NOT_FOUND";
        } else if (!users.get(id).equals(pw)) {
            result = "LOGIN_FAIL:BAD_PASSWORD";
        } else {
            result = "LOGIN_OK";
            System.out.println("[AUTH] 로그인 성공: " + id);
        }

        ChatMessage res = new ChatMessage(
                MessageType.AUTH_RESULT,
                "server",
                msg.getSender(),
                result,
                msg.getTimestamp()
        );
        out.println(gson.toJson(res));
    }

    // ----------------------------------------------------
    // 3-3) 키 교환 요청 처리 (KEY_REQ → KEY_RES)
    // ----------------------------------------------------
    private void handleKeyRequest(ChatMessage msg, PrintWriter out) {
        System.out.println("[서버][KEY_REQ] from=" + msg.getSender()
                + " to=" + msg.getReceiver()
                + ", body(공개키 Base64)=" + msg.getBody());

        // 1) 서버 공개키를 Base64 문자열로 인코딩
        String serverPubKeyBase64 =
                EcdhUtil.encodePublicKey(serverKeyPair.getPublic());

        // 2) 클라에게 KEY_RES 응답
        ChatMessage res = new ChatMessage(
                MessageType.KEY_RES,
                "server",
                msg.getSender(),        // 요청 보낸 클라이언트에게 돌려줌
                serverPubKeyBase64,     // body = 서버 공개키(Base64)
                msg.getTimestamp()
        );

        out.println(gson.toJson(res));
    }

    // ----------------------------------------------------
    // 3-4) 채팅 릴레이 (서버는 암호문 내용을 모름)
    // ----------------------------------------------------
    private void handleChatRelay(ChatMessage msg, PrintWriter out) {
        System.out.println("[서버][CHAT] " + msg.getSender()
                + " -> " + msg.getReceiver()
                + " : " + msg.getBody());

        // 과제 요구사항: 서버는 "내용을 모른 채" 그대로 전달만 한다.
        ChatMessage echo = new ChatMessage(
                MessageType.CHAT,      // SYSTEM이 아니라 CHAT으로 그대로
                msg.getSender(),       // 원래 보낸 사람
                msg.getReceiver(),     // 원래 받는 사람
                msg.getBody(),         // 암호문 그대로
                msg.getTimestamp()
        );

        out.println(gson.toJson(echo));
    }
}
