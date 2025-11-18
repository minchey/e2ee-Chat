package com.e2ee.crypto;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

public class EcdhUtil {

    // 1) X25519 키쌍 생성
    public static KeyPair generateKeyPair() throws Exception {

        // 1. X25519 키쌍을 생성할 수 있는 KeyPairGenerator 공장 불러오기
        KeyPairGenerator generator = KeyPairGenerator.getInstance("X25519");

        // 2. 키 생성기 초기 설정 (X25519는 크기 고정이라 따로 크기 지정 안 해도 됨)
        generator.initialize(255);

        // 3. 개인키 + 공개키 생성
        KeyPair keyPair = generator.generateKeyPair();

        return keyPair;
    }

    // 2) 내 개인키 + 상대 공개키로 공유 비밀키 생성하기
    public static byte[] deriveSharedSecret(PrivateKey myPrivate, PublicKey theirPublic) throws Exception {

        //1. X25519용 비밀 공유 기계(KeyAgreement) 준비
        KeyAgreement ka = KeyAgreement.getInstance("X25519");

        //2. 내 개인키로 초기화
        ka.init(myPrivate);

        //3. 상대 공개키와 한번의 교환 단계 수행
        ka.doPhase(theirPublic, true);

        //4. 최종 비밀키 생성
        byte[] sharedSecret = ka.generateSecret();

        return sharedSecret;
    }

    // 3) 공유 비밀키에서 AES 키 뽑아내기 (HKDF-SHA256, 정석)
    public static SecretKey deriveAesKeyFromSharedSecret(byte[] sharedSecret) throws Exception {

    }

    private static byte[] hkdfExtract(byte[] salt, byte[] ikm) throws Exception {
        //1. salt가 없으면 32바이트 0으로 대체
        if(salt == null || salt.length ==0){
            salt = new byte[32]; //자동으로 0으로 채워짐
        }

        // 2. HmacSHA256 준비 (키 = salt)
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(salt, "HmacSHA256");
        mac.init(keySpec);

        // 3. ikm(sharedSecret)을 넣어서 PRK 생성
        return mac.doFinal(ikm);
    }

    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws Exception {

    }
}
