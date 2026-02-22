package com.example.cheerboard.dto;

import com.example.cheerboard.domain.CheerPost;

// 게시글 수정을 위한 데이터 전송 객체 (DTO)
public record UpdatePostReq(
                // title removed
                String content,
                CheerPost.ShareMode shareMode,
                String sourceUrl,
                String sourceTitle,
                String sourceAuthor,
                String sourceLicense,
                String sourceLicenseUrl,
                String sourceChangedNote,
                String sourceSnapshotType) {
        public UpdatePostReq(String content) {
                this(content, null, null, null, null, null, null, null, null);
        }
}
