package com.pickstage.backend.global.exception;

// 외부 서비스(Spotify 등)를 지금 쓸 수 없을 때 (설정 누락, 호출 한도 초과 등). 사용자에게 보여줄 수 있는 문장을 담는다.
public class ExternalServiceUnavailableException extends RuntimeException {

    public ExternalServiceUnavailableException(String message) {
        super(message);
    }
}
