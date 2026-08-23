package com.pickstage.backend.entity;

import java.time.LocalDateTime;

import jakarta.annotation.Generated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "checklists")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Checklist {

    @Id
    @GeneratedValue(strategy = jakarta.persistence.GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String content;
    private Boolean isChecked;
    private Boolean isCustom;
    private int sortOrder;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    
}
