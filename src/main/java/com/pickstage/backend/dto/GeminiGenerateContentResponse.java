package com.pickstage.backend.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Getter @Setter @NoArgsConstructor
public class GeminiGenerateContentResponse {
    private List<Candidate> candidates;

    @Getter @Setter @NoArgsConstructor
    public static class Candidate {
        private Content content;
    }

    @Getter @Setter @NoArgsConstructor
    public static class Content {
        private List<Part> parts;
    }

    @Getter @Setter @NoArgsConstructor
    public static class Part {
        private String text;
    }
}
