package com.e2ee.server.tcp;

import com.e2ee.server.protocol.AuthPayload;
import com.e2ee.server.protocol.ChatMessage;
import com.e2ee.server.protocol.MessageType;
import com.e2ee.server.store.UserStore;
import com.e2ee.server.store.HistoryStore;
import com.google.gson.Gson;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatTcpServer {

    private static final int PORT = 9000;
    private final Gson gson = new Gson();

    // userTag -> PrintWriter
    private final Map<String, PrintWriter> clientOutputs = new ConcurrentHashMap<>();

    // 파일 기반 유저 저장소 + 히스토리 저장소
    private final UserStore userStore = new UserStore();
    private final HistoryStore historyStore = new HistoryStore();


    // 서버 시작
    @PostConstruct
    public void start() {
        Thread t = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("[TCP] ChatServer started on port " + PORT);

                while (true) {
                    Socket client = serverSocket.accept();
                    System.out.println("[TCP] 클라이언트 접속: " + client);

                    new Thread(
                            () -> handleClient(client),
                            "client-" + client.getPort()
                    ).start();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        t.setDaemon(true);
        t.start();
    }


    private void handleClient(Socket client) {
        System.out.println("[CLIENT] 핸들러 시작");

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(client.getOutputStream(), StandardCharsets.UTF_8),
                     true)) {

            String line;
            while ((line = br.readLine()) != null) {

                ChatMessage msg = gson.fromJson(line, ChatMessage.class);
                System.out.println("[서버 RAW] " + line);

                // sender → out 매핑 (처음만 등록됨)
                clientOutputs.putIfAbsent(msg.getSender(), out);

                handleMessage(msg, out);
            }

        } catch (Exception e) {
            System.out.println("[CLIENT] 연결 종료: " + client);
        }
    }


    // ============ 회원가입 ============
    // ----------------------------------------------------
    // 3-1) 회원가입 처리 (id, pw, publicKey 저장)
    // ----------------------------------------------------
    private void handleSignup(ChatMessage msg, PrintWriter out) {

        AuthPayload p = gson.fromJson(msg.getBody(), AuthPayload.class);

        String id = p.getId();
        String pw = p.getPassword();
        String publicKey = p.getPublicKey();  // ★ 여기서 공개키를 읽음

        if (id == null || pw == null || publicKey == null) {
            ChatMessage res = new ChatMessage(
                    MessageType.AUTH_RESULT,
                    "server",
                    msg.getSender(),
                    "SIGNUP_FAIL:BAD_PAYLOAD",
                    msg.getTimestamp()
            );
            out.println(gson.toJson(res));
            return;
        }

        if (userStore.exists(id)) {
            // 이미 존재
            ChatMessage res = new ChatMessage(
                    MessageType.AUTH_RESULT,
                    "server",
                    msg.getSender(),
                    "SIGNUP_FAIL:ID_EXISTS",
                    msg.getTimestamp()
            );
            out.println(gson.toJson(res));
            return;
        }

        // ★ 저장: id, pw, publicKey
        userStore.addUser(id, pw, publicKey);

        System.out.println("[AUTH] 회원가입 완료: " + id);

        ChatMessage res = new ChatMessage(
                MessageType.AUTH_RESULT,
                "server",
                msg.getSender(),
                "SIGNUP_OK",
                msg.getTimestamp()
        );

        out.println(gson.toJson(res));
    }



    // ============ 로그인 ============
    private void handleLogin(ChatMessage msg, PrintWriter out) {

        AuthPayload p = gson.fromJson(msg.getBody(), AuthPayload.class);
        String id = p.getId();
        String pw = p.getPassword();

        String result;

        if (!userStore.exists(id)) result = "LOGIN_FAIL:ID_NOT_FOUND";
        else if (!userStore.checkPassword(id, pw)) result = "LOGIN_FAIL:BAD_PASSWORD";
        else result = "LOGIN_OK";

        ChatMessage res = new ChatMessage(
                MessageType.AUTH_RESULT,
                "server",
                msg.getSender(),
                result,
                msg.getTimestamp()
        );

        out.println(gson.toJson(res));
    }


    // ================= KEY_REQ → 서버가 직접 KEY_RES 보내기 ==================
    private void handleKeyRequest(ChatMessage msg) {

        String targetId = msg.getReceiver();

        // 🔥 서버에 저장된 공개키 꺼내기
        String targetPubKey = userStore.getPublicKey(targetId);

        if (targetPubKey == null) {
            // 상대 없음
            PrintWriter senderOut = clientOutputs.get(msg.getSender());
            if (senderOut != null) {
                ChatMessage warn = new ChatMessage(
                        MessageType.SYSTEM,
                        "server",
                        msg.getSender(),
                        "NO_SUCH_USER:" + targetId,
                        msg.getTimestamp()
                );
                senderOut.println(gson.toJson(warn));
            }
            return;
        }

        // 🔥 KEY_RES 생성
        ChatMessage res = new ChatMessage(
                MessageType.KEY_RES,
                "server",               // server → requester
                msg.getSender(),        // 요청자에게 보내기
                targetPubKey,           // 공개키
                msg.getTimestamp()
        );

        PrintWriter senderOut = clientOutputs.get(msg.getSender());
        if (senderOut != null) senderOut.println(gson.toJson(res));

        System.out.println("[KEY] 서버가 공개키 전달: " +
                msg.getReceiver() + " → " + msg.getSender());
    }


    // ================= CHAT 릴레이 + 히스토리 저장 ==================
    private void handleChat(ChatMessage msg, PrintWriter out) {

        System.out.println("[서버][CHAT] " +
                msg.getSender() + " -> " + msg.getReceiver() +
                " : " + msg.getBody());

        // 🔥 서버는 내용 해독 없이 그대로 저장
        historyStore.add(msg);

        String json = gson.toJson(msg);

        // 전체방
        if ("ALL".equalsIgnoreCase(msg.getReceiver())) {
            for (PrintWriter w : clientOutputs.values()) {
                w.println(json);
            }
            return;
        }

        // 1:1 메시지
        PrintWriter targetOut = clientOutputs.get(msg.getReceiver());
        if (targetOut != null) {
            targetOut.println(json);
        } else {
            // 대상이 오프라인
            ChatMessage warn = new ChatMessage(
                    MessageType.SYSTEM,
                    "server",
                    msg.getSender(),
                    "TARGET_OFFLINE:" + msg.getReceiver(),
                    msg.getTimestamp()
            );
            out.println(gson.toJson(warn));
        }
    }


    // ============ 메시지 분배 ===============
    private void handleMessage(ChatMessage msg, PrintWriter out) {

        if (msg.getType() == MessageType.AUTH_SIGNUP) {
            handleSignup(msg, out);
            return;
        }

        if (msg.getType() == MessageType.AUTH_LOGIN) {
            handleLogin(msg, out);
            return;
        }

        if (msg.getType() == MessageType.KEY_REQ) {
            handleKeyRequest(msg);
            return;
        }

        if (msg.getType() == MessageType.CHAT) {
            handleChat(msg, out);
            return;
        }

        System.out.println("[서버] 알 수 없는 타입: " + msg.getType());
    }

}
