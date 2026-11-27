package com.pickstage.backend.service;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.XMLConstants;
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

// KOPIS(공연예술통합전산망) 공연 API에서 우리 공연과 같은 공연을 찾아 출연진·가격·예매처 링크를 가져온다.
// 예매 사이트를 직접 긁지 않고 공식 API만 쓴다. 키가 없거나 실패하면 "못 찾음"으로 처리한다.
@Service
public class KopisPerformanceService {

    private static final Logger log = LoggerFactory.getLogger(KopisPerformanceService.class);
    private static final String BASE_URL = "http://kopis.or.kr/openApi/restful";
    private static final DateTimeFormatter REQUEST_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter RESPONSE_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    // 목록 조회는 한 번에 31일까지만 된다
    private static final int MAX_RANGE_DAYS = 30;
    // "1일권 110,000원, 2일권 160,000원" 에서 (이름, 가격) 을 차례로 꺼낸다
    private static final Pattern PRICE = Pattern.compile("(.*?)([0-9]{1,3}(?:,[0-9]{3})+|[0-9]+)\\s*원\\s*,?");

    private final RestClient restClient;

    @Value("${kopis.api-key:}")
    private String apiKey;

    public KopisPerformanceService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public record Summary(String id, String title, LocalDate start, LocalDate end, String venueName) {
    }

    public record Price(String label, Integer amount) {
    }

    public record Vendor(String name, String url) {
    }

    public record Performance(String id, String title, List<String> cast, List<Price> prices, List<Vendor> vendors) {
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    // 이름과 기간이 맞는 공연을 찾는다. 확실하지 않으면 비운다 (엉뚱한 공연의 정보를 제안하지 않도록).
    public Optional<Performance> find(String title, LocalDate start, LocalDate end) {
        if (!isConfigured() || title == null || start == null) {
            return Optional.empty();
        }
        LocalDate last = end == null || end.isBefore(start) ? start : end;
        LocalDate until = last.isAfter(start.plusDays(MAX_RANGE_DAYS)) ? start.plusDays(MAX_RANGE_DAYS) : last;
        try {
            for (String keyword : searchKeywords(title)) {
                List<Summary> found = parseList(get("/pblprfr", "stdate", start.format(REQUEST_DATE),
                        "eddate", until.format(REQUEST_DATE), "cpage", "1", "rows", "20", "shprfnm", keyword));
                Optional<Summary> match = pickMatch(title, start, last, found);
                if (match.isPresent()) {
                    return parseDetail(get("/pblprfr/" + match.get().id()));
                }
            }
        } catch (Exception e) {
            // 예외 메시지에 요청 주소(인증키 포함)가 들어갈 수 있어 키는 가려서 남긴다
            String message = String.valueOf(e.getMessage()).replace(apiKey, "***");
            log.warn("KOPIS 공연 조회에 실패했어요 (title={}): {} {}", title, e.getClass().getSimpleName(), message);
        }
        return Optional.empty();
    }

    private String get(String path, String... queryPairs) {
        StringBuilder uri = new StringBuilder(BASE_URL).append(path).append("?service={service}");
        List<Object> vars = new ArrayList<>();
        vars.add(apiKey);
        for (int i = 0; i + 1 < queryPairs.length; i += 2) {
            uri.append('&').append(queryPairs[i]).append("={v").append(i / 2).append('}');
            vars.add(queryPairs[i + 1]);
        }
        byte[] body = restClient.get().uri(uri.toString(), vars.toArray()).retrieve().body(byte[].class);
        return body == null ? null : new String(body, StandardCharsets.UTF_8);
    }

    // ---- 순수 로직 (테스트 가능) ----

    // "2026 데이브레이크 루시 산보 연말콘서트 〈WINTER CITY〉" -> 괄호·연도를 뺀 말, 앞 두 단어, 첫 단어
    static List<String> searchKeywords(String title) {
        String base = title.replaceAll("[\\[(〈《<【][^\\])〉》>】]*[\\])〉》>】]", " ")
                .replaceAll("(?<!\\d)20\\d{2}(?!\\d)", " ")
                .replaceAll("['\"‘’“”:：\\-–]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (base.isEmpty()) {
            base = title.trim();
        }
        String[] words = base.split(" ");
        Set<String> keywords = new LinkedHashSet<>();
        keywords.add(base);
        if (words.length > 2) {
            keywords.add(words[0] + " " + words[1]);
        }
        if (words[0].length() >= 2) {
            keywords.add(words[0]);
        }
        return List.copyOf(keywords);
    }

