package com.pickstage.backend.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SpotifyServiceTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void 검색_결과에서_후보를_읽고_320px에_가까운_사진을_고른다() throws Exception {
        JsonNode root = mapper.readTree("""
                {"artists":{"items":[
                  {"id":"a1","name":"10cm","external_urls":{"spotify":"https://open.spotify.com/artist/a1"},
                   "images":[{"url":"https://i.scdn.co/big","width":640},{"url":"https://i.scdn.co/mid","width":320},{"url":"https://i.scdn.co/small","width":160}]},
                  {"id":"a2","name":"No Image","images":[]},
                  {"id":"","name":"skip"}
                ]}}""");

        List<SpotifyService.ArtistCandidate> candidates = SpotifyService.parseCandidates(root);

        assertEquals(2, candidates.size());
        assertEquals("a1", candidates.get(0).getSpotifyId());
        assertEquals("https://i.scdn.co/mid", candidates.get(0).getImageUrl());
        assertEquals("https://open.spotify.com/artist/a1", candidates.get(0).getSpotifyUrl());
        assertNull(candidates.get(1).getImageUrl());
        assertNull(candidates.get(1).getSpotifyUrl());
    }

    @Test
    void Retry_After는_최소_1분_최대_7일로_맞춘다() {
        assertEquals(84661, SpotifyService.parseRetryAfter("84661"));
        assertEquals(60, SpotifyService.parseRetryAfter("1"));
        assertEquals(7 * 24 * 3600L, SpotifyService.parseRetryAfter("99999999"));
        assertEquals(3600, SpotifyService.parseRetryAfter(null));
        assertEquals(3600, SpotifyService.parseRetryAfter("abc"));
    }
}
