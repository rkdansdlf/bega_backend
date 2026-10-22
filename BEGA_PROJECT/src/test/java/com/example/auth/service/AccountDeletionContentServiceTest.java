package com.example.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    void preservesAllAccountContentAndMediaWhileAnyModerationCaseRemainsOpen() {
        CheerPost post = CheerPost.builder().id(101L).content("identifying post").sourceUrl("private pointer")
                .images(new ArrayList<>()).build();
        CheerPost protectedPost = CheerPost.builder().id(102L).content("unresolved evidence").build();
        when(cheerReportRepo.findOpenModerationPostIdsByAuthorId(7L)).thenReturn(Set.of(102L));
        when(cheerPostRepo.findAllByAuthorIdIncludingDeletedForUpdate(7L))
                .thenReturn(List.of(post, protectedPost));

        service.anonymize(7L);

        assertThat(post.getContent()).isEqualTo("identifying post");
        assertThat(post.getSourceUrl()).isEqualTo("private pointer");
        assertThat(post.getImages()).isEmpty();
        assertThat(protectedPost.getContent()).isEqualTo("unresolved evidence");
        verify(cheerCommentRepo, never()).findAllByAuthor_Id(7L);
        verifyNoInteractions(mediaUploadService);
    }
}
