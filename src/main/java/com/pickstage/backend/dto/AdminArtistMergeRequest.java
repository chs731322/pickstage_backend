package com.pickstage.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

// 이 아티스트(경로의 id)를 targetId 아티스트로 합친다
@Getter
@NoArgsConstructor
public class AdminArtistMergeRequest {

    private Long targetId;
}
