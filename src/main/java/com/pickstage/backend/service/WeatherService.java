package com.pickstage.backend.service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.pickstage.backend.dto.WeatherSummary;
import com.pickstage.backend.global.exception.ExternalServiceUnavailableException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// 기상청 예보(공공데이터포털)를 서버에서 대신 불러온다. 서울 격자 기준.
// - 단기예보: 오늘~모레. 우선 사용한다.
// - 중기예보: 발표일 기준 3~10일 뒤 예보만 제공한다. 단기예보를 못 가져올 때 대신 쓴다.
// 인증키(KMA_API_KEY)를 브라우저·앱에 넣지 않으려고 프록시로 만들었고, 결과는 30분 동안 서버에 보관한다.
@Service
public class WeatherService {

    private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

    private static final String MID_BASE_URL = "https://apis.data.go.kr/1360000/MidFcstInfoService";
    private static final String SHORT_BASE_URL = "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0";
    // 서울 격자 좌표 (단기예보)
    private static final int SHORT_NX = 60;
    private static final int SHORT_NY = 127;
    // 중기 기온 조회용 지점 코드(서울), 육상예보 구역 코드(서울·인천·경기)
    private static final String TEMPERATURE_REG_ID = "11B10101";
    private static final String LAND_FORECAST_REG_ID = "11B00000";

    static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    // 이 시간 안의 결과는 다시 묻지 않고 그대로 돌려준다
    static final Duration CACHE_TTL = Duration.ofMinutes(30);
    // 새로 못 가져올 때, 이 시간 안의 옛 결과는 대신 보여준다
    static final Duration STALE_LIMIT = Duration.ofHours(6);
    // 가져오기에 실패하면 이 시간 동안은 기상청을 다시 부르지 않는다 (요청이 몰려도 기상청에 부담을 주지 않도록)
    static final Duration RETRY_AFTER_FAILURE = Duration.ofMinutes(1);

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Map<Integer, String> SKY_LABELS = Map.of(1, "맑음", 3, "구름많음", 4, "흐림");
    private static final Map<Integer, String> PTY_LABELS = Map.of(1, "비", 2, "비/눈", 3, "눈", 4, "소나기");
    private static final String UNAVAILABLE_MESSAGE = "날씨 정보를 불러오지 못했어요. 잠시 후 다시 시도해주세요.";

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final Clock clock;

    @Value("${kma.api-key:}")
    private String serviceKey;

    private WeatherSummary cached;
    private Instant cachedAt = Instant.EPOCH;
    private Instant lastFailureAt = Instant.EPOCH;

    @Autowired
    public WeatherService(ObjectMapper objectMapper) {
        this(objectMapper, Clock.system(SEOUL));
    }

