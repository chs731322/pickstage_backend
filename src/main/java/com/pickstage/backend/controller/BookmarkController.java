package com.pickstage.backend.controller;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.pickstage.backend.service.BookmarkService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.http.ResponseEntity;
import com.pickstage.backend.global.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import com.pickstage.backend.dto.FestivalListResponse;
import java.util.List;

@RestController
@RequestMapping("/api/bookmarks")
public class BookmarkController {
    private final BookmarkService bookmarkService;

    public BookmarkController(BookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    // 북마크 추가
    @PostMapping("/{festivalId}")
    public ResponseEntity<ApiResponse<String>> addBookmark(@AuthenticationPrincipal Long userId, @PathVariable Long festivalId) {
        bookmarkService.addBookmark(userId, festivalId);
        return ResponseEntity.status(201).body(ApiResponse.success("북마크가 추가되었습니다."));
    }

    // 북마크 삭제
    @DeleteMapping("/{festivalId}")
    public ResponseEntity<ApiResponse<String>> removeBookmark(@AuthenticationPrincipal Long userId, @PathVariable Long festivalId) {
        bookmarkService.removeBookmark(userId, festivalId);
        return ResponseEntity.ok(ApiResponse.success("북마크가 삭제되었습니다."));
    }

    // 나의 북마크 조회
    @GetMapping
    public ResponseEntity<ApiResponse<List<FestivalListResponse>>> getUserBookmarks(@AuthenticationPrincipal Long userId) {
        List<FestivalListResponse> bookmarks = bookmarkService.getUserBookmarks(userId);
        return ResponseEntity.ok(ApiResponse.success(bookmarks));
    }
    
}  
