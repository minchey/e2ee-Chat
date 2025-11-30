package com.e2ee.client.store;

import com.e2ee.crypto.PasswordKey;
import com.e2ee.crypto.AesGcmUtil;
import com.e2ee.crypto.EcdhUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

/**
 * =====================================
 *   KeyVault
 *   - 클라이언트 개인키/공개키 로컬 보관소
 *   - 개인키는 AES-GCM + PBKDF2 로 암호화
 *   - 공개키는 Base64 평문 저장
 * =====================================
 */
public class KeyVault {

    // 홈디렉터리: ~/.e2ee-chat/keystore
    private static final Path BASE_DIR =
            Paths.get(System.getProperty("user.home"), ".e2ee-chat", "keystore");

    // 파일명 규칙:
    //   <userId>_public.key
    //   <userId>_private.key
    private static Path pubFile(String userId) {
        return BASE_DIR.resolve(userId + "_public.key");
    }
    private static Path privFile(String userId) {
        return BASE_DIR.resolve(userId + "_private.key");
    }

    // ----------------------------------------------------
    // 0) 디렉토리 생성
    // ----------------------------------------------------
    static {
        try {
            if (!Files.exists(BASE_DIR)) {
                Files.createDirectories(BASE_DIR);
                System.out.println("[KeyVault] 디렉토리 생성: " + BASE_DIR);
            }
        } catch (Exception e) {
            throw new RuntimeException("KeyVault 디렉토리 생성 실패", e);
        }
    }

    // ----------------------------------------------------
    // 1) 키쌍 저장
    // ----------------------------------------------------
    public static void saveKeyPair(KeyPair kp, String userId, String password) throws Exception {

        PublicKey pub = kp.getPublic();
        PrivateKey priv = kp.getPrivate();

        // ====== 공개키 저장 (평문 Base64) ======
        byte[] pubBytes = pub.getEncoded();
        Files.write(pubFile(userId), Base64.getEncoder().encode(pubBytes));

        // ====== 개인키 저장 (AES-GCM + PBKDF2 암호화) ======
        byte[] privBytes = priv.getEncoded(); // PKCS8
        byte[] encryptedPriv = AesGcmUtil.encryptBytes(privBytes, password);

        Files.write(privFile(userId), encryptedPriv);

        System.out.println("[KeyVault] 개인키/공개키 저장 완료 (" + BASE_DIR + ")");
    }

    // ----------------------------------------------------
    // 2) 키쌍 로드 (AES-GCM 복호화)
    // ----------------------------------------------------
    public static KeyPair loadKeyPair(String userId, String password) throws Exception {

        // (1) 공개키 로드
        byte[] pubBase64 = Files.readAllBytes(pubFile(userId));
        byte[] pubBytes = Base64.getDecoder().decode(pubBase64);

        KeyFactory kf = KeyFactory.getInstance("X25519");
        PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(pubBytes));

        // (2) 개인키 로드 (AES-GCM 복호화)
        byte[] encPrivBytes = Files.readAllBytes(privFile(userId));
        byte[] privBytes = AesGcmUtil.decryptBytes(encPrivBytes, password);

        PrivateKey priv = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));

        System.out.println("[KeyVault] 로컬 개인키/공개키 로드 완료");

        return new KeyPair(pub, priv);
    }

    // ----------------------------------------------------
    // 3) 존재 여부 확인
    // ----------------------------------------------------
    public static boolean exists(String userId) {
        return Files.exists(pubFile(userId)) && Files.exists(privFile(userId));
    }

    // ----------------------------------------------------
    // 4) 로드 or 생성
    // ----------------------------------------------------
    public static KeyPair loadOrCreate(String userId, String password) throws Exception {
        if (exists(userId)) {
            return loadKeyPair(userId, password);
        }

        // 없으면 새로 생성
        KeyPair kp = EcdhUtil.generateKeyPair();
        saveKeyPair(kp, userId, password);
        return kp;
    }
}
