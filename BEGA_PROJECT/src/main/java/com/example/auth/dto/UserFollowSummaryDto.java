package com.example.auth.dto;

import com.example.auth.entity.UserEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 팔로워/팔로잉 목록용 유저 요약 DTO
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserFollowSummaryDto {
    private Long id;
    private String handle;
    private String name;
    private String profileImageUrl;
    private String favoriteTeam;
    private boolean isFollowedByMe;

    public static UserFollowSummaryDto from(UserEntity user, boolean isFollowedByMe) {
        return UserFollowSummaryDto.builder()
                .id(user.getId())
                .handle(user.getHandle())
                .name(user.getName())
                .profileImageUrl(user.getProfileImageUrl())
                .favoriteTeam(user.getFavoriteTeamId())
                .isFollowedByMe(isFollowedByMe)
                .build();
    }
}
