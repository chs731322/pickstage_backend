package com.pickstage.backend.service;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.ECParameterSpec;
import java.security.spec.ECPoint;
import java.security.spec.ECPrivateKeySpec;
import java.security.spec.ECPublicKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

// 웹 푸시 암호화(RFC 8291, aes128gcm)와 서버 신원 서명(VAPID, RFC 8292). 자바 기본 암호 기능만 쓴다.
final class WebPushCrypto {

    private static final int RECORD_SIZE = 4096;
    private static final int POINT_LENGTH = 65; // 0x04 + x(32) + y(32)

    private WebPushCrypto() {
    }

    static byte[] decode(String base64url) {
        return Base64.getUrlDecoder().decode(base64url.trim());
    }

    static String encode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // 본문 = salt(16) + record size(4) + 키 길이(1) + 서버 공개키(65) + 암호문
    static byte[] encrypt(byte[] plaintext, byte[] receiverPublic, byte[] authSecret,
            byte[] senderPrivate, byte[] senderPublic, byte[] salt) throws GeneralSecurityException {
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(privateKey(senderPrivate));
        agreement.doPhase(publicKey(receiverPublic), true);
        byte[] shared = agreement.generateSecret();

        byte[] keyInfo = concat("WebPush: info\0".getBytes(StandardCharsets.US_ASCII), receiverPublic, senderPublic);
        byte[] ikm = expand(hmac(authSecret, shared), keyInfo, 32);
        byte[] prk = hmac(salt, ikm);
        byte[] key = expand(prk, "Content-Encoding: aes128gcm\0".getBytes(StandardCharsets.US_ASCII), 16);
        byte[] nonce = expand(prk, "Content-Encoding: nonce\0".getBytes(StandardCharsets.US_ASCII), 12);

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        byte[] encrypted = cipher.doFinal(concat(plaintext, new byte[] { 2 })); // 0x02: 마지막 조각

        byte[] header = ByteBuffer.allocate(16 + 4 + 1 + POINT_LENGTH)
                .put(salt).putInt(RECORD_SIZE).put((byte) POINT_LENGTH).put(senderPublic).array();
        return concat(header, encrypted);
    }

    // Authorization: vapid t=<JWT>, k=<서버 공개키>
    static String vapidHeader(String endpoint, String subject, String publicKey, String privateKey, long expiresAtSeconds)
            throws GeneralSecurityException {
        URI uri = URI.create(endpoint);
        String audience = uri.getScheme() + "://" + uri.getAuthority();
        String header = encode("{\"typ\":\"JWT\",\"alg\":\"ES256\"}".getBytes(StandardCharsets.UTF_8));
        String payload = encode(("{\"aud\":\"" + audience + "\",\"exp\":" + expiresAtSeconds + ",\"sub\":\"" + subject + "\"}")
                .getBytes(StandardCharsets.UTF_8));
        Signature signature = Signature.getInstance("SHA256withECDSAinP1363Format");
        signature.initSign(privateKey(decode(privateKey)));
        signature.update((header + "." + payload).getBytes(StandardCharsets.US_ASCII));
        return "vapid t=" + header + "." + payload + "." + encode(signature.sign()) + ", k=" + publicKey.trim();
    }

    static PrivateKey privateKey(byte[] scalar) throws GeneralSecurityException {
        return KeyFactory.getInstance("EC").generatePrivate(new ECPrivateKeySpec(new BigInteger(1, scalar), curve()));
    }

    static PublicKey publicKey(byte[] point) throws GeneralSecurityException {
        if (point.length != POINT_LENGTH || point[0] != 4) {
            throw new GeneralSecurityException("공개키 형식이 올바르지 않습니다.");
        }
        ECPoint w = new ECPoint(new BigInteger(1, Arrays.copyOfRange(point, 1, 33)),
                new BigInteger(1, Arrays.copyOfRange(point, 33, 65)));
        return KeyFactory.getInstance("EC").generatePublic(new ECPublicKeySpec(w, curve()));
    }

    private static ECParameterSpec curve() throws GeneralSecurityException {
        AlgorithmParameters parameters = AlgorithmParameters.getInstance("EC");
        parameters.init(new ECGenParameterSpec("secp256r1"));
        return parameters.getParameterSpec(ECParameterSpec.class);
    }

    private static byte[] hmac(byte[] key, byte[] data) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data);
    }

    // HKDF-Expand (32바이트 이하만 필요해서 한 블록)
    private static byte[] expand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        return Arrays.copyOf(hmac(prk, concat(info, new byte[] { 1 })), length);
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}
