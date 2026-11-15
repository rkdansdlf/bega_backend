package com.example.leaderboard.repository;

/**
 * Shared JPQL visibility predicate for public leaderboard feeds.
 *
 * <p>The ordering of the branches mirrors {@code PublicVisibilityVerifier}:
 * owners are always visible, bidirectional blocks hide non-owners, and private
 * accounts are visible only to followers.</p>
 */
final class LeaderboardVisibilityQueries {

    static final String VISIBLE_USER_PREDICATE = """
            (
                (:viewerId IS NOT NULL AND u.id = :viewerId)
                OR (
                    (
                        :viewerId IS NULL
                        OR NOT EXISTS (
                            SELECT ub
                            FROM UserBlock ub
                            WHERE (ub.id.blockerId = :viewerId AND ub.id.blockedId = u.id)
                               OR (ub.id.blockerId = u.id AND ub.id.blockedId = :viewerId)
                        )
                    )
                    AND (
                        u.privateAccount = false
                        OR (
                            :viewerId IS NOT NULL
                            AND EXISTS (
                                SELECT uf
                                FROM UserFollow uf
                                WHERE uf.id.followerId = :viewerId
                                  AND uf.id.followingId = u.id
                            )
                        )
                    )
                )
            )
            """;

    private LeaderboardVisibilityQueries() {
    }
}
