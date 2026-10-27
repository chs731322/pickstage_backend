package com.pickstage.backend.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.dto.FestivalDraftRequest;
import com.pickstage.backend.dto.FestivalDraftResponse;
import com.pickstage.backend.dto.FestivalDraftResponse.ArtistMatch;
import com.pickstage.backend.dto.FestivalDraftResponse.Draft;
import com.pickstage.backend.dto.FestivalDraftResponse.DuplicateCandidate;
import com.pickstage.backend.dto.FestivalDraftResponse.TicketDraft;
import com.pickstage.backend.dto.FestivalDraftResponse.VenueCandidate;
import com.pickstage.backend.dto.LineupRequest;
import com.pickstage.backend.entity.Artist;
import com.pickstage.backend.entity.Festival;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.entity.Venue;
import com.pickstage.backend.repository.FestivalRepository;
import com.pickstage.backend.repository.UserRepository;
import com.pickstage.backend.repository.VenueRepository;

import lombok.Getter;
import lombok.NoArgsConstructor;
import tools.jackson.databind.ObjectMapper;

// 공고 텍스트/포스터 이미지에서 공연 등록 초안을 만든다. (저장은 하지 않고, 관리자가 검토한 뒤 등록 API로 저장)
@Service
public class FestivalDraftService {

