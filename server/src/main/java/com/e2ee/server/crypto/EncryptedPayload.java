package com.e2ee.server.crypto;

/**
 * 암호화된 데이터를 담는 간단한 그릇 클래스.
 *
 * - algorithm   : "AES/GCM/NoPadding" 같은 알고리즘 이름
 * - nonceBase64 : GCM Nonce(IV)를 Base64 문자열로 인코딩한 값
 * - cipherBase64: (cipherText + GCM Tag 전체)를 Base64 문자열로 인코딩한 값
 *
 * 실제 바이트 ↔ Base64 변환은 AesGcmUtil에서 하고,
 * 이 클래스는 "이미 Base64로 바뀐 문자열"만 보관한다.
 */
public class EncryptedPayload {

    // 어떤 알고리즘으로 암호화했는지 (예: "AES/GCM/NoPadding")
    private final String algorithm;

    // GCM에서 사용하는 Nonce(IV)를 Base64로 표현한 문자열
    private final String nonceBase64;

    // 암호문 + 태그 전체를 Base64로 표현한 문자열
    private final String cipherBase64;

    public EncryptedPayload(String algorithm, String nonceBase64, String cipherBase64) {
        this.algorithm = algorithm;
        this.nonceBase64 = nonceBase64;
        this.cipherBase64 = cipherBase64;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getNonceBase64() {
        return nonceBase64;
    }

    public String getCipherBase64() {
        return cipherBase64;
    }

    /**
     * 이 암호화 결과를 네트워크/파일 전송용 "문자열 1개"로 포장한다.
     *
     * 형식:  algorithm:nonceBase64:cipherBase64
     * 예시: AES/GCM/NoPadding:aaa...=:bbb...=
     *
     * 나중에 fromWireString()에서 같은 구분자 ':'로 다시 잘라서 복원한다.
     */
    public String toWireString() {
        return algorithm + ":" + nonceBase64 + ":" + cipherBase64;
    }

    /**
     * toWireString()으로 만든 문자열을 다시 EncryptedPayload 객체로 복원한다.
     *
     * @param s "algorithm:nonceBase64:cipherBase64" 형식의 문자열
     * @return EncryptedPayload 인스턴스
     */
    public static EncryptedPayload fromWireString(String s) {
        // 1) ':' 기준으로 3조각으로 나눈다
        String[] parts = s.split(":", 3);
        if (parts.length != 3) {
            throw new IllegalArgumentException("잘못된 암호 데이터 형식입니다: " + s);
        }

        String algorithm = parts[0];
        String nonceBase64 = parts[1];
        String cipherBase64 = parts[2];

        // 2) 위 3개 문자열 그대로 기존 생성자에 넣어 객체 생성
        return new EncryptedPayload(algorithm, nonceBase64, cipherBase64);
    }

    @Override
    public String toString() {
        // 디버그용: 너무 길어질 수 있으니 알고리즘과 앞부분만 출력
        return "EncryptedPayload{" +
                "algorithm='" + algorithm + '\'' +
                ", nonceBase64(len)=" + (nonceBase64 != null ? nonceBase64.length() : 0) +
                ", cipherBase64(len)=" + (cipherBase64 != null ? cipherBase64.length() : 0) +
                '}';
    }
}
