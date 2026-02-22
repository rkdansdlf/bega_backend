package com.example.cheerboard.dto;

import com.example.cheerboard.domain.CheerPost;

import java.util.List;

public record CreatePostReq(String teamId,
                // title removed
                String content,
                List<String> images,
                String postType,
                CheerPost.ShareMode shareMode,
                String sourceUrl,
                String sourceTitle,
                String sourceAuthor,
                String sourceLicense,
                String sourceLicenseUrl,
                String sourceChangedNote,
                String sourceSnapshotType) {
        public CreatePostReq(String teamId, String content, List<String> images, String postType) {
                this(teamId, content, images, postType, null, null, null, null, null, null, null, null);
        }
}
