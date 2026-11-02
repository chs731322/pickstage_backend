package com.pickstage.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter 
@NoArgsConstructor 
public class AdminFAQRequest {
    private String question;
    private String answer;
    private String category;
}
