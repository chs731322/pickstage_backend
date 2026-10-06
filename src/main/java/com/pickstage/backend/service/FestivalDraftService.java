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
    private static final Set<String> GENRES = Set.of("rock", "indie_band", "general_music", "exception");
    private static final List<String> REGIONS = List.of("서울", "경기", "인천", "부산", "대구", "대전", "광주", "울산",
            "세종", "강원", "충북", "충남", "전북", "전남", "경북", "경남", "제주");

    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final VenueRepository venueRepository;
    private final FestivalRepository festivalRepository;
    private final ArtistMatchService artistMatchService;

    public FestivalDraftService(GeminiService geminiService, ObjectMapper objectMapper,
            UserRepository userRepository, VenueRepository venueRepository,
            FestivalRepository festivalRepository, ArtistMatchService artistMatchService) {
        this.geminiService = geminiService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.venueRepository = venueRepository;
        this.festivalRepository = festivalRepository;
        this.artistMatchService = artistMatchService;
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

        String json = geminiService.generateJson(
                buildPrompt(hasText ? request.getText() : null, hasImage),
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
        List<VenueCandidate> venueCandidates = findVenueCandidates(draft.getVenueName(), draft.getRegion());
        // 이름이 정확히 일치하는(점수 100 이상) 공연장이 있으면 자동 선택
        Long venueId = (!venueCandidates.isEmpty() && venueCandidates.get(0).getScore() >= 100)
                ? venueCandidates.get(0).getId() : null;
        draft = Draft.builder()
                .title(draft.getTitle()).description(draft.getDescription())
                .venueId(venueId).venueName(draft.getVenueName()).address(draft.getAddress())
                .region(draft.getRegion()).startDate(draft.getStartDate()).endDate(draft.getEndDate())
                .genre(draft.getGenre()).tickets(draft.getTickets()).lineup(draft.getLineup())
                .build();

        if (draft.getVenueName() != null && venueCandidates.isEmpty()) {
            warnings.add("일치하는 공연장을 찾지 못했어요. 공연장 이름과 주소를 확인해 주세요.");
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

    private String buildPrompt(String text, boolean hasImage) {
        StringBuilder sb = new StringBuilder();
        sb.append("너는 공연·페스티벌 정보를 정리하는 도우미야. 아래 자료에서 공연 등록에 필요한 정보를 추출해 JSON으로 답해줘.\n");
        sb.append("오늘 날짜는 ").append(LocalDate.now()).append("이야. 연도가 적혀 있지 않으면 오늘 이후로 가장 가까운 날짜로 해석해.\n\n");
        sb.append("규칙:\n");
        sb.append("- 자료에 실제로 적힌 내용만 쓰고, 없는 정보는 절대 지어내지 말고 null로 둬. (특히 날짜, 가격, 링크)\n");
        sb.append("- title은 공식 공연명(연도 포함 표기가 있으면 그대로)을 쓰고, description은 소개 문구를 900자 이내로 요약해.\n");
        sb.append("- startDate/endDate는 공연 전체의 첫날/마지막 날(YYYY-MM-DD). 하루짜리면 둘이 같아.\n");
        sb.append("- genre는 다음 중 하나: rock(록/메탈 중심), indie_band(인디/밴드/펑크 중심), general_music(아이돌·힙합·EDM·대중음악 등 종합), exception(재즈·클래식 등).\n");
        sb.append("- region은 장소가 있는 시/도의 짧은 이름이야.\n");
        sb.append("- tickets는 예매처(vendorName)별·권종(label)별로 나눠. price는 원 단위 숫자, openAt은 예매 오픈 일시(YYYY-MM-DDTHH:mm:ss).\n");
        sb.append("- lineup은 실제 출연하는 아티스트만, 적힌 순서대로. 자료에 적힌 표기를 그대로 쓰고 번역하거나 한글로 음역하지 마(영문 이름은 영문 그대로). 한글 이름과 영문 이름이 같이 적혀 있을 때만 한글 이름을 써. SNS 아이디, 소속, 팀·스테이지 이름, TBA 같은 안내 문구는 빼.\n");
        sb.append("  날짜별 라인업이면 performanceDate에 그 날짜를, 모르면 null을 넣어.\n\n");
        if (text != null) {
            sb.append("[자료 텍스트]\n").append(text).append("\n");
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
        props.put("genre", nullableEnum(List.of("rock", "indie_band", "general_music", "exception")));
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
        if (e.getLineup() != null) {
            for (ExtractedLineup l : e.getLineup()) {
                if (l.getArtistName() == null || l.getArtistName().isBlank()) {
                    continue;
                }
                lineup.add(new LineupRequest(l.getArtistName().trim(), parseDate(l.getPerformanceDate(), null, null)));
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
