package com.example.auth.oauth2;

import com.example.bega.auth.dto.OAuth2LinkStateData;

/** Local OAuth callback data that is finalized only after account eligibility is locked and checked. */
public record OAuth2LoginIntent(
        String provider,
        String providerId,
        String email,
        String userName,
        String profileImageUrl,
        boolean emailVerified,
        boolean emailFromLinkedIdentity,
        OAuth2LinkStateData linkData) {

    public boolean isLinkFlow() {
        return linkData != null && linkData.isLinkMode();
    }
}
