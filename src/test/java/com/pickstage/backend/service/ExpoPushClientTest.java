package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.pickstage.backend.global.exception.ExternalServiceUnavailableException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class ExpoPushClientTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String text) throws Exception {
        return mapper.readTree(text);
    }

    @Test
    void 접수된_푸시와_거절된_푸시를_순서대로_읽는다() throws Exception {
        List<ExpoPushClient.Ticket> tickets = ExpoPushClient.parseTickets(json("""
                {"data":[
                  {"status":"ok","id":"a"},
                  {"status":"error","message":"not registered","details":{"error":"DeviceNotRegistered"}},
                  {"status":"error","message":"too big","details":{"error":"MessageTooBig"}}
                ]}"""), 3);

        assertEquals(3, tickets.size());
        assertTrue(tickets.get(0).ok());
        assertFalse(tickets.get(1).ok());
        assertTrue(tickets.get(1).deviceNotRegistered());
        assertEquals("MessageTooBig", tickets.get(2).errorCode());
        assertFalse(tickets.get(2).deviceNotRegistered());
        assertNull(tickets.get(0).errorCode());
    }

    @Test
    void 결과가_모자라면_나머지는_알_수_없는_오류로_채우고_넘치면_자른다() throws Exception {
        List<ExpoPushClient.Ticket> fewer = ExpoPushClient.parseTickets(json("{\"data\":[{\"status\":\"ok\"}]}"), 3);
        assertEquals(3, fewer.size());
        assertTrue(fewer.get(0).ok());
        assertEquals("Unknown", fewer.get(1).errorCode());

        List<ExpoPushClient.Ticket> more = ExpoPushClient.parseTickets(
                json("{\"data\":[{\"status\":\"ok\"},{\"status\":\"ok\"},{\"status\":\"ok\"}]}"), 2);
        assertEquals(2, more.size());
    }

    @Test
    void 요청_전체가_거절되면_실패로_본다() throws Exception {
        assertThrows(ExternalServiceUnavailableException.class, () -> ExpoPushClient.parseTickets(
                json("{\"errors\":[{\"code\":\"PUSH_TOO_MANY_EXPERIENCE_IDS\",\"message\":\"x\"}]}"), 1));
    }
}