    // 테스트에서 시계를 바꿔 쓸 때
    WeatherService(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5000);
        factory.setReadTimeout(8000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    // 날씨 요약 (30분 동안 보관). 한꺼번에 여러 요청이 와도 기상청에는 한 번만 물어본다.
    public synchronized WeatherSummary getSummary() {
        Instant now = clock.instant();
        if (cached != null && now.isBefore(cachedAt.plus(CACHE_TTL))) {
            return cached;
        }
        // 방금 실패했다면 곧바로 다시 부르지 않는다
        if (now.isBefore(lastFailureAt.plus(RETRY_AFTER_FAILURE))) {
            return staleOrThrow(now);
        }
        try {
            WeatherSummary fresh = loadSummary();
            cached = fresh;
            cachedAt = now;
            return fresh;
        } catch (ExternalServiceUnavailableException e) {
            lastFailureAt = now;
            if (serviceKeyMissing()) {
                // 설정 문제는 옛 결과가 있어도 그대로 알린다
                throw e;
            }
            return staleOrThrow(now);
        }
    }

    // 새로 못 가져왔을 때: 6시간 안의 옛 결과가 있으면 그것을, 없으면 오류를 돌려준다
    private WeatherSummary staleOrThrow(Instant now) {
        if (cached != null && now.isBefore(cachedAt.plus(STALE_LIMIT))) {
            log.warn("날씨를 새로 가져오지 못해 이전 결과를 대신 보여줍니다.");
            return cached;
        }
        throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
    }

    // 오늘 날씨(단기예보)를 우선 쓰고, 불러오지 못하면 가까운 며칠 뒤 예보(중기예보)로 대신한다.
    WeatherSummary loadSummary() {
        if (serviceKeyMissing()) {
            throw new ExternalServiceUnavailableException("날씨 연동이 아직 설정되지 않았어요. (서버의 KMA_API_KEY 필요)");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        try {
            return loadShortTerm(now);
        } catch (RuntimeException shortTermFailure) {
            log.warn("단기예보를 가져오지 못해 중기예보로 대신합니다: {}", reason(shortTermFailure));
            try {
                return loadMidTerm(now);
            } catch (RuntimeException midTermFailure) {
                log.warn("중기예보도 가져오지 못했습니다: {}", reason(midTermFailure));
                throw new ExternalServiceUnavailableException(UNAVAILABLE_MESSAGE);
            }
        }
    }

    // 로그에 남길 실패 원인. fetch 가 만든 문구만 그대로 쓰고, 그 밖의 예외는 메시지에 주소(인증키)가 섞일 수 있어 종류만 남긴다.
    private static String reason(RuntimeException failure) {
        return failure instanceof IllegalStateException ? failure.getMessage() : failure.getClass().getSimpleName();
    }

    private boolean serviceKeyMissing() {
        return serviceKey == null || serviceKey.isBlank();
    }

    // ---- 단기예보 ----

    private WeatherSummary loadShortTerm(LocalDateTime now) {
        String[] base = shortTermBase(now);
        String url = SHORT_BASE_URL + "/getVilageFcst?serviceKey=" + encodedKey()
                + "&pageNo=1&numOfRows=1000&dataType=JSON&base_date=" + base[0] + "&base_time=" + base[1]
                + "&nx=" + SHORT_NX + "&ny=" + SHORT_NY;
        return buildShortTermSummary(items(fetch(url)), now.toLocalDate());
    }

    // 오늘 최저/최고 기온이 모두 들어 있는 02시 발표분을 쓴다 (02:10 이전에는 어제 23시 발표분)
    static String[] shortTermBase(LocalDateTime now) {
        int minutes = now.getHour() * 60 + now.getMinute();
        if (minutes >= 2 * 60 + 10) {
            return new String[] { now.toLocalDate().format(YMD), "0200" };
        }
        return new String[] { now.toLocalDate().minusDays(1).format(YMD), "2300" };
    }

    static WeatherSummary buildShortTermSummary(JsonNode rows, LocalDate today) {
        WeatherSummary.Day todayDay = summarizeDay(rows, today, 0);
        if (todayDay == null) {
            throw new IllegalStateException("오늘 예보 데이터가 아직 준비되지 않았어요.");
        }
        List<WeatherSummary.Day> upcoming = new ArrayList<>();
        for (int offset = 1; offset <= 2; offset++) {
            WeatherSummary.Day day = summarizeDay(rows, today.plusDays(offset), offset);
            if (day != null) {
                upcoming.add(day);
            }
        }
        return new WeatherSummary(todayDay.date(), 0, todayDay.tempMin(), todayDay.tempMax(), todayDay.sky(),
                todayDay.isRain(), "short", upcoming);
    }

    // 하루치 예보(최저/최고 기온, 하늘 상태)를 정리한다. 기온을 알 수 없으면 null.
    static WeatherSummary.Day summarizeDay(JsonNode rows, LocalDate date, int dayOffset) {
        String ymd = date.format(YMD);
        List<Double> temps = new ArrayList<>();
        List<Double> tmn = new ArrayList<>();
        List<Double> tmx = new ArrayList<>();
        List<Integer> rainCodes = new ArrayList<>();
        List<Integer> skyCodes = new ArrayList<>();

        for (JsonNode row : rows) {
            if (!ymd.equals(row.path("fcstDate").asString(""))) {
                continue;
            }
            String category = row.path("category").asString("");
            String time = row.path("fcstTime").asString("");
            double value = parseNumber(row.path("fcstValue").asString(""));
            if (Double.isNaN(value)) {
                continue;
            }
            switch (category) {
                case "TMP" -> temps.add(value);
                case "TMN" -> tmn.add(value);
                case "TMX" -> tmx.add(value);
                // 낮 시간(09~18시) 기준으로 비/눈이 있으면 그것을, 없으면 가장 흐린 하늘 상태를 보여준다
                case "PTY" -> {
                    if (time.compareTo("0900") >= 0 && time.compareTo("1800") <= 0 && value > 0) {
                        rainCodes.add((int) value);
                    }
                }
                case "SKY" -> {
                    if (time.compareTo("0900") >= 0 && time.compareTo("1800") <= 0) {
                        skyCodes.add((int) value);
                    }
                }
                default -> {
                }
            }
        }

        Double min = !tmn.isEmpty() ? tmn.get(0) : (temps.isEmpty() ? null : temps.stream().min(Double::compare).get());
        Double max = !tmx.isEmpty() ? tmx.get(0) : (temps.isEmpty() ? null : temps.stream().max(Double::compare).get());
        if (min == null || max == null) {
            return null;
        }

        String sky = null;
        if (!rainCodes.isEmpty()) {
            sky = PTY_LABELS.get(rainCodes.stream().max(Integer::compare).get());
        } else if (!skyCodes.isEmpty()) {
            sky = SKY_LABELS.get(skyCodes.stream().max(Integer::compare).get());
        }
        return new WeatherSummary.Day(date, dayOffset, (int) Math.round(min), (int) Math.round(max), sky,
                !rainCodes.isEmpty());
    }

    // ---- 중기예보 ----

    private WeatherSummary loadMidTerm(LocalDateTime now) {
        String tmFc = latestAnnouncementTime(now);
        JsonNode ta = firstItem(fetch(midUrl("getMidTa", TEMPERATURE_REG_ID, tmFc)));
        JsonNode land = firstItem(fetch(midUrl("getMidLandFcst", LAND_FORECAST_REG_ID, tmFc)));
        return buildMidTermSummary(ta, land, now.toLocalDate());
    }

    private String midUrl(String operation, String regId, String tmFc) {
        return MID_BASE_URL + "/" + operation + "?serviceKey=" + encodedKey()
                + "&pageNo=1&numOfRows=10&dataType=JSON&regId=" + regId + "&tmFc=" + tmFc;
    }

    // 중기예보는 매일 06:00 / 18:00에 발표된다. 발표 후 실제 반영까지 지연이 있어 30분 여유를 둔다.
    static String latestAnnouncementTime(LocalDateTime now) {
        int hour = now.getMinute() >= 30 ? now.getHour() : now.getHour() - 1;
        LocalDate date = now.toLocalDate();
        int announceHour;
        if (hour >= 18) {
            announceHour = 18;
        } else if (hour >= 6) {
            announceHour = 6;
        } else {
            announceHour = 18;
            date = date.minusDays(1);
        }
        return date.format(YMD) + String.format("%02d00", announceHour);
    }

    // 중기예보에서 실제로 값이 있는 가장 가까운 날(3~10일 뒤)의 기온과 하늘 상태
    static WeatherSummary buildMidTermSummary(JsonNode ta, JsonNode land, LocalDate today) {
        int day = -1;
        for (int d = 3; d <= 10; d++) {
            if (ta.has("taMin" + d)) {
                day = d;
                break;
            }
        }
        if (day == -1) {
            throw new IllegalStateException("예보 데이터가 아직 준비되지 않았어요.");
        }
        String sky = firstNonBlank(
                land.path("wf" + day + "Am").asString(""),
                land.path("wf" + day + "Pm").asString(""),
                land.path("wf" + day).asString(""));
        return new WeatherSummary(today.plusDays(day), day,
                (int) Math.round(ta.path("taMin" + day).asDouble(0)),
                (int) Math.round(ta.path("taMax" + day).asDouble(0)),
                sky, false, "mid", List.of());
    }

    // ---- 공통 ----

    private JsonNode fetch(String url) {
        try {
            String body = restClient.get().uri(URI.create(url)).retrieve().body(String.class);
            JsonNode root = objectMapper.readTree(body);
            String resultCode = root.path("response").path("header").path("resultCode").asString("");
            if (!"00".equals(resultCode)) {
                throw new IllegalStateException("기상청이 오류를 돌려줬어요. (resultCode=" + resultCode + ")");
            }
            return root.path("response").path("body").path("items").path("item");
        } catch (RestClientResponseException e) {
            // 주소에 인증키가 들어 있어서 예외 메시지는 로그에 남기지 않는다
            throw new IllegalStateException("기상청 호출 실패: HTTP " + e.getStatusCode().value());
        } catch (IllegalStateException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalStateException("기상청 호출 실패: " + e.getClass().getSimpleName() + " / " + rootCause(e));
        }
    }

    // 연결이 안 된 것인지(연결 시간 초과·거부), 응답이 늦은 것인지 구별할 수 있게 가장 안쪽 원인을 적는다.
    // 연결 관련 예외의 메시지에는 주소가 들어가지 않는다. 다른 예외는 종류만 적는다.
    static String rootCause(Throwable e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        boolean network = root instanceof java.net.SocketTimeoutException || root instanceof java.net.ConnectException;
        return root.getClass().getSimpleName() + (network ? " (" + root.getMessage() + ")" : "");
    }

    private static JsonNode items(JsonNode item) {
        if (!item.isArray() || item.isEmpty()) {
            throw new IllegalStateException("예보 데이터가 비어 있어요.");
        }
        return item;
    }

    private static JsonNode firstItem(JsonNode item) {
        JsonNode first = item.isArray() ? item.get(0) : item;
        if (first == null || first.isMissingNode() || first.isNull()) {
            throw new IllegalStateException("예보 데이터가 비어 있어요.");
        }
        return first;
    }

    // 공공데이터포털은 "Encoding" 키(이미 %로 인코딩된 값)와 "Decoding" 키 두 가지를 준다. 어느 쪽을 넣어도 동작하게 맞춘다.
    String encodedKey() {
        String key = serviceKey.trim();
        return key.contains("%") ? key : URLEncoder.encode(key, StandardCharsets.UTF_8);
    }

    private static double parseNumber(String text) {
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    // 테스트에서 키를 넣을 때
    void setServiceKeyForTest(String key) {
        this.serviceKey = key;
    }
}
