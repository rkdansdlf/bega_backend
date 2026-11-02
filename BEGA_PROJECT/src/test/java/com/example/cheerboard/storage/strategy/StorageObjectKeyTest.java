package com.example.cheerboard.storage.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class StorageObjectKeyTest {
    @Test
    void matchingBucketPrefixesAndLeadingSlashResolveToTheSamePhysicalKey() {
        assertThat(StorageObjectKey.resolve("shared", "posts/7/a.webp")).isEqualTo("shared/posts/7/a.webp");
        assertThat(StorageObjectKey.resolve("shared", " /shared/posts/7/a.webp "))
                .isEqualTo("shared/posts/7/a.webp");
    }

    @Test
    void resolutionPreservesActualObjectKeyCharacters() {
        assertThat(StorageObjectKey.resolve("shared", "//posts/7/a%2Fb.webp"))
                .isEqualTo("shared//posts/7/a%2Fb.webp");
        assertThat(StorageObjectKey.resolve(null, "/posts/7/a.webp")).isEqualTo("posts/7/a.webp");
    }

    @Test
    void aliasesIncludeBareKeyWhenBucketNameIsAlsoThePathPrefix() {
        assertThat(StorageObjectKey.aliases("media", "media/cheer/7/a.webp"))
                .containsExactlyInAnyOrder("media/cheer/7/a.webp", "/media/cheer/7/a.webp",
                        "cheer/7/a.webp", "/cheer/7/a.webp");
        assertThat(StorageObjectKey.aliases("shared", "media/cheer/7/a.webp"))
                .allSatisfy(alias -> assertThat(StorageObjectKey.resolve("shared", alias))
                        .isEqualTo("shared/media/cheer/7/a.webp"));
        assertThat(StorageObjectKey.aliases("shared", "//posts/7/a.webp"))
                .allSatisfy(alias -> assertThat(StorageObjectKey.resolve("shared", alias))
                        .isEqualTo("shared//posts/7/a.webp"));
        assertThat(StorageObjectKey.aliases("shared", "shared/shared/a.webp"))
                .allSatisfy(alias -> assertThat(StorageObjectKey.resolve("shared", alias))
                        .isEqualTo("shared/shared/a.webp"));
    }

    @Test
    void aliasCandidatesNeverIdentifyADifferentPhysicalObject() {
        for (String bucket : new String[]{"shared", "media", ""}) {
            for (String path : new String[]{"media/cheer/7/a.webp", "/shared/a", "//shared/a", "shared/shared/a", ""}) {
                String expected = StorageObjectKey.resolve(bucket, path);
                assertThat(StorageObjectKey.aliases(bucket, path)).isNotEmpty()
                        .allSatisfy(alias -> assertThat(StorageObjectKey.resolve(bucket, alias)).isEqualTo(expected));
            }
        }
    }

    @Test
    void managedDiaryPrefixDetectionUsesThePhysicalKeyWhenBucketIsMedia() {
        assertThat(StorageObjectKey.isWithinResolvedPrefix("media", "media/diary/7/123/image.webp", "media/diary/"))
                .isTrue();
        assertThat(StorageObjectKey.isWithinResolvedPrefix("media", "/diary/7/123/image.webp", "media/diary/"))
                .isTrue();
        assertThat(StorageObjectKey.isWithinResolvedPrefix("other", "diary/7/123/image.webp", "media/diary/"))
                .isFalse();
    }

    @Test
    void physicalIdentityIncludesActualBucketAndResolvesAliases() {
        var bare = StorageObjectIdentity.of("physical", "shared", "posts/7/a.webp");
        var prefixed = StorageObjectIdentity.of("physical", "shared", "/shared/posts/7/a.webp");
        assertThat(bare).isEqualTo(prefixed);
        assertThat(bare.digest()).isEqualTo(prefixed.digest()).hasSize(64);
        assertThat(bare.digest()).isEqualTo("eb7b5958da184aea4cbfee1af1224e3d61f9c48eb3db242ff75e10bf1389076a");
        assertThat(StorageObjectIdentity.of("other", "shared", "posts/7/a.webp").digest())
                .isNotEqualTo(bare.digest());
    }

    @Test
    void identityDigestDoesNotConflateBucketAndKeyBoundaries() {
        assertThat(new StorageObjectIdentity("a", "bc").digest())
                .isNotEqualTo(new StorageObjectIdentity("ab", "c").digest());
    }

    @Test
    void identityRejectsUrlsAndMissingPathsForAutomaticCleanup() {
        assertThatThrownBy(() -> StorageObjectIdentity.of("physical", "shared", "https://example.invalid/file"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageObjectIdentity.of("physical", "shared", " "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageObjectIdentity.of("physical", "shared", "https://example.invalid/\nfile"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageObjectIdentity.of("physical", "shared", "//example.invalid/file"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageObjectIdentity.of("physical", "shared", "posts/7/a%2Fb.webp"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StorageObjectIdentity.of("physical", "shared", "posts/7/a.webp?token=secret"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
