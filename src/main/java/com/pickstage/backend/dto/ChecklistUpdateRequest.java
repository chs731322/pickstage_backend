package com.pickstage.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChecklistUpdateRequest {

    private String content;
    private Boolean isChecked;
}
