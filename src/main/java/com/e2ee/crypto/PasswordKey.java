package com.e2ee.crypto;

import javax.crypto.SecretKeyFactory;
import javax.crypto.SecretKey;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

public class PasswordKey {

    public static SecretKey deriveKey(String password) throws Exception {
        byte[] salt = "fixed-salt-for-demo".getBytes(StandardCharsets.UTF_8);

        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                65536,
                256 // 256-bit AES key
        );

        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        byte[] keyBytes = skf.generateSecret(spec).getEncoded();

        return new SecretKeySpec(keyBytes, "AES");
    }
}
