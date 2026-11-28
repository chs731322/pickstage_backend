package com.pickstage.backend.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.pickstage.backend.service.WebPushService;

class AdminWebPushControllerTest {

    private final WebPushService service = mock(WebPushService.class);
    private final AdminWebPushController controller = new AdminWebPushController(service);

    // 글자 값은 success(message) 쪽이 골라지기 쉬워서, data 칸에 담기는지 확인한다
    @Test
    void 공개_키는_data_칸에_담아_보낸다() {
        when(service.publicKey(1L)).thenReturn("BPublicKey");

        var body = controller.publicKey(1L).getBody();

        assertEquals("BPublicKey", body.getData());
    }
}
