package com.example.mate.controller;

import com.example.common.exception.AuthenticationRequiredException;
import com.example.common.ratelimit.RateLimit;
import com.example.mate.dto.MateSearchTermDTO;
import com.example.mate.service.MateSearchTermService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/parties/search-terms")
@RequiredArgsConstructor
public class MateSearchTermController {

    private final MateSearchTermService mateSearchTermService;

    @RateLimit(limit = 60, window = 60, key = "mate:search-terms")
    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<Void> recordSearchTerm(
            @RequestBody(required = false) MateSearchTermDTO.RecordRequest request,
            @AuthenticationPrincipal Long userId) {
        requireUserId(userId);
        mateSearchTermService.recordSearchTerm(request == null ? null : request.getTerm());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/popular")
    public ResponseEntity<List<MateSearchTermDTO.PopularResponse>> getPopularSearchTerms(
            @RequestParam(defaultValue = "5") Integer limit) {
        return ResponseEntity.ok(mateSearchTermService.getPopularTerms(limit));
    }

    private Long requireUserId(Long userId) {
        if (userId == null) {
            throw new AuthenticationRequiredException("인증이 필요합니다.");
        }
        return userId;
    }
}
