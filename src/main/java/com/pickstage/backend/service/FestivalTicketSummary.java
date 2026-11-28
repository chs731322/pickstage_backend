package com.pickstage.backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.pickstage.backend.entity.Festival;

// 티켓들을 보고 공연의 대표 값(티켓 오픈 시각, 예매 링크)을 맞춘다.
// 홈의 "티켓 오픈 일정"과 목록의 티켓 오픈 순서는 티켓이 아니라 공연의 대표 오픈 시각을 본다.
final class FestivalTicketSummary {

    private FestivalTicketSummary() {
    }

    // 티켓을 통째로 저장했을 때: 앞으로 열릴 것 중 가장 이른 시각, 모두 지났으면 첫 오픈 시각.
    // 오픈 시각이 하나도 없으면 원래 값을 그대로 둔다. 예매 링크는 비어 있을 때만 채운다.
    static void apply(Festival festival, List<LocalDateTime> openTimes, List<String> bookingUrls, LocalDateTime now) {
        List<LocalDateTime> times = openTimes.stream().filter(Objects::nonNull).sorted().toList();
        if (!times.isEmpty()) {
            festival.setTicketOpenDate(times.stream().filter(time -> !time.isBefore(now)).findFirst().orElse(times.get(0)));
        }
        fillBookingUrl(festival, bookingUrls);
    }

    // 티켓 하나를 더했을 때: 앞으로 열리는 시각이고, 지금 값이 없거나 지났거나 더 늦으면 바꾼다
    static void add(Festival festival, LocalDateTime openAt, String bookingUrl, LocalDateTime now) {
        LocalDateTime current = festival.getTicketOpenDate();
        if (openAt != null && !openAt.isBefore(now)
                && (current == null || current.isBefore(now) || openAt.isBefore(current))) {
            festival.setTicketOpenDate(openAt);
        }
        fillBookingUrl(festival, bookingUrl == null ? List.of() : List.of(bookingUrl));
    }

    private static void fillBookingUrl(Festival festival, List<String> bookingUrls) {
        if (festival.getBookingUrl() != null && !festival.getBookingUrl().isBlank()) {
            return;
        }
        bookingUrls.stream()
                .filter(url -> url != null && !url.isBlank())
                .findFirst()
                .ifPresent(festival::setBookingUrl);
    }
}
