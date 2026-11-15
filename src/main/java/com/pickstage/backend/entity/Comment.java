package com.pickstage.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "comments")
@Getter @Setter @Builder @AllArgsConstructor @NoArgsConstructor
public class Comment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long postId;
    private Long userId;
    private Long parentId;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
    // 신고가 쌓여 임시로 숨긴 시각
    private LocalDateTime hiddenAt;
}