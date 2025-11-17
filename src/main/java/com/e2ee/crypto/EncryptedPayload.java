package com.e2ee.crypto;

public class EncryptedPayload {
    private String algorithm;       // AES/GCM/NoPadding
    private String nonceBase64;     // IV/Nonce
    private String cipherBase64;    // 암호문 (Base64)

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
}
