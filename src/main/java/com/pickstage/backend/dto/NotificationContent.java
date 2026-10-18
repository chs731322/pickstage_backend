package com.pickstage.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationContent {
    private Long id;
    private String type;
    private String title;
    private String message;
    private Long festivalId;
    private String festivalTitle;
    private Long postId;

    @Getter(onMethod_ = @__(@JsonProperty("isRead")))
    private boolean isRead;

    private String sentAt;
}
