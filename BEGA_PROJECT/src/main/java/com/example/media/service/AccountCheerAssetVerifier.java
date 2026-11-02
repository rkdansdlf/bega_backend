package com.example.media.service;

import com.example.cheerboard.domain.CheerPost;
import com.example.cheerboard.storage.entity.PostImage;
import com.example.media.entity.MediaAsset;
import com.example.media.entity.MediaAssetStatus;
import com.example.media.entity.MediaDomain;
import com.example.media.repository.MediaAssetLinkRepository;
import com.example.media.repository.MediaAssetRepository;
import java.util.Collection;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Checks that every managed cheer image on an account's posts is backed by an asset the account owns and that is
 * linked to that same post. Only then may the content pass clear the image rows, because media cleanup owns those
 * objects. Legacy paths are not managed and are not checked here.
 */
@Service
@RequiredArgsConstructor
public class AccountCheerAssetVerifier {

    private final MediaAssetRepository mediaAssets;
    private final MediaAssetLinkRepository mediaAssetLinks;

    public boolean allManagedImagesHaveOwnedLinks(Long userId, Collection<CheerPost> authoredPosts) {
        for (CheerPost post : authoredPosts) {
            if (post.getImages() == null) {
                continue;
            }
            for (PostImage image : post.getImages()) {
                String path = image.getStoragePath();
                if (!MediaDomain.CHEER.isManagedPath(path)) {
                    continue;
                }
                if (!isOwnedAndLinked(userId, post.getId(), path)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isOwnedAndLinked(Long userId, Long postId, String path) {
        return mediaAssets.findByObjectKey(path)
                .filter(asset -> isCheerAssetOwnedBy(asset, userId))
                .map(asset -> mediaAssetLinks.countCheerPostLink(asset.getId(), postId) > 0)
                .orElse(false);
    }

    /**
     * A reserved asset ({@code CHEER_PURGE_PENDING}) still belongs to the author and stays linked to its post, and
     * account cleanup never deletes it, so it counts as backed. Otherwise a reported post's reservation would hold the
     * account past the stale-reservation limit.
     */
    private static boolean isCheerAssetOwnedBy(MediaAsset asset, Long userId) {
        return userId != null
                && userId.equals(asset.getOwnerUserId())
                && asset.getDomain() == MediaDomain.CHEER
                && (asset.getStatus() == MediaAssetStatus.READY
                    || asset.getStatus() == MediaAssetStatus.CHEER_PURGE_PENDING);
    }
}
