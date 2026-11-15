package com.pickstage.backend.dto;

import com.pickstage.backend.entity.Report;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdminReportResolveRequest {

    @NotNull(message = "신고 대상 종류를 선택해주세요.")
    private Report.TargetType targetType;

    @NotNull(message = "신고 대상을 지정해주세요.")
    private Long targetId;

    // DELETE: 문제가 있어 삭제 / DISMISS: 문제 없음 (숨김 해제)
    @NotNull(message = "처리 방법을 선택해주세요.")
    private Action action;

    public enum Action {
        DELETE, DISMISS
    }
}
