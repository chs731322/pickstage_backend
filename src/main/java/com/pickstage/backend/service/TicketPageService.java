package com.pickstage.backend.service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import lombok.Builder;
import lombok.Getter;

// AI 공연 등록 초안에서 붙여넣은 글에 예매처 주소가 있으면, 그 페이지의 글자(권종·가격·예매 오픈 일정·라인업)를 읽어 AI에 함께 넘긴다.
// 서버가 임의의 주소를 대신 열어 주는 일을 막기 위해, 알려진 예매처의 https 주소만 열고 리다이렉트는 따라가지 않는다.
@Service
public class TicketPageService {

    private static final Logger log = LoggerFactory.getLogger(TicketPageService.class);
    private static final int MAX_PAGES = 3;
    private static final int MAX_BYTES = 2 * 1024 * 1024;
    private static final int MAX_TEXT_CHARS = 12_000;
    private static final Pattern URL_PATTERN = Pattern.compile("https://[^\\s\"'<>)\\]]+");

    // 예매처 도메인 -> 화면에 보여줄 예매처 이름 (DB에 이미 쓰는 표기와 맞춘다)
    private static final Map<String, String> VENDORS = new LinkedHashMap<>();
    static {
        VENDORS.put("nol.yanolja.com", "NOL티켓");
        VENDORS.put("tickets.interpark.com", "NOL티켓");
        VENDORS.put("ticket.interpark.com", "NOL티켓");
        VENDORS.put("ticket.melon.com", "멜론티켓");
        VENDORS.put("ticketlink.co.kr", "티켓링크");
        VENDORS.put("ticket.yes24.com", "예스24");
        VENDORS.put("booking.naver.com", "네이버예약");
        VENDORS.put("2tm.co.kr", "2TM");
    }

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Getter
    @Builder
    public static class TicketPage {
        private final String url;
        private final String vendorName;
        private final String text; // 페이지 글자 (일부)
    }

    // 글에 들어 있는 예매처 페이지를 읽어 온다. 읽지 못한 주소는 건너뛴다.
    public List<TicketPage> fetchAll(String text) {
        List<TicketPage> pages = new ArrayList<>();
        for (String url : extractTicketUrls(text)) {
            try {
                String html = download(url);
                String pageText = htmlToText(html);
                if (!pageText.isBlank()) {
                    pages.add(TicketPage.builder().url(url).vendorName(vendorNameOf(url)).text(pageText).build());
                }
            } catch (Exception e) {
                log.warn("예매처 페이지를 읽지 못했어요 (url={}): {}", url, e.getClass().getSimpleName());
            }
        }
        return pages;
    }

    private String download(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("User-Agent", "Mozilla/5.0 (compatible; PickstageDraftBot/1.0)")
                .header("Accept", "text/html")
                .GET()
                .build();
        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        try (InputStream in = response.body()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                if (out.size() > MAX_BYTES) {
                    break; // 너무 큰 응답은 앞부분만 쓴다
                }
            }
            return out.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    // ---- 순수 로직 (테스트 가능) ----

    // 글에서 허용된 예매처의 https 주소만 중복 없이 최대 3개
    static List<String> extractTicketUrls(String text) {
        List<String> urls = new ArrayList<>();
        if (text == null) {
            return urls;
        }
        Matcher m = URL_PATTERN.matcher(text);
        while (m.find() && urls.size() < MAX_PAGES) {
            String url = m.group().replaceAll("[.,;]+$", "");
            if (isAllowedTicketUrl(url) && !urls.contains(url)) {
                urls.add(url);
            }
        }
        return urls;
    }

    static boolean isAllowedTicketUrl(String url) {
        try {
            URI uri = URI.create(url);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || uri.getHost() == null) {
                return false;
            }
            if (uri.getPort() != -1 && uri.getPort() != 443) {
                return false;
            }
            return vendorKeyOf(uri.getHost()) != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // 호스트가 허용 도메인(또는 그 하위 도메인)이면 해당 도메인 키
    private static String vendorKeyOf(String host) {
        String h = host.toLowerCase();
        for (String domain : VENDORS.keySet()) {
            if (h.equals(domain) || h.endsWith("." + domain)) {
                return domain;
            }
        }
        return null;
    }

    public static String vendorNameOf(String url) {
        try {
            String key = vendorKeyOf(URI.create(url).getHost());
            return key == null ? null : VENDORS.get(key);
        } catch (Exception e) {
            return null;
        }
    }

    // 스크립트·스타일을 빼고 태그를 줄바꿈으로 바꿔 사람이 읽는 글자만 남긴다
    static String htmlToText(String html) {
        if (html == null) {
            return "";
        }
        String text = html
                .replaceAll("(?is)<!--.*?-->", " ")
                .replaceAll("(?is)<(script|style|noscript|svg)[^>]*>.*?</\\1>", " ")
                .replaceAll("(?i)<br\\s*/?>|</(p|div|li|tr|h[1-6]|dt|dd|section)>", "\n")
                .replaceAll("(?s)<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&middot;", "·");
        text = text.replaceAll("[ \\t\\u00A0]+", " ").replaceAll("\\s*\\n\\s*", "\n").replaceAll("\\n{2,}", "\n").trim();
        return text.length() > MAX_TEXT_CHARS ? text.substring(0, MAX_TEXT_CHARS) : text;
    }
}