    private static final int MAX_TEXT_LENGTH = 30_000;
    private static final int MAX_IMAGE_BASE64_LENGTH = 7_000_000; // 약 5MB
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final List<String> GENRE_LIST = List.of("rock", "indie_band", "hiphop", "jazz", "edm", "general_music", "exception");
    private static final Set<String> GENRES = Set.copyOf(GENRE_LIST);
    private static final List<String> REGIONS = List.of("서울", "경기", "인천", "부산", "대구", "대전", "광주", "울산",
            "세종", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주");

    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final VenueRepository venueRepository;
    private final FestivalRepository festivalRepository;
    private final ArtistMatchService artistMatchService;
    private final KopisVenueService kopisVenueService;
    private final TicketPageService ticketPageService;

    public FestivalDraftService(GeminiService geminiService, ObjectMapper objectMapper,
            UserRepository userRepository, VenueRepository venueRepository,
            FestivalRepository festivalRepository, ArtistMatchService artistMatchService,
            KopisVenueService kopisVenueService, TicketPageService ticketPageService) {
        this.geminiService = geminiService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.venueRepository = venueRepository;
        this.festivalRepository = festivalRepository;
        this.artistMatchService = artistMatchService;
        this.kopisVenueService = kopisVenueService;
        this.ticketPageService = ticketPageService;
    }

    @Transactional(readOnly = true)
    public FestivalDraftResponse createDraft(Long requesterId, FestivalDraftRequest request) {
        requireAdmin(requesterId);

        boolean hasText = request.getText() != null && !request.getText().isBlank();
        boolean hasImage = request.getImageBase64() != null && !request.getImageBase64().isBlank();
        if (!hasText && !hasImage) {
            throw new IllegalArgumentException("공고 텍스트나 포스터 이미지 중 하나는 필요합니다.");
        }
        if (hasText && request.getText().length() > MAX_TEXT_LENGTH) {
            throw new IllegalArgumentException("텍스트가 너무 깁니다. (최대 " + MAX_TEXT_LENGTH + "자)");
        }
        if (hasImage) {
            if (request.getImageMimeType() == null || !IMAGE_TYPES.contains(request.getImageMimeType())) {
                throw new IllegalArgumentException("이미지는 JPEG, PNG, WEBP만 지원합니다.");
            }
            if (request.getImageBase64().length() > MAX_IMAGE_BASE64_LENGTH) {
                throw new IllegalArgumentException("이미지가 너무 큽니다. (최대 약 5MB)");
            }
        }

        // 붙여넣은 글에 예매처 주소가 있으면 그 페이지의 권종·가격·오픈 일정도 읽어서 함께 넘긴다
        List<TicketPageService.TicketPage> ticketPages = hasText ? ticketPageService.fetchAll(request.getText()) : List.of();

        String json = geminiService.generateJson(
                buildPrompt(hasText ? request.getText() : null, hasImage, ticketPages),
                buildSchema(),
                hasImage ? request.getImageBase64() : null,
                hasImage ? request.getImageMimeType() : null);

        Extracted extracted = objectMapper.readValue(json, Extracted.class);
        String extractedTitle = extracted.getTitle();
        if (extractedTitle == null || extractedTitle.isBlank() || extractedTitle.trim().equalsIgnoreCase("null")) {
            throw new IllegalArgumentException("공연 정보를 찾지 못했어요. 공고 내용이나 포스터를 확인해 주세요.");
        }
        List<String> warnings = new ArrayList<>();

        Draft draft = toDraft(extracted, warnings);
        draft = withTicketFixes(draft, ticketPages, warnings);
        List<VenueCandidate> venueCandidates = findVenueCandidates(draft.getVenueName(), draft.getRegion());
        // 이름이 정확히 일치하는(점수 100 이상) 공연장이 있으면 자동 선택
        Long venueId = (!venueCandidates.isEmpty() && venueCandidates.get(0).getScore() >= 100)
                ? venueCandidates.get(0).getId() : null;

        // AI가 공연장 이름을 주소 칸에 그대로 옮겨 적는 경우가 있어서, 그런 값은 비운다
        String address = draft.getAddress();
        if (address != null && (address.isBlank() || looksLikeVenueName(address, draft.getVenueName()))) {
            address = null;
        }
        // 자료에 주소가 없으면 공연장 목록에 있는 주소로 채운다
        if (address == null && !venueCandidates.isEmpty() && venueCandidates.get(0).getScore() >= 70
                && venueCandidates.get(0).getAddress() != null && !venueCandidates.get(0).getAddress().isBlank()) {
            address = venueCandidates.get(0).getAddress();
            warnings.add("자료에 주소가 없어 공연장 목록의 주소(" + venueCandidates.get(0).getName() + ")로 채웠어요. 맞는지 확인해 주세요.");
        }

        draft = Draft.builder()
                .title(draft.getTitle()).description(draft.getDescription())
                .venueId(venueId).venueName(draft.getVenueName()).address(address)
                .region(draft.getRegion()).startDate(draft.getStartDate()).endDate(draft.getEndDate())
                .genre(draft.getGenre()).tickets(draft.getTickets()).lineup(draft.getLineup())
                .build();

        if (draft.getVenueName() != null && venueCandidates.isEmpty()) {
            // 우리 공연장 목록에 없으면 KOPIS에서 찾아 본다. 등록하면 이 정보로 새 공연장이 자동으로 만들어진다.
            Optional<KopisVenueService.KopisVenue> kopis = kopisVenueService.find(draft.getVenueName());
            if (kopis.isPresent()) {
                KopisVenueService.KopisVenue k = kopis.get();
                warnings.add("공연장 '" + draft.getVenueName() + "'은(는) 아직 등록돼 있지 않아요. 등록하면 KOPIS의 '" + k.getName()
                        + "'(" + (k.getAddress() == null ? "주소 없음" : k.getAddress()) + ") 정보로 새 공연장이 자동으로 만들어져요. 다른 곳이면 공연장 이름을 고쳐 주세요.");
                draft = Draft.builder()
                        .title(draft.getTitle()).description(draft.getDescription())
                        .venueId(draft.getVenueId()).venueName(draft.getVenueName())
                        .address(draft.getAddress() == null ? k.getAddress() : draft.getAddress())
                        .region(draft.getRegion() == null ? k.getRegion() : draft.getRegion())
                        .startDate(draft.getStartDate()).endDate(draft.getEndDate())
                        .genre(draft.getGenre()).tickets(draft.getTickets()).lineup(draft.getLineup())
                        .build();
            } else {
                warnings.add("일치하는 공연장을 찾지 못했어요" + (kopisVenueService.isConfigured() ? "(KOPIS에서도 찾지 못했어요)" : "")
                        + ". 공연장 이름과 주소를 확인해 주세요. 이대로 등록하면 공연장 이름만 저장돼요.");
            }
        }

        List<DuplicateCandidate> duplicates = findDuplicates(draft.getTitle(), draft.getStartDate(), draft.getEndDate(), draft.getVenueId());
        if (!duplicates.isEmpty()) {
            warnings.add("이미 비슷한 이름의 공연이 등록돼 있어요. 중복인지 확인해 주세요.");
        }

        List<ArtistMatch> matches = new ArrayList<>();
        for (LineupRequest entry : draft.getLineup()) {
            Optional<Artist> found = artistMatchService.find(entry.getArtistName());
            matches.add(ArtistMatch.builder()
                    .name(entry.getArtistName())
                    .artistId(found.map(Artist::getId).orElse(null))
                    .matchedName(found.map(Artist::getName).orElse(null))
                    .newArtist(found.isEmpty())
                    .build());
        }
        long newCount = matches.stream().filter(ArtistMatch::isNewArtist).count();
        if (newCount > 0) {
            warnings.add("라인업 중 " + newCount + "명은 새 아티스트로 등록돼요. 이름 표기를 확인해 주세요.");
        }

        return FestivalDraftResponse.builder()
                .draft(draft)
                .venueCandidates(venueCandidates)
                .duplicates(duplicates)
                .artistMatches(matches)
                .warnings(warnings)
                .build();
    }

    // ---- 프롬프트 / 스키마 ----

    private String buildPrompt(String text, boolean hasImage, List<TicketPageService.TicketPage> ticketPages) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 공연·페스티벌 정보를 정리하는 도우미야. 아래 자료에서 공연 등록에 필요한 정보를 추출해 JSON으로 답해줘.\n");
        sb.append("오늘 날짜는 ").append(LocalDate.now()).append("이야. 연도가 적혀 있지 않으면 오늘 이후로 가장 가까운 날짜로 해석해.\n\n");
        sb.append("규칙:\n");
        sb.append("- 자료에 실제로 적힌 내용만 쓰고, 없는 정보는 절대 지어내지 말고 null로 둬. (특히 날짜, 가격, 링크)\n");
        sb.append("- title은 공식 공연명(연도 포함 표기가 있으면 그대로)을 쓰고, description은 소개 문구를 900자 이내로 요약해.\n");
        sb.append("- startDate/endDate는 공연 전체의 첫날/마지막 날(YYYY-MM-DD). 하루짜리면 둘이 같아.\n");
        sb.append("- genre는 다음 중 하나: rock(록/메탈 중심), indie_band(인디/밴드/펑크 중심), hiphop(힙합/랩 중심), jazz(재즈 중심), edm(EDM·DJ 중심), general_music(아이돌·대중음악 등 종합), exception(클래식 등 그 외).\n");
        sb.append("- venueName은 공연장 이름이고, address는 자료에 도로명·지번 주소가 실제로 적혀 있을 때만 써. 장소 이름을 address에 넣지 말고, 주소가 없으면 address는 null로 둬.\n");
        sb.append("- region은 장소가 있는 시/도의 짧은 이름이야.\n");
        sb.append("- tickets는 예매처(vendorName)별·권종(label)별로 나눠. label은 '1일권', '2일권', 'R석'처럼 권종 이름이고 권종 구분이 없으면 '일반'이라고 써. price는 원 단위 숫자, openAt은 예매 오픈 일시(YYYY-MM-DDTHH:mm:ss).\n");
        sb.append("  예매처 페이지가 함께 주어지면 그 안의 권종·가격·예매 오픈(선예매/일반) 일정과 라인업을 tickets와 lineup에 반영하고, bookingUrl에는 그 페이지 주소를 써. 가격이 여러 개면 권종별로 따로 넣어.\n");
        sb.append("- lineup은 실제 출연하는 아티스트만, 적힌 순서대로. 자료에 적힌 표기를 그대로 쓰고 번역하거나 한글로 음역하지 마(영문 이름은 영문 그대로). 한글 이름과 영문 이름이 같이 적혀 있을 때만 한글 이름을 써. SNS 아이디, 소속, 팀·스테이지 이름, TBA 같은 안내 문구는 빼.\n");
        sb.append("  lineup 배열의 항목 하나에는 아티스트 한 팀(한 명) 이름만 넣어. 쉼표·슬래시·줄바꿈·점으로 나열된 이름은 반드시 각각 별도 항목으로 나눠서 넣고, 나열된 문장 전체를 한 항목에 넣지 마.\n");
        sb.append("  '1차 라인업', '2차 라인업', '라인업', '출연진' 같은 제목·안내 문구는 아티스트 이름이 아니니까 빼.\n");
        sb.append("  날짜별 라인업이면 performanceDate에 그 날짜를, 모르면 null을 넣어.\n\n");
        if (text != null) {
            sb.append("[자료 텍스트]\n").append(text).append("\n");
        }
        for (TicketPageService.TicketPage page : ticketPages) {
            sb.append("\n[예매처 페이지: ").append(page.getVendorName()).append(" ").append(page.getUrl()).append("]\n")
                    .append(page.getText()).append("\n");
        }
        if (hasImage) {
            sb.append("\n[첨부된 이미지도 같은 공연의 자료야. 포스터나 라인업 이미지에 적힌 글자를 읽어서 함께 반영해.]\n");
        }
        return sb.toString();
    }

