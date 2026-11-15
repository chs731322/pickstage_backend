package com.pickstage.backend.dto;

import com.pickstage.backend.entity.Report;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReportCreateRequest {

    @NotNull(message = "신고 대상 종류를 선택해주세요.")
    private Report.TargetType targetType;

    @NotNull(message = "신고 대상을 지정해주세요.")
    private Long targetId;

    @NotNull(message = "신고 사유를 선택해주세요.")
    private Report.Reason reason;

    @Size(max = 200, message = "자세한 설명은 200자 이내로 입력해주세요.")
    private String detail;
}
