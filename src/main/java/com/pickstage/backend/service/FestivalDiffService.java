package com.pickstage.backend.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import com.pickstage.backend.dto.FestivalDraftResponse.TicketDraft;
import com.pickstage.backend.entity.FestivalTicket;

public class FestivalDiffService {

    // artistId 가 null 이면 우리 DB에 아직 없는 아티스트
    public record ArtistCandidate(String name, Long artistId) {
    }

    public static List<ArtistCandidate> newArtists(Set<Long> currentIds, List<ArtistCandidate> extracted) {
        List<ArtistCandidate> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (ArtistCandidate candidate : extracted) {
            if (candidate.artistId() == null || !currentIds.contains(candidate.artistId())) {
                if (seen.add(ArtistMatchService.normalize(candidate.name())))
                    result.add(candidate);
            }
        }
        return result;
    }

    // 예매처와 권종 이름을 합쳐 "같은 티켓"인지 구별하는 열쇠를 만든다
    private static String ticketKey(String vendorName, String label) {
        return vendorKey(vendorName) + "|" + ArtistMatchService.normalize(label);
    }

    // 같은 예매처를 다르게 적어도 하나로 본다 (NOL / NOL티켓 / 놀티켓 / 인터파크 등)
    static String vendorKey(String vendorName) {
        String name = ArtistMatchService.normalize(vendorName);
        if (name.contains("nol") || name.contains("놀티켓") || name.contains("인터파크") || name.contains("interpark")) {
            return "nol";
        }
        if (name.contains("melon") || name.contains("멜론")) {
            return "melon";
        }
        if (name.contains("yes24") || name.contains("예스24")) {
            return "yes24";
        }
        if (name.contains("naver") || name.contains("네이버")) {
            return "naver";
        }
        if (name.contains("ticketlink") || name.contains("티켓링크")) {
            return "ticketlink";
        }
        return name;
    }

    public static List<TicketDraft> newTickets(List<FestivalTicket> current, List<TicketDraft> extracted) {
        Set<String> seen = new HashSet<>();
        for (FestivalTicket ticket : current) {
            seen.add(ticketKey(ticket.getVendorName(), ticket.getLabel()));
        }
        List<TicketDraft> result = new ArrayList<>();
        for (TicketDraft ticket : extracted) {
            if (seen.add(ticketKey(ticket.getVendorName(), ticket.getLabel()))) {
                result.add(ticket);
            }
        }
        return result;
    }
}