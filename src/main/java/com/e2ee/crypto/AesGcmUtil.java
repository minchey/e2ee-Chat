package com.e2ee.crypto;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.security.SecureRandom;
import java.util.Base64;

public class AesGcmUtil {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int AES_KEY_SIZE = 256;     // AES 256비트
    private static final int GCM_TAG_LENGTH = 128;   // 128bit Auth Tag
    private static final int NONCE_LENGTH = 12;      // 12byte = GCM 표준

    // 1) 세션키 생성
    public static SecretKey generateKey() throws Exception {
        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(AES_KEY_SIZE);
        return generator.generateKey();
    }

    // 2) 암호화
    public static EncryptedPayload encrypt(String plaintext, SecretKey key) throws Exception {
        byte[] nonce = new byte[NONCE_LENGTH];
        new SecureRandom().nextBytes(nonce);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);

        byte[] encrypted = cipher.doFinal(plaintext.getBytes());

        return new EncryptedPayload(
                ALGORITHM,
                Base64.getEncoder().encodeToString(nonce),
                Base64.getEncoder().encodeToString(encrypted)
        );
    }

    // 3) 복호화
    public static String decrypt(EncryptedPayload payload, SecretKey key) throws Exception {
        byte[] nonce = Base64.getDecoder().decode(payload.getNonceBase64());
        byte[] cipherData = Base64.getDecoder().decode(payload.getCipherBase64());

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);

        byte[] decrypted = cipher.doFinal(cipherData);
        return new String(decrypted);
    }
}
