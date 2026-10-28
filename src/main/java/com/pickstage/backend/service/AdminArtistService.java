package com.pickstage.backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.JpaSort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pickstage.backend.dto.AdminArtistMergeRequest;
import com.pickstage.backend.dto.AdminArtistRequest;
import com.pickstage.backend.dto.AdminArtistResponse;
import com.pickstage.backend.dto.AdminArtistSplitRequest;
import com.pickstage.backend.dto.PageResponse;
import com.pickstage.backend.entity.Artist;
import com.pickstage.backend.entity.FestivalArtist;
import com.pickstage.backend.entity.InterestArtist;
import com.pickstage.backend.entity.User;
import com.pickstage.backend.repository.ArtistRepository;
import com.pickstage.backend.repository.FestivalArtistRepository;
import com.pickstage.backend.repository.InterestArtistRepository;
import com.pickstage.backend.repository.UserRepository;

// 관리자 아티스트 관리: 목록·검색, 정보 수정, 삭제, 중복 아티스트 합치기
@Service
public class AdminArtistService {

    private static final int MAX_NAME_LENGTH = 100;
    private static final int MAX_URL_LENGTH = 1000;
    private static final Pattern HTTP_URL = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);

    private final UserRepository userRepository;
    private final ArtistRepository artistRepository;
    private final FestivalArtistRepository festivalArtistRepository;
    private final InterestArtistRepository interestArtistRepository;
    private final SpotifyService spotifyService;
    private final ArtistMatchService artistMatchService;

    public AdminArtistService(
            UserRepository userRepository,
            ArtistRepository artistRepository,
            FestivalArtistRepository festivalArtistRepository,
            InterestArtistRepository interestArtistRepository,
            SpotifyService spotifyService,
            ArtistMatchService artistMatchService) {
        this.userRepository = userRepository;
        this.artistRepository = artistRepository;
        this.festivalArtistRepository = festivalArtistRepository;
        this.interestArtistRepository = interestArtistRepository;
        this.spotifyService = spotifyService;
        this.artistMatchService = artistMatchService;
    }

    // Spotify에서 이 아티스트의 후보를 찾는다 (사진은 관리자가 후보를 보고 직접 고른다). query가 없으면 아티스트 이름으로 찾는다.
    @Transactional(readOnly = true)
    public List<SpotifyService.ArtistCandidate> findSpotifyCandidates(Long requesterId, Long artistId, String query) {
        requireAdmin(requesterId);
        Artist artist = findArtist(artistId);
        String keyword = query == null || query.isBlank() ? artist.getName() : query.trim();
        if (keyword.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("검색어는 " + MAX_NAME_LENGTH + "자 이하로 입력해 주세요.");
        }
        return spotifyService.searchArtists(keyword);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminArtistResponse> list(
            Long requesterId, String keyword, boolean unlinkedOnly, boolean noPhotoOnly, boolean noSpotifyOnly,
            String sort, int page, int size) {
        requireAdmin(requesterId);

        // LIKE 검색에서 %·_가 와일드카드로 쓰이지 않게 뺀다
        String cleaned = keyword == null ? "" : keyword.trim().toLowerCase().replace("%", "").replace("_", "");
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100), listSort(sort));
        var result = artistRepository.searchForAdmin(cleaned.isEmpty() ? null : cleaned, unlinkedOnly, noPhotoOnly,
                noSpotifyOnly, pageable);

        List<Long> ids = result.getContent().stream().map(Artist::getId).toList();
        Map<Long, Long> festivalCounts = toCountMap(ids.isEmpty() ? List.of() : festivalArtistRepository.countByArtistIds(ids));
        Map<Long, Long> interestCounts = toCountMap(ids.isEmpty() ? List.of() : interestArtistRepository.countByArtistIds(ids));

        return PageResponse.of(result.map(artist -> AdminArtistResponse.of(
                artist,
                festivalCounts.getOrDefault(artist.getId(), 0L),
                interestCounts.getOrDefault(artist.getId(), 0L))));
    }

    // 정렬: latest(최신 등록순, 기본) / festivals(출연 공연 많은 순) / interests(관심 등록 많은 순) / noPhoto(프로필 사진 없는 아티스트 먼저).
    // 값이 같으면 최근에 등록된 아티스트가 먼저 나온다.
    static Sort listSort(String sort) {
        Sort byNewest = Sort.by(Sort.Direction.DESC, "id");
        if ("festivals".equals(sort)) {
            return JpaSort.unsafe(Sort.Direction.DESC, "(SELECT COUNT(fa) FROM FestivalArtist fa WHERE fa.artist = a)").and(byNewest);
        }
        if ("interests".equals(sort)) {
            return JpaSort.unsafe(Sort.Direction.DESC, "(SELECT COUNT(ia) FROM InterestArtist ia WHERE ia.artistId = a.id)").and(byNewest);
        }
        if ("noPhoto".equals(sort)) {
            return JpaSort.unsafe(Sort.Direction.ASC,
                    "(CASE WHEN a.profileImageUrl IS NULL OR a.profileImageUrl = '' THEN 0 ELSE 1 END)").and(byNewest);
        }
        return byNewest;
    }

    @Transactional
    public AdminArtistResponse update(Long requesterId, Long artistId, AdminArtistRequest request) {
        requireAdmin(requesterId);
        Artist artist = findArtist(artistId);

        if (request.getName() != null) {
            String name = request.getName().trim();
            if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
                throw new IllegalArgumentException("이름은 1~" + MAX_NAME_LENGTH + "자로 입력해 주세요.");
            }
            String normalized = ArtistMatchService.normalize(name);
            if (artistRepository.findFirstByNormalizedName(normalized).filter(a -> !a.getId().equals(artistId)).isPresent()
                    || artistRepository.findFirstByName(name).filter(a -> !a.getId().equals(artistId)).isPresent()) {
                throw new IllegalArgumentException("같은 이름의 아티스트가 이미 있어요. 중복이면 '합치기'를 사용해 주세요.");
            }
            artist.rename(name, normalized);
        }

        artist.updateDetails(
                textOrKeep(request.getEnglishName(), artist.getEnglishName(), MAX_NAME_LENGTH, "영문명"),
                textOrKeep(request.getArtistType(), artist.getArtistType(), 20, "유형"),
                textOrKeep(request.getGenre(), artist.getGenrePrimary(), 50, "장르"),
                urlOrKeep(request.getProfileImage(), artist.getProfileImageUrl(), "프로필 이미지"),
                urlOrKeep(request.getOfficialUrl(), artist.getOfficialUrl(), "공식 사이트"),
                urlOrKeep(request.getInstagramUrl(), artist.getInstagramUrl(), "인스타그램"),
                urlOrKeep(request.getSpotifyUrl(), artist.getSpotifyUrl(), "스포티파이"));

        artistRepository.save(artist);
        return AdminArtistResponse.of(artist,
                festivalArtistRepository.countByArtistId(artistId),
                interestCountOf(artistId));
    }

    // 공연 라인업에 연결된 아티스트는 지우지 않는다 (먼저 라인업에서 빼거나 다른 아티스트로 합친다)
    @Transactional
    public void delete(Long requesterId, Long artistId) {
        requireAdmin(requesterId);
        Artist artist = findArtist(artistId);

        long festivals = festivalArtistRepository.countByArtistId(artistId);
        if (festivals > 0) {
            throw new IllegalArgumentException(
                    festivals + "개 공연의 라인업에 연결돼 있어 삭제할 수 없어요. 라인업에서 먼저 빼거나 다른 아티스트로 합쳐 주세요.");
        }

        interestArtistRepository.deleteByArtistId(artistId);
        artistRepository.deleteAliasesByArtistId(artistId);
        artistRepository.delete(artist);
    }

    // 중복 아티스트 합치기: sourceId의 공연 출연·관심 등록·별칭을 target으로 옮기고 source는 지운다
    @Transactional
    public AdminArtistResponse merge(Long requesterId, Long sourceId, AdminArtistMergeRequest request) {
        requireAdmin(requesterId);
        Long targetId = request.getTargetId();
        if (targetId == null || targetId.equals(sourceId)) {
            throw new IllegalArgumentException("합칠 대상 아티스트를 다시 선택해 주세요.");
        }
        Artist source = findArtist(sourceId);
        Artist target = findArtist(targetId);

        festivalArtistRepository.deleteDuplicatesBeforeMerge(sourceId, targetId);
        festivalArtistRepository.moveToArtist(sourceId, targetId);
        interestArtistRepository.deleteDuplicatesBeforeMerge(sourceId, targetId);
        interestArtistRepository.moveToArtist(sourceId, targetId);
        artistRepository.moveAliases(sourceId, targetId);

        // 합쳐지는 쪽의 이름은 별칭으로 남겨서, 앞으로 라인업에 그 표기가 들어와도 대상 아티스트로 연결되게 한다
        String alias = source.getName() == null ? "" : source.getName().trim();
        String normalizedAlias = ArtistMatchService.normalize(alias);
        if (!normalizedAlias.isEmpty()
                && !normalizedAlias.equals(target.getNormalizedName())
                && artistRepository.countAlias(targetId, normalizedAlias) == 0) {
            artistRepository.addAlias(targetId, alias.length() > MAX_NAME_LENGTH ? alias.substring(0, MAX_NAME_LENGTH) : alias,
                    normalizedAlias);
        }

        target.fillMissingFrom(source);
        artistRepository.save(target);
        artistRepository.deleteAliasesByArtistId(sourceId);
        artistRepository.delete(source);

        return AdminArtistResponse.of(target,
                festivalArtistRepository.countByArtistId(targetId),
                interestCountOf(targetId));
    }

    // 분리하기: "따미X신드럼"처럼 여럿이 한 아티스트로 등록된 경우, 이름마다 아티스트(없으면 새로 만든다)를 찾아
    // 원래 아티스트의 공연 출연과 관심 등록을 각각에 복사한 뒤 원래 아티스트는 지운다.
    @Transactional
    public List<AdminArtistResponse> split(Long requesterId, Long artistId, AdminArtistSplitRequest request) {
        requireAdmin(requesterId);
        Artist source = findArtist(artistId);

        // 이름 정리: 공백 제거, 중복(정규화 기준) 제거
        Map<String, String> uniqueNames = new LinkedHashMap<>();
        for (String raw : request.getNames() == null ? List.<String>of() : request.getNames()) {
            String name = raw == null ? "" : raw.trim();
            if (name.isEmpty()) {
                continue;
            }
            if (name.length() > MAX_NAME_LENGTH) {
                throw new IllegalArgumentException("이름은 " + MAX_NAME_LENGTH + "자 이하로 입력해 주세요.");
            }
            uniqueNames.putIfAbsent(ArtistMatchService.normalize(name), name);
        }
        if (uniqueNames.size() < 2 || uniqueNames.size() > 6) {
            throw new IllegalArgumentException("나눌 이름을 2~6개 입력해 주세요.");
        }

        List<Artist> parts = new ArrayList<>();
        for (String name : uniqueNames.values()) {
            Artist part = artistMatchService.findOrCreate(name);
            if (part.getId().equals(artistId)) {
                throw new IllegalArgumentException("'" + name + "'은(는) 나누려는 아티스트와 같은 이름이에요. 이름을 다시 확인해 주세요.");
            }
            if (parts.stream().noneMatch(p -> p.getId().equals(part.getId()))) {
                parts.add(part);
            }
        }
        if (parts.size() < 2) {
            throw new IllegalArgumentException("서로 다른 아티스트 2명 이상으로 나눠야 해요.");
        }

        // 공연 출연: 각 아티스트가 아직 그 공연에 없으면 같은 날짜·순서로 추가한다
        for (FestivalArtist link : festivalArtistRepository.findByArtistIdWithFestival(artistId)) {
            for (Artist part : parts) {
                if (!festivalArtistRepository.existsLink(link.getFestival().getId(), part.getId())) {
                    festivalArtistRepository.save(FestivalArtist.builder()
                            .festival(link.getFestival())
                            .artist(part)
                            .isHeadliner(link.isHeadliner())
                            .performanceDate(link.getPerformanceDate())
                            .billingOrder(link.getBillingOrder())
                            .source("admin")
                            .build());
                }
            }
        }

        // 관심 등록: 관심으로 담은 회원은 나뉜 아티스트 모두를 관심으로 갖게 한다
        for (InterestArtist interest : interestArtistRepository.findByArtistId(artistId)) {
            for (Artist part : parts) {
                if (!interestArtistRepository.existsByUserIdAndArtistId(interest.getUserId(), part.getId())) {
                    interestArtistRepository.save(InterestArtist.builder()
                            .userId(interest.getUserId())
                            .artistId(part.getId())
                            .createdAt(LocalDateTime.now())
                            .build());
                }
            }
        }

        festivalArtistRepository.deleteByArtistId(artistId);
        interestArtistRepository.deleteByArtistId(artistId);
        artistRepository.deleteAliasesByArtistId(artistId);
        artistRepository.delete(source);

        return parts.stream()
                .map(part -> AdminArtistResponse.of(part,
                        festivalArtistRepository.countByArtistId(part.getId()),
                        interestCountOf(part.getId())))
                .toList();
    }

    private long interestCountOf(Long artistId) {
        return toCountMap(interestArtistRepository.countByArtistIds(List.of(artistId))).getOrDefault(artistId, 0L);
    }

    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : rows) {
            counts.put(((Number) row[0]).longValue(), ((Number) row[1]).longValue());
        }
        return counts;
    }

    // null이면 그대로 두고, 빈 문자열이면 비운다
    private String textOrKeep(String value, String current, int maxLength, String label) {
        if (value == null) {
            return current;
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(label + "은(는) " + maxLength + "자 이하로 입력해 주세요.");
        }
        return trimmed.isEmpty() ? null : trimmed;
    }

    // 주소는 http(s)만 받는다 (javascript: 같은 주소가 링크로 쓰이는 것을 막는다)
    private String urlOrKeep(String value, String current, String label) {
        if (value == null) {
            return current;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_URL_LENGTH || !HTTP_URL.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(label + " 주소는 http:// 또는 https://로 시작하는 올바른 주소여야 해요.");
        }
        return trimmed;
    }

    private Artist findArtist(Long artistId) {
        return artistRepository.findById(artistId)
                .orElseThrow(() -> new IllegalStateException("존재하지 않는 아티스트입니다."));
    }

    private void requireAdmin(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("사용자를 찾을 수 없습니다."));
        if (!user.getRole().equals(User.Role.admin)) {
            throw new AccessDeniedException("관리자 권한이 없습니다.");
        }
    }
}
