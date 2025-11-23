package com.e2ee.server.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Base64;

import java.nio.charset.StandardCharsets;

public class AesGcmUtil {

    // 사용할 암호 알고리즘 이름 (JDK에게 알려줄 문자열)
    private static final String ALGORITHM = "AES/GCM/NoPadding";

    // AES 키 길이 (256비트 사용)
    private static final int AES_KEY_SIZE = 256;     // AES 256비트

    // GCM 인증 태그 길이 (128비트 = 16바이트)
    private static final int GCM_TAG_LENGTH = 128;   // 128bit Auth Tag

    // GCM에서 사용하는 nonce(IV) 길이 (12바이트가 표준)
    private static final int NONCE_LENGTH = 12;      // 12byte = GCM 표준

    // 1) 세션키 생성 - AES키
    public static SecretKey generateKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(AES_KEY_SIZE);
        return generator.generateKey();
    }

    // 2) 평문을 AES-GCM으로 암호화
    public static EncryptedPayload encrypt(String plaintext, SecretKey key) throws Exception {
        // 1. Nonce(IV) 12바이트 랜덤으로 만들기
        byte[] nonce = new byte[NONCE_LENGTH];
        new SecureRandom().nextBytes(nonce);

        // 2. Cipher(AES/GCM/NoPadding) 준비하기
        Cipher cipher = Cipher.getInstance(ALGORITHM);

        // GCM 모드에 Nonce와 태그 길이 설정
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);

        // 암호화 모드로 초기화 (ENCRYPT_MODE)
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        byte[] plainBytes = plaintext.getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = cipher.doFinal(plainBytes);

        return new EncryptedPayload(
                ALGORITHM,
                Base64.getEncoder().encodeToString(nonce),
                Base64.getEncoder().encodeToString(encrypted)
        );
    }

    // 3) 복호화
    public static String decrypt(EncryptedPayload payload, SecretKey key) throws Exception {

        // 1. payload 안에 들어있는 Base64 문자열을 다시 byte[]로 바꾸기
        byte[] nonce = Base64.getDecoder().decode(payload.getNonceBase64());
        byte[] cipherBytes = Base64.getDecoder().decode(payload.getCipherBase64());

        // 2. Cipher(AES/GCM/NoPadding) 준비
        Cipher cipher = Cipher.getInstance(ALGORITHM);

        // GCMParameterSpec에 똑같은 태그 길이 + nonce 사용
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);

        // 복호화 모드로 초기화 (DECRYPT_MODE)
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        byte[] decrypted = cipher.doFinal(cipherBytes);
        return new String(decrypted);
    }
    // 간단 테스트용 main 메서드
    public static void main(String[] args) throws Exception {
        // 1. AES 비밀키 하나 만들기
        javax.crypto.SecretKey key = generateKey();

        // 2. 평문 준비
        String text = "안녕, E2EE 세상!";

        System.out.println("평문: " + text);

        // 3. 암호화
        EncryptedPayload encrypted = encrypt(text, key);
        System.out.println("암호문(Base64): " + encrypted.getCipherBase64());

        // 4. 복호화
        String decrypted = decrypt(encrypted, key);
        System.out.println("복호화 결과: " + decrypted);
    }

}
