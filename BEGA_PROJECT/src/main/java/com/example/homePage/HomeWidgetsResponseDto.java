package com.example.homepage;

import com.example.cheerboard.dto.PostSummaryRes;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeWidgetsResponseDto {

    private List<PostSummaryRes> hotCheerPosts;
    private List<FeaturedMateCardDto> featuredMates;
}
