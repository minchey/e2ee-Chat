package com.e2ee.session;

import com.e2ee.crypto.EncryptedPayload;
import com.e2ee.crypto.EcdhUtil;
import com.e2ee.crypto.AesGcmUtil;

import javax.crypto.SecretKey;
import java.security.KeyPair;
import java.security.PublicKey;

public class E2eeSession {

    private final KeyPair myKeyPair;   // 내 X25519 키쌍
    private final PublicKey theirPublicKey; // 상대 공개키
    private final SecretKey aesKey;     // 이 세션에서 쓸 AES 키

    // 생성자: 세션 안에 쓸 값 3개를 저장
    public E2eeSession(KeyPair myKeyPair,
                       PublicKey theirPublicKey,
                       SecretKey aesKey) {
        this.myKeyPair = myKeyPair;
        this.theirPublicKey = theirPublicKey;
        this.aesKey = aesKey;
    }

    public static E2eeSession create(KeyPair myKeyPair, PublicKey theirPublicKey) throws Exception{

        // 1) ECDH로 sharedSecret 만들기
        byte[] sharedSecret = EcdhUtil.deriveSharedSecret(myKeyPair.getPrivate(), //내 개인키
                                                            theirPublicKey);      //상대 공개키

        // 2) HKDF로 sharedSecret에서 AES 키 뽑기
        SecretKey aesKey = EcdhUtil.deriveAesKeyFromSharedSecret(sharedSecret);


        // 3) 세 값(내 키쌍, 상대 공개키, AES키)을 들고 있는 세션 객체를 만들어서 돌려줌
        return new E2eeSession(myKeyPair, theirPublicKey, aesKey);
    }

    // 이 세션의 AES 키로 문자열 암호화
    public EncryptedPayload encrypt(String plaintext) throws Exception {
        return AesGcmUtil.encrypt(plaintext, aesKey);
    }

    // 이 세션의 AES 키로 암호문 복호화
    public String decrypt(EncryptedPayload payload) throws Exception {
        return AesGcmUtil.decrypt(payload, aesKey);
    }


}
