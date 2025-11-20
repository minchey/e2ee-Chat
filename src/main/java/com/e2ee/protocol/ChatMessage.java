package com.e2ee.protocol;

import com.e2ee.crypto.EncryptedPayload;
import com.e2ee.session.E2eeSession;
import com.e2ee.crypto.EcdhUtil;


import java.security.PublicKey;

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

    // 키 교환 요청 메시지 만들기 (KEY_REQ)
    public static ChatMessage keyRequest(String sender,
                                         String receiver,
                                         PublicKey myPublicKey,
                                         String timestamp) {

        // 내 공개키를 Base64 문자열로 바꿈
        String pubKeyBase64 = EcdhUtil.encodePublicKey(myPublicKey);

        // body에 공개키 문자열을 넣어서 ChatMessage 만들어서 돌려줌
        return new ChatMessage(
                MessageType.KEY_REQ,
                sender,
                receiver,
                pubKeyBase64,
                timestamp
        );
    }

    // 키 교환 응답 메시지 만들기 (KEY_RES)
    public static ChatMessage keyResponse(String sender,
                                          String receiver,
                                          java.security.PublicKey myPublicKey,
                                          String timestamp) {

        String pubKeyBase64 = com.e2ee.crypto.EcdhUtil.encodePublicKey(myPublicKey);

        return new ChatMessage(
                MessageType.KEY_RES,
                sender,
                receiver,
                pubKeyBase64,
                timestamp
        );
    }

    // body에 들어있는 Base64 공개키를 PublicKey 객체로 복원
    public java.security.PublicKey extractPeerPublicKey() throws Exception {
        return com.e2ee.crypto.EcdhUtil.decodePublicKey(this.body);
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

    /**
     * 평문 문자열을 받아서, 주어진 E2eeSession으로 암호화한 CHAT 메시지를 생성한다.
     */
    public static ChatMessage encryptedChat(String sender,
                                            String receiver,
                                            String plaintext,
                                            E2eeSession session,
                                            String timestamp) throws Exception {

        // 1) 평문을 세션의 AES 키로 암호화
        EncryptedPayload payload = session.encrypt(plaintext);

        // 2) EncryptedPayload를 문자열 1개로 포장
        String body = payload.toWireString();

        // 3) CHAT 타입 메시지 생성
        return new ChatMessage(
                MessageType.CHAT,
                sender,
                receiver,
                body,
                timestamp
        );
    }

    /**
     * 이 메시지가 CHAT 타입이고, body 안에 암호문이 들어있다고 가정했을 때,
     * 주어진 E2eeSession으로 복호화하여 평문을 꺼낸다.
     */

    public String decryptBody(com.e2ee.session.E2eeSession session) throws Exception {
        if (this.type != MessageType.CHAT) {
            throw new IllegalStateException("CHAT 타입이 아닌 메시지는 decryptBody를 사용할 수 없습니다.");
        }

        // 1) body 문자열을 EncryptedPayload로 복원
        EncryptedPayload payload = EncryptedPayload.fromWireString(this.body);

        // 2) 세션의 AES 키로 복호화
        return session.decrypt(payload);
    }

}
