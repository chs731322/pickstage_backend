package com.pickstage.backend.dto;

import java.util.List;

import lombok.Getter;
import lombok.NoArgsConstructor;

// "따미X신드럼"처럼 여러 아티스트가 하나로 등록된 경우, 경로의 아티스트를 names의 개별 아티스트들로 나눈다
@Getter
@NoArgsConstructor
public class AdminArtistSplitRequest {

    private List<String> names;
}
