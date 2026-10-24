package com.pickstage.backend.service;

import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import lombok.Builder;
import lombok.Getter;

// 공연장이 우리 DB에 없을 때, 한국문화정보원 KOPIS 공연시설 API에서 이름으로 찾아 주소·좌표·수용 인원을 가져온다.
// KOPIS 인증키가 설정되어 있지 않거나 API가 실패하면 조용히 "못 찾음"으로 처리한다 (공연 등록은 그대로 진행된다).
@Service
public class KopisVenueService {

    private static final Logger log = LoggerFactory.getLogger(KopisVenueService.class);
    private static final String BASE_URL = "http://kopis.or.kr/openApi/restful";
    private static final int MAX_SEARCHES = 4;

    private final RestClient restClient;

    @Value("${kopis.api-key:}")
    private String apiKey;

    public KopisVenueService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    @Getter
    @Builder
    public static class KopisVenue {
        private final String name;
        private final String address;
        private final String region; // 서울, 경기 … (KOPIS의 시도 이름)
        private final BigDecimal latitude;
        private final BigDecimal longitude;
        private final Integer capacity;
    }

    @Getter
    @Builder
    static class Facility {
        private final String id;
        private final String name;
        private final String region;
    }

    // 공연장 이름으로 KOPIS에서 공연시설을 찾는다. 이름이 정확히 같은 시설을 우선하고, 못 찾으면 이름 끝 단어를 하나씩 줄여 다시 찾는다.
    public Optional<KopisVenue> find(String venueName) {
        if (!isConfigured() || venueName == null || venueName.isBlank()) {
            return Optional.empty();
        }
        try {
            List<String> keywords = searchKeywords(venueName);
            for (String keyword : keywords) {
                List<Facility> found = parseFacilities(get("/prfplc", "cpage", "1", "rows", "10", "shprfnmfct", keyword));
                Optional<Facility> best = pickBest(keyword, found);
                if (best.isPresent()) {
                    return fetchDetail(best.get());
                }
            }
        } catch (Exception e) {
            // 예외 메시지에 요청 주소(인증키 포함)가 들어갈 수 있어 키는 가려서 남긴다
            String message = String.valueOf(e.getMessage()).replace(apiKey, "***");
            log.warn("KOPIS 공연장 조회에 실패했어요 (venue={}): {} {}", venueName, e.getClass().getSimpleName(), message);
        }
        return Optional.empty();
    }

    private Optional<KopisVenue> fetchDetail(Facility facility) throws Exception {
        String xml = get("/prfplc/" + facility.getId());
        Element db = firstDb(xml);
        if (db == null) {
            return Optional.empty();
        }
        String name = text(db, "fcltynm");
        return Optional.of(KopisVenue.builder()
                .name(name.isBlank() ? facility.getName() : name)
                .address(blankToNull(text(db, "adres")))
                .region(blankToNull(facility.getRegion()))
                .latitude(toDecimal(text(db, "la")))
                .longitude(toDecimal(text(db, "lo")))
                .capacity(toInt(text(db, "seatscale")))
                .build());
    }

    private String get(String path, String... queryPairs) {
        StringBuilder uri = new StringBuilder(BASE_URL).append(path).append("?service={service}");
        List<Object> vars = new ArrayList<>();
        vars.add(apiKey);
        for (int i = 0; i + 1 < queryPairs.length; i += 2) {
            uri.append('&').append(queryPairs[i]).append("={v").append(i / 2).append('}');
            vars.add(queryPairs[i + 1]);
        }
        // 응답에 charset이 없어 String으로 받으면 한글이 깨지므로, 바이트로 받아 UTF-8로 읽는다
        byte[] body = restClient.get().uri(uri.toString(), vars.toArray()).retrieve().body(byte[].class);
        return body == null ? null : new String(body, java.nio.charset.StandardCharsets.UTF_8);
    }

    // ---- 순수 로직 (테스트 가능) ----

    // "킨텍스 제2전시장 7·8홀 [고양]" -> ["킨텍스 제2전시장 7·8홀", "킨텍스 제2전시장", "킨텍스"]
    static List<String> searchKeywords(String venueName) {
        String base = venueName.split("[\\(\\[]")[0].trim();
        if (base.isEmpty()) {
            base = venueName.trim();
        }
        List<String> keywords = new ArrayList<>();
        String[] words = base.split("\\s+");
        for (int n = words.length; n >= 1 && keywords.size() < MAX_SEARCHES; n--) {
            String keyword = String.join(" ", java.util.Arrays.copyOfRange(words, 0, n));
            // 한 글자짜리처럼 너무 짧은 검색어는 엉뚱한 시설이 잡히므로 쓰지 않는다
            if (keyword.replace(" ", "").length() >= 2 && !keywords.contains(keyword)) {
                keywords.add(keyword);
            }
        }
        return keywords;
    }

    // 이름이 정확히 같은 시설 > 이름이 서로 포함되는 시설(이름 길이가 가장 가까운 것). 없으면 비어 있다 (엉뚱한 시설을 넣지 않는다).
    static Optional<Facility> pickBest(String keyword, List<Facility> candidates) {
        String target = compact(keyword);
        for (Facility f : candidates) {
            if (compact(f.getName()).equals(target)) {
                return Optional.of(f);
            }
        }
        Facility best = null;
        int bestGap = Integer.MAX_VALUE;
        for (Facility f : candidates) {
            String name = compact(f.getName());
            if (name.length() >= 2 && (name.contains(target) || target.contains(name))) {
                int gap = Math.abs(name.length() - target.length());
                if (gap < bestGap) {
                    best = f;
                    bestGap = gap;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    static List<Facility> parseFacilities(String xml) throws Exception {
        List<Facility> result = new ArrayList<>();
        Document doc = parse(xml);
        NodeList dbs = doc.getElementsByTagName("db");
        for (int i = 0; i < dbs.getLength(); i++) {
            Element db = (Element) dbs.item(i);
            result.add(Facility.builder()
                    .id(text(db, "mt10id"))
                    .name(text(db, "fcltynm"))
                    .region(text(db, "sidonm"))
                    .build());
        }
        return result;
    }

    private static Element firstDb(String xml) throws Exception {
        NodeList dbs = parse(xml).getElementsByTagName("db");
        return dbs.getLength() == 0 ? null : (Element) dbs.item(0);
    }

    // 외부 개체·DTD를 막은 안전한 XML 파서
    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        DocumentBuilder builder = factory.newDocumentBuilder();
        return builder.parse(new InputSource(new StringReader(xml == null ? "<dbs/>" : xml)));
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 || nodes.item(0).getTextContent() == null ? "" : nodes.item(0).getTextContent().trim();
    }

    private static String compact(String s) {
        return s == null ? "" : s.replaceAll("[\\s·\\-_.,()\\[\\]]", "").toLowerCase();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    private static BigDecimal toDecimal(String s) {
        try {
            return s == null || s.isBlank() ? null : new BigDecimal(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toInt(String s) {
        String digits = s == null ? "" : s.replaceAll("[^0-9]", "");
        if (digits.isEmpty() || digits.length() > 9) {
            return null;
        }
        return Integer.valueOf(digits);
    }
}
