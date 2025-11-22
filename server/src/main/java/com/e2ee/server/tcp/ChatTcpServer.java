package com.e2ee.server.tcp;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import java.io.BufferedReader;

import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import com.e2ee.server.protocol.ChatMessage;
import com.e2ee.server.protocol.MessageType;
import com.google.gson.Gson;

@Component
public class ChatTcpServer {

    //서버가 사용할 포트번호
    private static final int PORT = 9000;

    private final Gson gson = new Gson();  // ★ JSON <-> 객체 변환용


    @PostConstruct   // 스프링이 뜰 때 함께 실행해줘!
    public void start(){
        // 새로운 스레드에서 TCP 서버를 돌림 (스프링 메인 스레드를 막지 않으려고)
        Thread t = new Thread(() -> {
            try(ServerSocket serverSocket = new ServerSocket(PORT)){
                System.out.println("[TCP] ChatServer started on port " + PORT);

                // 지금은 일단 무한 대기만 — 나중에 여기서 accept() 하고 클라이언트 처리할 거야.
                while (true) {
                    var client = serverSocket.accept();
                    System.out.println("[TCP] 클라이언트가 연결을 시도했어요.");

                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(client.getInputStream(), StandardCharsets.UTF_8)
                    );

                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println("[서버] RAW: " + line);

                        // 1) JSON 문자열 -> ChatMessage 객체로 변환
                        ChatMessage msg = gson.fromJson(line, ChatMessage.class);

                        // 2) type에 따라 분기
                        if (msg.getType() == MessageType.KEY_REQ) {
                            System.out.println("[서버][KEY_REQ] from=" + msg.getSender()
                                    + " to=" + msg.getReceiver()
                                    + ", body(공개키 Base64)=" + msg.getBody());
                        } else if (msg.getType() == MessageType.CHAT) {
                            System.out.println("[ 서버 ][CHAT] " + msg.getSender()
                                    + " -> " + msg.getReceiver()
                                    + " : " + msg.getBody());
                        } else {
                            System.out.println("[서버] 알 수 없는 타입: " + msg.getType());
                        }
                    }
                }

            }catch (Exception e){
                e.printStackTrace();
            }
        }, "chat-tcp-server");

        t.setDaemon(true);
        t.start();
    }
}
