package com.pickstage.backend;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableCaching
public class PickstageApplication {

	public static void main(String[] args) {
		// 서버(Railway)의 기본 시간대가 UTC라서 글·댓글 시각이 9시간 늦게 저장되던 문제를 막는다.
		// LocalDateTime.now()를 쓰는 모든 코드(저장 시각, 알림 스케줄러, 만료 판단)가 한국 시간 기준이 된다.
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
		SpringApplication.run(PickstageApplication.class, args);
	}

}
