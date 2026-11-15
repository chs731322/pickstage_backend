package com.pickstage.backend.global.exception;

// 이미 처리된 요청을 다시 보낸 경우 (이미 신고함, 이미 차단함 등).
// DataIntegrityViolationException은 응답 문구가 일반 문구로 바뀌어서, 사용자에게 이유를 알려주려고 따로 만들었다.
public class DuplicateRequestException extends RuntimeException {

    public DuplicateRequestException(String message) {
        super(message);
    }
}
