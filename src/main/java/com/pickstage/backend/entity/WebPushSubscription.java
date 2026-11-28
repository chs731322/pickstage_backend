package com.pickstage.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 브라우저(웹 푸시) 구독 1건
@Entity
@Table(name = "web_push_subscriptions")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WebPushSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private String endpoint;

    @Column(name = "p256dh")
    private String p256dh;

    private String auth;

    private LocalDateTime createdAt;
}
