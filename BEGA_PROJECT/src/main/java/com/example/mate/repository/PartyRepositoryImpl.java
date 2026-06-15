package com.example.mate.repository;

import com.example.auth.entity.QUserBlock;
import com.example.auth.entity.QUserEntity;
import com.example.auth.entity.QUserFollow;
import com.example.mate.entity.Party;
import com.example.mate.entity.Party.PartyStatus;
import com.example.mate.entity.QPartyApplication;
import com.example.mate.entity.QParty;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.Order;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.JPAExpressions;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public class PartyRepositoryImpl implements PartyRepositoryCustom {

        private static final QParty party = QParty.party;

        private final JPAQueryFactory queryFactory;

        public PartyRepositoryImpl(EntityManager entityManager) {
                this.queryFactory = new JPAQueryFactory(entityManager);
        }

        @Override
        public Page<Party> findPartiesWithFilter(
                        String teamId,
                        String stadium,
                        LocalDate gameDate,
                        String query,
                        List<Party.PartyStatus> excludedStatuses,
                        PartyStatus status,
                        Pageable pageable) {
                BooleanBuilder filters = buildPartyFilters(teamId, stadium, gameDate, query, excludedStatuses, status);

                List<Party> content = queryFactory
                                .selectFrom(party)
                                .where(filters)
                                .orderBy(toOrderSpecifiers(pageable.getSort()))
                                .offset(pageable.getOffset())
                                .limit(pageable.getPageSize())
                                .fetch();

                Long total = queryFactory
                                .select(party.count())
                                .from(party)
                                .where(filters)
                                .fetchOne();

                return new PageImpl<>(content, pageable, total != null ? total : 0L);
        }

        @Override
        public Page<Party> findVisiblePublicPartiesWithFilter(
                        String teamId,
                        String stadium,
                        LocalDate gameDate,
                        String query,
                        List<Party.PartyStatus> excludedStatuses,
                        PartyStatus status,
                        Long currentUserId,
                        Pageable pageable) {
                QUserEntity host = QUserEntity.userEntity;
                BooleanBuilder filters = buildPartyFilters(teamId, stadium, gameDate, query, excludedStatuses, status)
                                .and(visibleToCurrentUser(host, currentUserId));

                List<Party> content = queryFactory
                                .selectFrom(party)
                                .leftJoin(host).on(host.id.eq(party.hostId))
                                .where(filters)
                                .orderBy(toOrderSpecifiers(pageable.getSort()))
                                .offset(pageable.getOffset())
                                .limit(pageable.getPageSize())
                                .fetch();

                Long total = queryFactory
                                .select(party.count())
                                .from(party)
                                .leftJoin(host).on(host.id.eq(party.hostId))
                                .where(filters)
                                .fetchOne();

                return new PageImpl<>(content, pageable, total != null ? total : 0L);
        }

        @Override
        public Page<Party> findMyHistory(
                        Long userId,
                        List<PartyStatus> statuses,
                        Pageable pageable) {
                BooleanBuilder filters = buildMyHistoryFilters(userId, statuses);

                List<Party> content = queryFactory
                                .selectFrom(party)
                                .where(filters)
                                .orderBy(toOrderSpecifiers(pageable.getSort()))
                                .offset(pageable.getOffset())
                                .limit(pageable.getPageSize())
                                .fetch();

                Long total = queryFactory
                                .select(party.count())
                                .from(party)
                                .where(filters)
                                .fetchOne();

                return new PageImpl<>(content, pageable, total != null ? total : 0L);
        }

        private BooleanBuilder buildPartyFilters(
                        String teamId,
                        String stadium,
                        LocalDate gameDate,
                        String query,
                        List<Party.PartyStatus> excludedStatuses,
                        PartyStatus status) {
                BooleanBuilder filters = new BooleanBuilder();

                if (hasText(teamId)) {
                        filters.and(party.teamId.eq(teamId.trim()));
                }
                if (hasText(stadium)) {
                        filters.and(party.stadium.eq(stadium.trim()));
                }
                if (gameDate != null) {
                        filters.and(party.gameDate.eq(gameDate));
                }
                if (hasText(query)) {
                        String normalizedQuery = query.trim().toLowerCase(Locale.ROOT);
                        filters.and(party.searchText.coalesce("").lower().contains(normalizedQuery));
                }
                if (status != null) {
                        filters.and(party.status.eq(status));
                } else if (excludedStatuses != null && !excludedStatuses.isEmpty()) {
                        filters.and(party.status.notIn(excludedStatuses));
                }

                return filters;
        }

        private BooleanBuilder buildMyHistoryFilters(Long userId, List<PartyStatus> statuses) {
                QPartyApplication application = QPartyApplication.partyApplication;
                BooleanBuilder filters = new BooleanBuilder();
                BooleanExpression isHost = party.hostId.eq(userId);
                BooleanExpression isApprovedParticipant = JPAExpressions
                                .selectOne()
                                .from(application)
                                .where(
                                                application.partyId.eq(party.id),
                                                application.applicantId.eq(userId),
                                                application.isApproved.eq(true))
                                .exists();

                filters.and(isHost.or(isApprovedParticipant));
                if (statuses != null && !statuses.isEmpty()) {
                        filters.and(party.status.in(statuses));
                }
                return filters;
        }

        private BooleanExpression visibleToCurrentUser(QUserEntity host, Long currentUserId) {
                BooleanExpression hostMissing = host.id.isNull();
                BooleanExpression publicHost = host.privateAccount.isFalse();

                if (currentUserId == null) {
                        return hostMissing.or(publicHost);
                }

                BooleanExpression selfHosted = host.id.eq(currentUserId);
                BooleanExpression notBlocked = blockRelationshipExists(host, currentUserId).not();
                BooleanExpression visiblePrivateHost = publicHost.or(followRelationshipExists(host, currentUserId));

                return hostMissing.or(selfHosted).or(notBlocked.and(visiblePrivateHost));
        }

        private BooleanExpression blockRelationshipExists(QUserEntity host, Long currentUserId) {
                QUserBlock userBlock = QUserBlock.userBlock;
                return JPAExpressions
                                .selectOne()
                                .from(userBlock)
                                .where(
                                                userBlock.id.blockerId.eq(currentUserId)
                                                                .and(userBlock.id.blockedId.eq(host.id))
                                                                .or(userBlock.id.blockerId.eq(host.id)
                                                                                .and(userBlock.id.blockedId.eq(currentUserId))))
                                .exists();
        }

        private BooleanExpression followRelationshipExists(QUserEntity host, Long currentUserId) {
                QUserFollow userFollow = QUserFollow.userFollow;
                return JPAExpressions
                                .selectOne()
                                .from(userFollow)
                                .where(
                                                userFollow.id.followerId.eq(currentUserId),
                                                userFollow.id.followingId.eq(host.id))
                                .exists();
        }

        private OrderSpecifier<?>[] toOrderSpecifiers(Sort sort) {
                List<OrderSpecifier<?>> orderSpecifiers = new ArrayList<>();
                for (Sort.Order sortOrder : sort) {
                        Order direction = sortOrder.isAscending() ? Order.ASC : Order.DESC;
                        switch (sortOrder.getProperty()) {
                                case "createdAt" -> orderSpecifiers.add(new OrderSpecifier<>(direction, party.createdAt));
                                case "gameDate" -> orderSpecifiers.add(new OrderSpecifier<>(direction, party.gameDate));
                                case "currentParticipants" ->
                                                orderSpecifiers.add(new OrderSpecifier<>(direction, party.currentParticipants));
                                case "id" -> orderSpecifiers.add(new OrderSpecifier<>(direction, party.id));
                                default -> {
                                }
                        }
                }
                return orderSpecifiers.toArray(OrderSpecifier[]::new);
        }

        private boolean hasText(String value) {
                return value != null && !value.trim().isEmpty();
        }
}
