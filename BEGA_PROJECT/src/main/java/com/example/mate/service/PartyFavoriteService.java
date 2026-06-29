package com.example.mate.service;

import com.example.mate.entity.UserPartyFavorite;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.UserPartyFavoriteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 파티 찜(favorite) 토글/조회. stadium UserStadiumFavorite 패턴을 미러링한다.
 */
@Service
@RequiredArgsConstructor
public class PartyFavoriteService {

    private final UserPartyFavoriteRepository favoriteRepository;
    private final PartyRepository partyRepository;

    @Transactional
    public void addFavorite(Long userId, Long partyId) {
        if (!partyRepository.existsById(partyId)) {
            throw new PartyNotFoundException(partyId);
        }
        if (!favoriteRepository.existsByUserIdAndPartyId(userId, partyId)) {
            favoriteRepository.save(new UserPartyFavorite(userId, partyId));
        }
    }

    @Transactional
    public void removeFavorite(Long userId, Long partyId) {
        favoriteRepository.deleteByUserIdAndPartyId(userId, partyId);
    }

    @Transactional(readOnly = true)
    public boolean isFavorited(Long userId, Long partyId) {
        return favoriteRepository.existsByUserIdAndPartyId(userId, partyId);
    }

    @Transactional(readOnly = true)
    public List<Long> getFavoritePartyIds(Long userId) {
        return favoriteRepository.findByUserId(userId).stream()
                .map(UserPartyFavorite::getPartyId)
                .toList();
    }
}
