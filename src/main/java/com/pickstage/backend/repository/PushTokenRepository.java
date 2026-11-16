package com.pickstage.backend.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.entity.PushToken;

public interface PushTokenRepository extends JpaRepository<PushToken, Long> {

    Optional<PushToken> findByToken(String token);

    // 한 사용자의 기기들 (최근에 쓴 순)
    List<PushToken> findByUserIdOrderByUpdatedAtDesc(Long userId);

    // 푸시를 받기로 한 기기들 (여러 사용자 한꺼번에)
    List<PushToken> findByUserIdInAndEnabledTrue(Collection<Long> userIds);

    // 회원 탈퇴·이용 정지 시 그 사용자의 기기를 모두 지운다
    @Transactional
    void deleteByUserId(Long userId);

    // 푸시 서버가 "더는 없는 기기"(앱 삭제 등)라고 알려준 토큰을 정리한다. 발송은 트랜잭션 밖에서 돌아서 직접 트랜잭션을 연다.
    @Transactional
    @Modifying
    @Query("DELETE FROM PushToken t WHERE t.token IN :tokens")
    void deleteAllByTokenIn(Collection<String> tokens);
}
