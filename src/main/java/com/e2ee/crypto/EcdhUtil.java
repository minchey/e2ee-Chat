package com.e2ee.crypto;

import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

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

        // 1. HKDF-Extract: PRK 만들기 (salt는 일단 비움)
        byte[] prk = hkdfExtract(null, sharedSecret);

        // 2. info: 용도 태그 (원하는 문자열 넣어도 됨)
        byte[] info = "E2EE-Chat-AES-GCM".getBytes(StandardCharsets.UTF_8);

        // 3. HKDF-Expand: 32바이트짜리 AES 키로 확장
        byte[] okm = hkdfExpand(prk, info, 32); // 32바이트 = 256bit

        // 4. 이 바이트 배열을 AES SecretKey로 포장
        return new SecretKeySpec(okm, "AES");
    }

    // HKDF-Extract 단계: salt와 sharedSecret으로 PRK 만들기
    private static byte[] hkdfExtract(byte[] salt, byte[] ikm) throws Exception {
        //1. salt가 없으면 32바이트 0으로 대체
        if (salt == null || salt.length == 0) {
            salt = new byte[32]; //자동으로 0으로 채워짐
        }

        // 2. HmacSHA256 준비 (키 = salt)
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(salt, "HmacSHA256");
        mac.init(keySpec);

        // 3. ikm(sharedSecret)을 넣어서 PRK 생성
        return mac.doFinal(ikm);
    }

    // HKDF-Expand 단계: PRK에서 원하는 길이만큼 키 뽑기
    private static byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws Exception {
        int hashLen = 32; //SHA-256 출력길이
        int n = (int) Math.ceil((double) length / hashLen);

        byte[] okm = new byte[length]; // Output Keying Material
        byte[] previousT = new byte[0];
        int offset = 0;

        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(prk, "HmacSHA256");
        mac.init(keySpec);

        for (int i = 1; i <= n; i++) {
            mac.reset();

            // T(i) = HMAC-PRK(T(i-1) | info | i)
            mac.update(previousT);
            if (info != null) {
                mac.update(info);
            }
            mac.update((byte) i);

            byte[] t = mac.doFinal();

            int bytesToCopy = Math.min(hashLen, length - offset);
            System.arraycopy(t, 0, okm, offset, bytesToCopy);
            offset += bytesToCopy;
            previousT = t;
        }
        return okm;
    }
    // === 공개키 <-> Base64 문자열 변환 ===

    // PublicKey -> Base64 문자열
    public static String encodePublicKey(PublicKey publicKey) {
        byte[] encoded = publicKey.getEncoded();                 // 공개키 바이트 배열
        return Base64.getEncoder().encodeToString(encoded);      // Base64 문자열로 변환
    }

    // Base64 문자열 -> PublicKey
    public static PublicKey decodePublicKey(String base64) throws Exception {
        byte[] encoded = Base64.getDecoder().decode(base64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(encoded);
        KeyFactory kf = KeyFactory.getInstance("X25519");
        return kf.generatePublic(spec);
    }

    public static void main(String[] args) throws Exception {
        // 1. A와 B 각각 X25519 키쌍 생성
        java.security.KeyPair a = generateKeyPair();
        java.security.KeyPair b = generateKeyPair();

        // 2. 각자 sharedSecret 계산
        byte[] secretA = deriveSharedSecret(a.getPrivate(), b.getPublic());
        byte[] secretB = deriveSharedSecret(b.getPrivate(), a.getPublic());

        System.out.println("sharedSecret 동일? " + java.util.Arrays.equals(secretA, secretB));

        // 3. 각자 sharedSecret에서 AES 키 생성(HKDF)
        javax.crypto.SecretKey aesKeyA = deriveAesKeyFromSharedSecret(secretA);
        javax.crypto.SecretKey aesKeyB = deriveAesKeyFromSharedSecret(secretB);

        // 4. 같은 AES 키인지 확인 (encoded 비교)
        boolean sameKey = java.util.Arrays.equals(
                aesKeyA.getEncoded(), aesKeyB.getEncoded()
        );
        System.out.println("AES 키 동일? " + sameKey);

        // 5. A가 메시지를 암호화, B가 복호화
        String msg = "ECDH + HKDF + AES-GCM 정석 E2EE 테스트!";
        EncryptedPayload payload = AesGcmUtil.encrypt(msg, aesKeyA);
        String decrypted = AesGcmUtil.decrypt(payload, aesKeyB);

        System.out.println("원문: " + msg);
        System.out.println("복호화 결과: " + decrypted);
    }

    // ★ 추가: PrivateKey + PublicKey → SecretKey 한 번에 만드는 헬퍼 함수
    public static SecretKey deriveAesKeyFromSharedSecret(
            PrivateKey myPrivate, PublicKey theirPublic) throws Exception {

        // 1) 공유 비밀 생성
        byte[] sharedSecret = deriveSharedSecret(myPrivate, theirPublic);

        // 2) 공유 비밀로부터 AES 키 생성
        return deriveAesKeyFromSharedSecret(sharedSecret);
    }


}
