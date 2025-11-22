package com.e2ee.server.tcp;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.net.ServerSocket;

@Component
public class ChatTcpServer {

    //서버가 사용할 포트번호
    private static final int PORT = 9000;

    @PostConstruct   // 스프링이 뜰 때 함께 실행해줘!
    public void start(){
        // 새로운 스레드에서 TCP 서버를 돌림 (스프링 메인 스레드를 막지 않으려고)
        Thread t = new Thread(() -> {
            try(ServerSocket serverSocket = new ServerSocket(PORT)){
                System.out.println("[TCP] ChatServer started on port " + PORT);

                // 지금은 일단 무한 대기만 — 나중에 여기서 accept() 하고 클라이언트 처리할 거야.
                while(true){
                    serverSocket.accept(); // 연결만 받아들이고 아무 것도 안함 (테스트용)
                    System.out.println("[TCP] 클라이언트가 연결을 시도했어요.");
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }, "chat-tcp-server");

        t.setDaemon(true);
        t.start();
    }
}
