package com.e2ee.server.protocol;

/**
 * 클라이언트가 보내오는 JSON 한 줄을 담는 그릇.
 * (클라 쪽 ChatMessage랑 같은 모양이어야 함)
 */
public class ChatMessage {

    private MessageType type;
    private String sender;
    private String receiver;
    private String body;
    private String timestamp;

    // Gson이 사용하기 위한 기본 생성자
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

    @Override
    public String toString() {
        return "ChatMessage{" +
                "type=" + type +
                ", sender='" + sender + '\'' +
                ", receiver='" + receiver + '\'' +
                ", body='" + body + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}

