package com.e2ee.crypto;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

public class EcdhUtil {

    // 1) X25519 키쌍 생성
    public static KeyPair generateKeyPair() throws Exception{

        // 1. X25519 키쌍을 생성할 수 있는 KeyPairGenerator 공장 불러오기
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");

        // 2. 키 생성기 초기 설정 (X25519는 크기 고정이라 따로 크기 지정 안 해도 됨)
        generator.initialize(255);

        // 3. 개인키 + 공개키 생성
        KeyPair keyPair = generator.generateKeyPair();

        return keyPair;
    }

}
