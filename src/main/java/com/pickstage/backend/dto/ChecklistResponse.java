package com.pickstage.backend.dto;

import com.pickstage.backend.entity.Checklist;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChecklistResponse {

    private Long id;
    private String content;
    private Boolean isChecked;
    private Boolean isCustom;
    private int sortOrder;

    public static ChecklistResponse from(Checklist checklist) {
        return ChecklistResponse.builder()
                .id(checklist.getId())
                .content(checklist.getContent())
                .isChecked(checklist.getIsChecked())
                .isCustom(checklist.getIsCustom())
                .sortOrder(checklist.getSortOrder())
                .build();
    }
}
