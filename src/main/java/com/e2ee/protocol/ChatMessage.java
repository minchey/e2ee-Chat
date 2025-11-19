package com.e2ee.protocol;

import com.e2ee.crypto.EncryptedPayload;

/**
 * 서버와 클라이언트가 주고받는 공통 메시지 포맷.
 *
 * - type     : 메시지 종류 (CHAT, KEY_REQ, KEY_RES 등)
 * - sender   : 보낸 사람 ID 또는 닉네임
 * - receiver : 받는 사람 ID 또는 닉네임 (또는 "ALL")
 * - body     : 내용 (암호문 또는 키 정보 등), 문자열 하나로 통일
 * - timestamp: 문자열 형태의 시간 정보 (예: 2025-11-19T20:30:15)
 */
public class ChatMessage {
    private MessageType type;
    private String sender;
    private String receiver;
    private String body;
    private String timestamp;

    // 기본 생성자 (Gson 같은 라이브러리가 사용)
    public ChatMessage() {
    }


    public ChatMessage(MessageType type,
                       String sender,
                       String receiver,
                       String body,
                       String timestamp) {
        this.type = type;
        this.sender = sender;
        this.receiver = receiver;
        this.body = body;
        this.timestamp = timestamp;
    }


    public MessageType getType() {
        return type;
    }

    public String getSender() {
        return sender;
    }

    public String getReceiver() {
        return receiver;
    }

    public String getBody() {
        return body;
    }

    public String getTimestamp() {
        return timestamp;
    }
}
