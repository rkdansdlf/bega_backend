package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.cheerboard.domain.CheerComment;
import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.repo.CheerPostRepo;
import com.example.cheerboard.repo.CheerReportRepo;
import com.example.cheerboard.repo.CheerCommentRepo;
import com.example.media.service.MediaUploadService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountDeletionContentServiceTest {

    @Mock
    private CheerPostRepo cheerPostRepo;

    @Mock
    private CheerCommentRepo cheerCommentRepo;

    @Mock
    private CheerReportRepo cheerReportRepo;

    @Mock
    private MediaUploadService mediaUploadService;

    @InjectMocks
    private AccountDeletionContentService service;

    @Test
    void redactsPublishedContentButLeavesPostsWithOpenModerationEvidenceUntouched() {
        CheerPost post = CheerPost.builder().id(101L).content("identifying post").sourceUrl("private pointer")
                .images(new ArrayList<>()).build();
        CheerPost protectedPost = CheerPost.builder().id(102L).content("unresolved evidence").build();
        CheerComment comment = CheerComment.builder().content("identifying comment").build();
        when(cheerReportRepo.findOpenModerationPostIdsByAuthorId(7L)).thenReturn(Set.of(102L));
        when(cheerPostRepo.findAllByAuthorIdIncludingDeletedForUpdate(7L))
                .thenReturn(List.of(post, protectedPost));
        when(cheerCommentRepo.findAllByAuthor_Id(7L)).thenReturn(List.of(comment));

        service.anonymize(7L);

        assertThat(post.getContent()).isEqualTo("게시글이 비식별 처리되었습니다.");
        assertThat(post.getSourceUrl()).isNull();
        assertThat(post.getImages()).isEmpty();
        assertThat(protectedPost.getContent()).isEqualTo("unresolved evidence");
        assertThat(comment.getContent()).isEqualTo("댓글이 비식별 처리되었습니다.");
    }
}