    private static Map<String, Object> nullable(String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", type);
        m.put("nullable", true);
        return m;
    }

    private static Map<String, Object> nullableEnum(List<String> values) {
        Map<String, Object> m = nullable("string");
        m.put("enum", values);
        return m;
    }

    private static Map<String, Object> obj(Map<String, Object> props, List<String> required) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "object");
        m.put("properties", props);
        m.put("required", required);
        return m;
    }

    private static Map<String, Object> arrayOf(Map<String, Object> items) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "array");
        m.put("items", items);
        return m;
    }

    private Map<String, Object> buildSchema() {
        Map<String, Object> ticketProps = new LinkedHashMap<>();
        ticketProps.put("label", nullable("string"));
        ticketProps.put("vendorName", nullable("string"));
        ticketProps.put("price", nullable("integer"));
        ticketProps.put("bookingUrl", nullable("string"));
        ticketProps.put("openAt", nullable("string"));

        Map<String, Object> lineupProps = new LinkedHashMap<>();
        lineupProps.put("artistName", Map.of("type", "string"));
        lineupProps.put("performanceDate", nullable("string"));

        Map<String, Object> props = new LinkedHashMap<>();
        props.put("title", Map.of("type", "string"));
        props.put("description", nullable("string"));
        props.put("venueName", nullable("string"));
        props.put("address", nullable("string"));
        props.put("region", nullableEnum(REGIONS));
        props.put("startDate", nullable("string"));
        props.put("endDate", nullable("string"));
        props.put("genre", nullableEnum(GENRE_LIST));
        props.put("tickets", arrayOf(obj(ticketProps, List.of("label"))));
        props.put("lineup", arrayOf(obj(lineupProps, List.of("artistName"))));

        return obj(props, List.of("title", "tickets", "lineup"));
    }

    // ---- 추출 결과 -> 초안 ----

    private Draft toDraft(Extracted e, List<String> warnings) {
        LocalDate start = parseDate(e.getStartDate(), "시작일", warnings);
        LocalDate end = parseDate(e.getEndDate(), "종료일", warnings);
        if (start != null && end == null) {
            end = start;
        }
        if (start == null) {
            warnings.add("시작일을 찾지 못했어요. 직접 입력해 주세요.");
        } else if (end != null && end.isBefore(start)) {
            warnings.add("종료일이 시작일보다 빨라요. 날짜를 확인해 주세요.");
        } else if (start.isBefore(LocalDate.now())) {
            warnings.add("시작일이 이미 지난 날짜예요. 연도를 확인해 주세요.");
        }

        String genre = e.getGenre();
        if (genre != null && !GENRES.contains(genre)) {
            warnings.add("장르 값이 올바르지 않아 비워뒀어요.");
            genre = null;
        }
        String region = e.getRegion();
        if (region != null && !REGIONS.contains(region)) {
            region = null;
        }

        List<TicketDraft> tickets = new ArrayList<>();
        if (e.getTickets() != null) {
            for (ExtractedTicket t : e.getTickets()) {
                tickets.add(TicketDraft.builder()
                        .label(t.getLabel())
                        .vendorName(t.getVendorName())
                        .price(t.getPrice())
                        .bookingUrl(isHttpUrl(t.getBookingUrl()) ? t.getBookingUrl() : null)
                        .openAt(parseDateTime(t.getOpenAt()))
                        .build());
            }
        }

        List<LineupRequest> lineup = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        if (e.getLineup() != null) {
            for (ExtractedLineup l : e.getLineup()) {
                LocalDate performanceDate = parseDate(l.getPerformanceDate(), null, null);
                // AI가 "1차 라인업: A, B, C"처럼 나열 문장 전체를 한 항목에 넣어 줄 때가 있어서, 이름 단위로 나눈다
                for (String name : splitLineupName(l.getArtistName())) {
                    if (seen.add(name.toLowerCase() + "|" + performanceDate)) {
                        lineup.add(new LineupRequest(name, performanceDate));
                    }
                }
            }
        }

        return Draft.builder()
                .title(e.getTitle() == null ? null : e.getTitle().trim())
                .description(e.getDescription())
                .venueName(e.getVenueName())
                .address(e.getAddress())
                .region(region)
                .startDate(start)
                .endDate(end)
                .genre(genre)
                .tickets(tickets)
                .lineup(lineup)
                .build();
    }

    // 티켓이 조용히 사라지지 않도록: 권종이 비면 '일반', 예매처 이름·링크가 비면 예매처 페이지 주소에서 채우고,
    // AI가 티켓을 하나도 못 찾았어도 예매처 페이지가 있으면 링크가 담긴 티켓 한 줄을 남긴다.
    static Draft withTicketFixes(Draft draft, List<TicketPageService.TicketPage> pages, List<String> warnings) {
        List<TicketDraft> fixed = new ArrayList<>();
        for (TicketDraft t : draft.getTickets()) {
            String url = t.getBookingUrl();
            if (url == null && pages.size() == 1) {
                url = pages.get(0).getUrl();
            }
            String vendor = t.getVendorName();
            if ((vendor == null || vendor.isBlank()) && url != null) {
                vendor = TicketPageService.vendorNameOf(url);
            }
            String label = t.getLabel();
            if ((label == null || label.isBlank()) && (t.getPrice() != null || url != null)) {
                label = "일반";
            }
            fixed.add(TicketDraft.builder().label(label).vendorName(vendor).price(t.getPrice())
                    .bookingUrl(url).openAt(t.getOpenAt()).build());
        }
        if (fixed.isEmpty() && !pages.isEmpty()) {
            for (TicketPageService.TicketPage page : pages) {
                fixed.add(TicketDraft.builder().label("일반").vendorName(page.getVendorName()).bookingUrl(page.getUrl()).build());
            }
            warnings.add("예매처 페이지에서 권종·가격을 찾지 못해 예매 링크만 담았어요. 가격과 예매 오픈 일정을 직접 입력해 주세요.");
        }
        return Draft.builder()
                .title(draft.getTitle()).description(draft.getDescription())
                .venueId(draft.getVenueId()).venueName(draft.getVenueName())
                .address(draft.getAddress()).region(draft.getRegion())
                .startDate(draft.getStartDate()).endDate(draft.getEndDate())
                .genre(draft.getGenre()).tickets(fixed).lineup(draft.getLineup())
                .build();
    }

    private static final java.util.regex.Pattern LINEUP_LABEL = java.util.regex.Pattern.compile(
            "^\\s*(\\d+\\s*차\\s*)?(라인\\s*업|line\\s*-?\\s*up|출연\\s*진|출연\\s*아티스트|아티스트)\\s*[:：\\-–]?\\s*",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern LINEUP_SEPARATOR = java.util.regex.Pattern.compile("[,，、;；\\n\\r]+|\\s+/\\s+");
    // 타임테이블의 날짜 제목 줄("12/18 (금)", "DAY 1", "2026.12.19 SAT")은 아티스트 이름이 아니다
    private static final java.util.regex.Pattern LINEUP_DATE_HEADING = java.util.regex.Pattern.compile(
            "^(day\\s*\\d+|((\\d{4})[-./])?\\d{1,2}[-./]\\d{1,2}\\s*([(\\[]\\s*[^)\\]]{1,5}\\s*[)\\]]|[a-z]{3,9}\\.?)?)$",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    // "이름 (@인스타계정)" 꼬리표와 끝에 붙은 이모지(🆕 등)
    private static final java.util.regex.Pattern LINEUP_ACCOUNT_TAG = java.util.regex.Pattern.compile("\\s*[(（]\\s*@[^)）]*[)）]");
    private static final java.util.regex.Pattern LINEUP_TRAILING_SYMBOLS = java.util.regex.Pattern.compile("[\\s\\p{So}\\p{Cs}\\uFE0F]+$");
    private static final Set<String> LINEUP_FILLER = Set.of("등", "외", "tba", "tbd", "추후공개", "추후공지", "미정", "라인업");

    // 한 항목에 나열 문장이 통째로 들어온 경우를 아티스트 이름 단위로 나눈다.
    // 쉼표가 정확히 하나뿐이고 한글이 없는 이름(예: "Earth, Wind & Fire")은 한 팀 이름일 수 있어 나누지 않는다.
    static List<String> splitLineupName(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String text = LINEUP_LABEL.matcher(raw.replace('\u00A0', ' ').trim()).replaceFirst("");
        String[] parts = LINEUP_SEPARATOR.split(text);
        if (parts.length == 2 && !text.contains("\n") && !text.contains("/") && !containsHangul(text)) {
            parts = new String[] { text };
        }
        List<String> names = new ArrayList<>();
        for (String part : parts) {
            String name = part.replaceFirst("^[\\s•·\\-–*▶▷]+", "");
            name = LINEUP_TRAILING_SYMBOLS.matcher(LINEUP_ACCOUNT_TAG.matcher(name).replaceAll("")).replaceFirst("").trim();
            String filler = name.replaceAll("\\s+", "").toLowerCase();
            if (!name.isEmpty() && !LINEUP_FILLER.contains(filler) && !LINEUP_DATE_HEADING.matcher(name).matches()) {
                names.add(name);
            }
        }
        return names;
    }

    private static boolean containsHangul(String s) {
        return s.chars().anyMatch(c -> c >= 0xAC00 && c <= 0xD7A3);
    }

    private static LocalDate parseDate(String value, String label, List<String> warnings) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (DateTimeParseException ex) {
            if (warnings != null && label != null) {
                warnings.add(label + " 형식을 해석하지 못했어요: " + value);
            }
            return null;
        }
    }

    private static LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim().replace(' ', 'T');
        try {
            return LocalDateTime.parse(v);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDate.parse(v.length() >= 10 ? v.substring(0, 10) : v).atStartOfDay();
            } catch (DateTimeParseException ex2) {
                return null;
            }
        }
    }

    private static boolean isHttpUrl(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://"));
    }

    // ---- 공연장 / 중복 매칭 ----

    private static String norm(String s) {
        return s == null ? "" : ArtistMatchService.normalize(s).replaceAll("[\\p{Punct}·]", "");
    }

    // 주소 칸에 공연장 이름이 그대로(또는 숫자 없이 이름만) 들어간 경우
    private static boolean looksLikeVenueName(String address, String venueName) {
        String a = norm(address);
        String v = norm(venueName);
        if (a.isEmpty() || v.isEmpty()) {
            return false;
        }
        return a.equals(v) || v.contains(a) || (a.contains(v) && !a.matches(".*\\d.*"));
    }

    private List<VenueCandidate> findVenueCandidates(String venueName, String region) {
        String target = norm(venueName);
        if (target.isEmpty()) {
            return List.of();
        }
        List<VenueCandidate> result = new ArrayList<>();
        for (Venue v : venueRepository.findAll()) {
            String name = norm(v.getName());
            if (name.isEmpty()) {
                continue;
            }
            int score = 0;
            if (name.equals(target)) {
                score = 100;
            } else if (target.length() >= 3 && name.length() >= 3 && (name.contains(target) || target.contains(name))) {
                score = 70;
            }
            if (score > 0 && region != null && region.equals(v.getRegion())) {
                score += 5;
            }
            if (score > 0) {
                result.add(VenueCandidate.builder().id(v.getId()).name(v.getName()).address(v.getAddress()).score(score).build());
            }
        }
        result.sort(Comparator.comparingInt(VenueCandidate::getScore).reversed());
        return result.size() > 5 ? new ArrayList<>(result.subList(0, 5)) : result;
    }

    // 중복 후보: 제목이 비슷하거나, 제목이 달라도(예: 영문/한글 표기) 같은 날짜에 같은 공연장인 공연
    private List<DuplicateCandidate> findDuplicates(String title, LocalDate start, LocalDate end, Long venueId) {
        String target = norm(title);
        List<DuplicateCandidate> result = new ArrayList<>();
        for (Festival f : festivalRepository.findAll()) {
            String t = norm(f.getTitle());
            boolean similarTitle = target.length() >= 4 && t.length() >= 4
                    && (t.equals(target) || t.contains(target) || target.contains(t) || diceSimilarity(t, target) >= 0.7);
            boolean sameDates = start != null && start.equals(f.getStartDate()) && end != null && end.equals(f.getEndDate());
            boolean sameVenue = venueId != null && f.getVenue() != null && venueId.equals(f.getVenue().getId());
            if (similarTitle || (sameDates && sameVenue)) {
                result.add(DuplicateCandidate.builder().id(f.getId()).title(f.getTitle()).startDate(f.getStartDate()).build());
            }
            if (result.size() >= 3) {
                break;
            }
        }
        return result;
    }

    // 글자 2개씩 묶은 집합의 Dice 유사도 (단어 순서가 달라도 비슷한 제목을 찾기 위함)
    private static double diceSimilarity(String a, String b) {
        if (a.length() < 2 || b.length() < 2) {
            return 0;
        }
        java.util.Map<String, Integer> grams = new java.util.HashMap<>();
        for (int i = 0; i < a.length() - 1; i++) {
            grams.merge(a.substring(i, i + 2), 1, Integer::sum);
        }
        int overlap = 0;
        for (int i = 0; i < b.length() - 1; i++) {
            String g = b.substring(i, i + 2);
            Integer c = grams.get(g);
            if (c != null && c > 0) {
                grams.put(g, c - 1);
                overlap++;
            }
        }
        return (2.0 * overlap) / ((a.length() - 1) + (b.length() - 1));
    }

    private void requireAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));
        if (!user.getRole().equals(User.Role.admin)) {
            throw new AccessDeniedException("관리자 권한이 없습니다.");
        }
    }

    // ---- Gemini 응답 파싱용 ----

    @Getter
    @NoArgsConstructor
    static class Extracted {
        private String title;
        private String description;
        private String venueName;
        private String address;
        private String region;
        private String startDate;
        private String endDate;
        private String genre;
        private List<ExtractedTicket> tickets;
        private List<ExtractedLineup> lineup;
    }

    @Getter
    @NoArgsConstructor
    static class ExtractedTicket {
        private String label;
        private String vendorName;
        private Integer price;
        private String bookingUrl;
        private String openAt;
    }

    @Getter
    @NoArgsConstructor
    static class ExtractedLineup {
        private String artistName;
        private String performanceDate;
    }
}
