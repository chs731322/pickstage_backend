package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.Signature;

import org.junit.jupiter.api.Test;

class WebPushCryptoTest {

    private static final String UA_PUBLIC = "BCVxsr7N_eNgVRqvHtD0zTZsEc6-VV-JvLexhqUzORcxaOzi6-AYWXvTBHm4bjyPjs7Vd8pZGH6SRpkNtoIAiw4";
    private static final String AS_PUBLIC = "BP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8";
    private static final String AS_PRIVATE = "yfWPiYE-n46HLnH0KqZOF1fJJU3MYrct3AELtAQ-oRw";

    // RFC 8291 부록 A의 예제와 한 바이트까지 같아야 한다
    @Test
    void 암호화_결과가_표준_예제와_같다() throws Exception {
        byte[] body = WebPushCrypto.encrypt(
                WebPushCrypto.decode("V2hlbiBJIGdyb3cgdXAsIEkgd2FudCB0byBiZSBhIHdhdGVybWVsb24"),
                WebPushCrypto.decode(UA_PUBLIC),
                WebPushCrypto.decode("BTBZMqHH6r4Tts7J_aSIgg"),
                WebPushCrypto.decode(AS_PRIVATE),
                WebPushCrypto.decode(AS_PUBLIC),
                WebPushCrypto.decode("DGv6ra1nlYgDCS1FRnbzlw"));

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        expected.writeBytes(WebPushCrypto.decode("DGv6ra1nlYgDCS1FRnbzlwAAEABBBP4z9KsN6nGRTbVYI_c7VJSPQTBtkgcy27mlmlMoZIIgDll6e3vCYLocInmYWAmS6TlzAC8wEqKK6PBru3jl7A8"));
        expected.writeBytes(WebPushCrypto.decode("8pfeW0KbunFT06SuDKoJH9Ql87S1QUrdirN6GcG7sFz1y1sqLgVi1VhjVkHsUoEsbI_0LpXMuGvnzQ"));
        assertArrayEquals(expected.toByteArray(), body);
    }

    @Test
    void 서버_서명은_공개키로_확인된다() throws Exception {
        String header = WebPushCrypto.vapidHeader("https://fcm.googleapis.com/fcm/send/abc", "https://p1cksta9e.com",
                AS_PUBLIC, AS_PRIVATE, 2_000_000_000L);
        String token = header.substring("vapid t=".length(), header.indexOf(", k="));
        String[] parts = token.split("\\.");

        Signature verifier = Signature.getInstance("SHA256withECDSAinP1363Format");
        verifier.initVerify(WebPushCrypto.publicKey(WebPushCrypto.decode(AS_PUBLIC)));
        verifier.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));

        assertTrue(verifier.verify(WebPushCrypto.decode(parts[2])));
        assertTrue(new String(WebPushCrypto.decode(parts[1]), StandardCharsets.UTF_8).contains("\"aud\":\"https://fcm.googleapis.com\""));
    }

    @Test
    void 브라우저_회사의_푸시_주소만_받는다() {
        assertTrue(WebPushService.isAllowedEndpoint("https://fcm.googleapis.com/fcm/send/x"));
        assertTrue(WebPushService.isAllowedEndpoint("https://web.push.apple.com/QL"));
        assertFalse(WebPushService.isAllowedEndpoint("http://fcm.googleapis.com/x"));
        assertFalse(WebPushService.isAllowedEndpoint("https://evil.example.com/x"));
        assertFalse(WebPushService.isAllowedEndpoint("https://fakefcm.googleapis.com.evil.com/x"));
    }
}
