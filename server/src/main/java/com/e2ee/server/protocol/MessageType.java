package com.e2ee.server.protocol;

public enum MessageType {
    CHAT,       // 일반 채팅
    KEY_REQ,    // 키 교환 요청
    KEY_RES,    // 키 교환 응답
    SYSTEM      // 시스템 메시지(공지 등)
}
