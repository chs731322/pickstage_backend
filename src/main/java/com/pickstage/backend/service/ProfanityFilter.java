package com.pickstage.backend.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

// 글·댓글·후기·닉네임에 욕설이나 혐오 표현이 들어 있는지 검사한다.
// 단어 목록은 resources/profanity.txt (금지 단어, 맨 앞 !는 허용 단어).
// 띄어쓰기는 그대로 두고 점·밑줄 같은 기호만 지워서 비교한다 ("시.발" → "시발").
// 띄어쓰기까지 지우면 "이시 발표"가 "이시발표"가 되어 멀쩡한 글이 걸리기 때문이다.
@Component
public class ProfanityFilter {

    public static final String MESSAGE = "부적절한 표현이 포함되어 있어요. 표현을 바꿔서 다시 작성해주세요.";

    private static final String WORD_LIST = "/profanity.txt";

    private final List<String> banned;
    private final List<String> allowed;

    public ProfanityFilter() {
        List<String> bannedWords = new ArrayList<>();
        List<String> allowedWords = new ArrayList<>();
        loadWordList(bannedWords, allowedWords);
        this.banned = bannedWords;
        this.allowed = allowedWords;
    }

    // 테스트에서 단어 목록을 직접 넣을 때 쓴다
    ProfanityFilter(List<String> banned, List<String> allowed) {
        this.banned = banned.stream().map(ProfanityFilter::normalize).toList();
        this.allowed = allowed.stream().map(ProfanityFilter::normalize).toList();
    }

    public boolean containsProfanity(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = normalize(text);
        for (String word : allowed) {
            normalized = normalized.replace(word, " ");
        }
        for (String word : banned) {
            if (normalized.contains(word)) {
                return true;
            }
        }
        return false;
    }

    // 하나라도 걸리면 400 (IllegalArgumentException)으로 거절한다. null은 건너뛴다.
    public void assertClean(String... texts) {
        for (String text : texts) {
            if (containsProfanity(text)) {
                throw new IllegalArgumentException(MESSAGE);
            }
        }
    }

    // 소문자로 바꾸고, 글자 사이에 끼워 넣은 기호(. _ - * ~ · 등)를 지운다. 띄어쓰기는 남긴다.
    private static String normalize(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[\\p{Punct}·ㆍ…~]+", "");
    }

    private void loadWordList(List<String> bannedWords, List<String> allowedWords) {
        try (InputStream in = ProfanityFilter.class.getResourceAsStream(WORD_LIST)) {
            if (in == null) {
                throw new IllegalStateException("금칙어 목록(" + WORD_LIST + ")을 찾을 수 없습니다.");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String word = line.trim();
                    if (word.isEmpty() || word.startsWith("#")) {
                        continue;
                    }
                    if (word.startsWith("!")) {
                        allowedWords.add(normalize(word.substring(1)));
                    } else {
                        bannedWords.add(normalize(word));
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("금칙어 목록을 읽지 못했습니다.", e);
        }
    }
}
