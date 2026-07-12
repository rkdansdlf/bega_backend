package com.example.cheerboard.dto;

import com.example.cheerboard.domain.CheerPost;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record CreatePostReq(String teamId,
                // title removed
                @NotBlank(message = "내용은 필수입니다.")
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
                String sourceSnapshotType,
                Long diaryId,
                Long partyId) {

        public CreatePostReq(
                        String teamId,
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
                this(teamId, content, images, postType, shareMode, sourceUrl, sourceTitle, sourceAuthor,
                                sourceLicense, sourceLicenseUrl, sourceChangedNote, sourceSnapshotType, null, null);
        }

        public CreatePostReq(String teamId, String content, List<String> images, String postType) {
                this(teamId, content, images, postType, null, null, null, null, null, null, null, null, null, null);
        }
}
