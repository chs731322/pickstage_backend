package com.pickstage.backend.global.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    // 별도 캐시 라이브러리(Redis, Caffeine 등) 없이 단순 인메모리 캐시로 충분한 규모라
    // ConcurrentMapCacheManager 사용. 등록/수정/삭제 시 @CacheEvict로 즉시 무효화되므로
    // TTL 없이도 최신 데이터가 보장됨.
    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("notices", "faqs", "faqDetail");
    }
}
