package com.example.cheerboard.controller;

import com.example.cheerboard.dto.CreatePostReq;
import com.example.cheerboard.dto.UpdatePostReq;
import com.example.cheerboard.dto.PostSummaryRes;
import com.example.cheerboard.dto.PostDetailRes;
import com.example.cheerboard.dto.CreateCommentReq;
import com.example.cheerboard.dto.CommentRes;
import com.example.cheerboard.service.CheerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/cheer")
@RequiredArgsConstructor
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class CheerController {

    private final CheerService svc;

    @GetMapping("/posts")
    public Page<PostSummaryRes> list(
        @RequestParam(required = false) String teamId,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
        Pageable pageable) {
        return svc.list(teamId, pageable);
    }

    @GetMapping("/posts/{id}")
    public PostDetailRes get(@PathVariable Long id) {
        return svc.get(id);
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public PostDetailRes create(@RequestBody CreatePostReq req) {
        return svc.createPost(req);
    }

    @PutMapping("/posts/{id}")
    public PostDetailRes update(@PathVariable Long id, @RequestBody UpdatePostReq req) {
        return svc.updatePost(id, req);
    }

    @DeleteMapping("/posts/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        svc.deletePost(id);
    }

    @PostMapping("/posts/{id}/like")
    public java.util.Map<String, Object> toggleLike(@PathVariable Long id) {
        boolean liked = svc.toggleLike(id);
        return java.util.Map.of("liked", liked);
    }

    @GetMapping("/posts/{id}/comments")
    public Page<CommentRes> comments(@PathVariable Long id,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
        Pageable pageable) {
        return svc.listComments(id, pageable);
    }

    @PostMapping("/posts/{id}/comments")
    public CommentRes addComment(@PathVariable Long id, @RequestBody CreateCommentReq req) {
        return svc.addComment(id, req);
    }

    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable Long commentId) {
        svc.deleteComment(commentId);
    }
}