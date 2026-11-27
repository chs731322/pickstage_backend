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
        return ArtistMatchService.normalize(vendorName) + "|" + ArtistMatchService.normalize(label);
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