    // 기간이 겹치고, 연도·기호를 뺀 이름이 같거나 한쪽이 다른 쪽을 포함해야 같은 공연으로 본다
    static Optional<Summary> pickMatch(String title, LocalDate start, LocalDate end, List<Summary> candidates) {
        String target = compact(title);
        return candidates.stream()
                .filter(c -> c.start() != null && c.end() != null && !c.start().isAfter(end) && !c.end().isBefore(start))
                .filter(c -> {
                    String name = compact(c.title());
                    return name.length() >= 4 && target.length() >= 4 && (name.contains(target) || target.contains(name));
                })
                .findFirst();
    }

    private static String compact(String title) {
        if (title == null) {
            return "";
        }
        // KOPIS 제목 끝의 지역 표시 "[서울]" 은 비교에서 뺀다
        return title.replaceAll("\\[[^\\]]{1,6}\\]\\s*$", "")
                .replaceAll("(?<!\\d)20\\d{2}(?!\\d)", "")
                .replaceAll("[^\\p{IsAlphabetic}\\p{IsDigit}]", "")
                .toLowerCase();
    }

    static List<Summary> parseList(String xml) throws Exception {
        List<Summary> result = new ArrayList<>();
        NodeList dbs = parse(xml).getElementsByTagName("db");
        for (int i = 0; i < dbs.getLength(); i++) {
            Element db = (Element) dbs.item(i);
            result.add(new Summary(text(db, "mt20id"), text(db, "prfnm"),
                    date(text(db, "prfpdfrom")), date(text(db, "prfpdto")), text(db, "fcltynm")));
        }
        return result;
    }

    static Optional<Performance> parseDetail(String xml) throws Exception {
        NodeList dbs = parse(xml).getElementsByTagName("db");
        if (dbs.getLength() == 0) {
            return Optional.empty();
        }
        Element db = (Element) dbs.item(0);
        List<Vendor> vendors = new ArrayList<>();
        NodeList relates = db.getElementsByTagName("relate");
        for (int i = 0; i < relates.getLength(); i++) {
            Element relate = (Element) relates.item(i);
            String url = text(relate, "relateurl");
            if (url.startsWith("http")) {
                vendors.add(new Vendor(text(relate, "relatenm"), url));
            }
        }
        return Optional.of(new Performance(text(db, "mt20id"), text(db, "prfnm"),
                parseCast(text(db, "prfcast")), parsePrices(text(db, "pcseguidance")), vendors));
    }

    // "혁오, 터치드 등" -> [혁오, 터치드]
    static List<String> parseCast(String raw) {
        Set<String> names = new LinkedHashSet<>();
        for (String part : raw.split("[,，]")) {
            String name = part.trim().replaceFirst("\\s+등$", "").trim();
            if (!name.isEmpty() && !name.equals("등") && !name.equals("-")) {
                names.add(name);
            }
        }
        return List.copyOf(names);
    }

    static List<Price> parsePrices(String raw) {
        List<Price> prices = new ArrayList<>();
        Matcher matcher = PRICE.matcher(raw == null ? "" : raw);
        while (matcher.find()) {
            String label = matcher.group(1).replaceAll("[:：]\\s*$", "").trim();
            String digits = matcher.group(2).replace(",", "");
            if (digits.length() <= 8) {
                prices.add(new Price(label.isEmpty() ? "일반" : label, Integer.valueOf(digits)));
            }
        }
        return prices;
    }

    private static LocalDate date(String value) {
        try {
            return LocalDate.parse(value, RESPONSE_DATE);
        } catch (Exception e) {
            return null;
        }
    }

    // 외부 개체·DTD를 막은 안전한 XML 파서
    private static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml == null ? "<dbs/>" : xml)));
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 || nodes.item(0).getTextContent() == null ? "" : nodes.item(0).getTextContent().trim();
    }
}
