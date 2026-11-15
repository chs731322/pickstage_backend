package com.pickstage.backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 사용자가 다른 사용자를 차단한 기록. 차단한 사람의 화면에서 상대가 쓴 글·댓글·후기를 숨긴다.
@Entity
@Table(name = "user_blocks")
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserBlock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long blockerId;
    private Long blockedId;
    private LocalDateTime createdAt;
}
