package com.e2ee.protocol;


/**
 * 서버와 클라이언트 사이에 오가는 메시지 종류를 나타내는 열거형.
 */

public enum MessageType {
    CHAT,       // 일반 채팅 메시지
    KEY_REQ,    // 공개키 / 세션키 교환 요청
    KEY_RES,    // 공개키 / 세션키 교환 응답
    SYSTEM,     // 시스템 알림(서버 공지 등)
    AUTH_SIGNUP,   // 회원가입 요청
    AUTH_LOGIN,    // 로그인 요청
    AUTH_RESULT    // 회원가입/로그인 결과
}
