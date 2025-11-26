package com.e2ee.server.protocol;

public enum MessageType {
    CHAT,       // 일반 채팅
    KEY_REQ,    // 키 교환 요청
    KEY_RES,    // 키 교환 응답
    SYSTEM,      // 시스템 메시지(공지 등)
    AUTH_SIGNUP,   // 회원가입 요청
    AUTH_LOGIN,    // 로그인 요청
    AUTH_RESULT    // 회원가입/로그인 결과
}
