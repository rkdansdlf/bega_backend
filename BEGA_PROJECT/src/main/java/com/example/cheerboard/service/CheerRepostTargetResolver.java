package com.example.cheerboard.service;

import com.example.cheerboard.domain.CheerPost;

public final class CheerRepostTargetResolver {

    private CheerRepostTargetResolver() {
    }

    public static CheerPost resolveActionTargetPost(CheerPost post) {
        CheerPost current = post;
        int hops = 0;
        while (current.isRepost()) {
            CheerPost parent = current.getRepostOf();
            if (parent == null) {
                return current;
            }
            current = parent;
            if (++hops > 32) {
                throw new IllegalArgumentException("리포스트 대상이 비정상적으로 설정되어 있습니다.");
            }
        }
        return current;
    }
}
