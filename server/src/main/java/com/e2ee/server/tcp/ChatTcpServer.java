package com.e2ee.server.tcp;

import com.e2ee.server.protocol.AuthPayload;
import com.e2ee.server.protocol.ChatMessage;
import com.e2ee.server.protocol.MessageType;
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

    // userTag -> printWriter
    private final Map<String, PrintWriter> clientOutputs = new ConcurrentHashMap<>();

    // id -> password
    private final Map<String, String> users = new ConcurrentHashMap<>();


    @PostConstruct
    public void start() {
        Thread t = new Thread(() -> {
            try (ServerSocket serverSocket = new ServerSocket(PORT)) {
                System.out.println("[TCP] ChatServer started on port " + PORT);

                while (true) {
                    Socket client = serverSocket.accept();
                    System.out.println("[TCP] 클라이언트 접속: " + client);

                    new Thread(() -> handleClient(client),
                            "client-" + client.getPort()).start();
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

                clientOutputs.putIfAbsent(msg.getSender(), out);

                handleMessage(msg, out);
            }

        } catch (Exception e) {
            System.out.println("[CLIENT] 연결 종료: " + client);
        }
    }


    private void handleSignup(ChatMessage msg, PrintWriter out) {

        AuthPayload p = gson.fromJson(msg.getBody(), AuthPayload.class);
        String id = p.getId();
        String pw = p.getPassword();

        String result;
        if (users.containsKey(id)) {
            result = "SIGNUP_FAIL:ID_EXISTS";
        } else {
            users.put(id, pw);
            result = "SIGNUP_OK";
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


    private void handleLogin(ChatMessage msg, PrintWriter out) {

        AuthPayload p = gson.fromJson(msg.getBody(), AuthPayload.class);
        String id = p.getId();
        String pw = p.getPassword();

        String result;

        if (!users.containsKey(id)) result = "LOGIN_FAIL:ID_NOT_FOUND";
        else if (!users.get(id).equals(pw)) result = "LOGIN_FAIL:BAD_PASSWORD";
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


    // ============ KEY_REQ: 상대에게 그대로 전달 ============
    private void handleKeyRequest(ChatMessage msg) {
        PrintWriter target = clientOutputs.get(msg.getReceiver());
        if (target != null) target.println(gson.toJson(msg));
    }

    // ============ KEY_RES: 상대에게 그대로 전달 ============
    private void handleKeyResponse(ChatMessage msg) {
        PrintWriter target = clientOutputs.get(msg.getReceiver());
        if (target != null) target.println(gson.toJson(msg));
    }


    private void handleMessage(ChatMessage msg, PrintWriter out) {

        if (msg.getType() == MessageType.AUTH_SIGNUP) {
            handleSignup(msg, out);
            return;

        } else if (msg.getType() == MessageType.AUTH_LOGIN) {
            handleLogin(msg, out);
            return;

        } else if (msg.getType() == MessageType.KEY_REQ) {
            handleKeyRequest(msg);
            return;

        } else if (msg.getType() == MessageType.KEY_RES) {
            handleKeyResponse(msg);
            return;
        }

        // ===================== CHAT =====================
        if (msg.getType() == MessageType.CHAT) {

            System.out.println("[서버][CHAT] " +
                    msg.getSender() + " -> " + msg.getReceiver() +
                    " : " + msg.getBody());

            String json = gson.toJson(msg);

            if ("ALL".equalsIgnoreCase(msg.getReceiver())) {
                for (PrintWriter w : clientOutputs.values()) {
                    w.println(json);
                }
            } else {
                PrintWriter target = clientOutputs.get(msg.getReceiver());
                if (target != null) {
                    target.println(json);
                } else {
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

            return;
        }

        System.out.println("[서버] 알 수 없는 타입: " + msg.getType());
    }
}
