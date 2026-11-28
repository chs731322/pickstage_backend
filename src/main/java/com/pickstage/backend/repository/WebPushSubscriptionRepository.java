package com.pickstage.backend.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.entity.WebPushSubscription;

public interface WebPushSubscriptionRepository extends JpaRepository<WebPushSubscription, Long> {

    List<WebPushSubscription> findByUserIdIn(Collection<Long> userIds);

    @Transactional
    void deleteByEndpoint(String endpoint);
}
