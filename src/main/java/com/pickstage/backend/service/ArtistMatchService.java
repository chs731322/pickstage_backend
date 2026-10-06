package com.pickstage.backend.service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.entity.Artist;
import com.pickstage.backend.repository.ArtistRepository;

// 라인업의 아티스트 이름을 기존 artists와 매칭하고, 없으면 새로 만든다.
@Service
public class ArtistMatchService {

    private static final int MAX_NAME_LENGTH = 100;

    private final ArtistRepository artistRepository;

    public ArtistMatchService(ArtistRepository artistRepository) {
        this.artistRepository = artistRepository;
    }

    // 공백 제거 + 소문자 (artists.normalized_name과 같은 규칙)
    public static String normalize(String name) {
        if (name == null) {
            return "";
        }
        String n = Normalizer.normalize(name, Normalizer.Form.NFKC)
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.ROOT);
        return n.length() > MAX_NAME_LENGTH ? n.substring(0, MAX_NAME_LENGTH) : n;
    }

    // 기존 아티스트 찾기. "PITTA(강형호)"처럼 괄호가 붙은 표기는 전체 -> 괄호 앞 -> 괄호 안 순서로 시도
    @Transactional(readOnly = true)
    public Optional<Artist> find(String name) {
        Optional<Artist> result = findExact(name);
        if (result.isPresent() || name == null || !name.contains("(")) {
            return result;
        }
        String before = name.substring(0, name.indexOf('(')).trim();
        result = findExact(before);
        if (result.isPresent()) {
            return result;
        }
        int close = name.indexOf(')', name.indexOf('('));
        if (close > name.indexOf('(') + 1) {
            return findExact(name.substring(name.indexOf('(') + 1, close).trim());
        }
        return Optional.empty();
    }

    // 정규화 이름 -> 별칭 -> 영문명 순서
    private Optional<Artist> findExact(String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            return Optional.empty();
        }

        Optional<Artist> byName = artistRepository.findFirstByNormalizedName(normalized);
        if (byName.isPresent()) {
            return byName;
        }

        Optional<Artist> byAlias = artistRepository.findByAlias(normalized);
        if (byAlias.isPresent()) {
            return byAlias;
        }

        List<Artist> byEnglish = artistRepository.findByNormalizedEnglishName(normalized);
        return byEnglish.isEmpty() ? Optional.empty() : Optional.of(byEnglish.get(0));
    }

    // 있으면 기존 아티스트, 없으면 새로 생성
    @Transactional
    public Artist findOrCreate(String name) {
        Optional<Artist> existing = find(name);
        if (existing.isPresent()) {
            return existing.get();
        }

        String trimmed = name.trim();
        if (trimmed.length() > MAX_NAME_LENGTH) {
            trimmed = trimmed.substring(0, MAX_NAME_LENGTH);
        }

        // name 컬럼도 UNIQUE라, 정규화 결과는 달라도 이름이 같은 행이 있으면 그것을 쓴다
        Optional<Artist> sameName = artistRepository.findFirstByName(trimmed);
        if (sameName.isPresent()) {
            return sameName.get();
        }

        return artistRepository.save(Artist.builder()
                .name(trimmed)
                .normalizedName(normalize(trimmed))
                .artistType("band")
                .build());
    }
}
