package com.pickstage.backend.global.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.pickstage.backend.global.response.ApiResponse;

class GlobalExceptionHandlerTest {

    @Test
    void DB_오류_메시지는_응답에_담지_않는다() {
        String sqlMessage = "could not execute statement [Data too long for column 'title' at row 1] [insert into festivals (title) values (?)]";
        ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler()
                .handleDataIntegrityViolation(new DataIntegrityViolationException(sqlMessage, new RuntimeException("Data too long")));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        String message = response.getBody().getMessage();
        assertFalse(message.contains("insert into"));
        assertFalse(message.contains("festivals"));
        assertFalse(message.contains("title"));
    }

    @Test
    void 중복_요청은_이유를_그대로_알려준다() {
        ResponseEntity<ApiResponse<Void>> response = new GlobalExceptionHandler()
                .handleDuplicateRequest(new DuplicateRequestException("이미 신고한 대상입니다."));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("이미 신고한 대상입니다.", response.getBody().getMessage());
    }
}
