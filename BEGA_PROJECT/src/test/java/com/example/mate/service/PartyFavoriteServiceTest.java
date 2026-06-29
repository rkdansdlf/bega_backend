package com.example.mate.service;

import com.example.mate.entity.UserPartyFavorite;
import com.example.mate.exception.PartyNotFoundException;
import com.example.mate.repository.PartyRepository;
import com.example.mate.repository.UserPartyFavoriteRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PartyFavoriteServiceTest {

    @Mock
    private UserPartyFavoriteRepository favoriteRepository;

    @Mock
    private PartyRepository partyRepository;

    @InjectMocks
    private PartyFavoriteService partyFavoriteService;

    @Test
    void addFavorite_savesWhenAbsent() {
        when(partyRepository.existsById(1L)).thenReturn(true);
        when(favoriteRepository.existsByUserIdAndPartyId(10L, 1L)).thenReturn(false);

        partyFavoriteService.addFavorite(10L, 1L);

        verify(favoriteRepository).save(any(UserPartyFavorite.class));
    }

    @Test
    void addFavorite_isIdempotentWhenAlreadyFavorited() {
        when(partyRepository.existsById(1L)).thenReturn(true);
        when(favoriteRepository.existsByUserIdAndPartyId(10L, 1L)).thenReturn(true);

        partyFavoriteService.addFavorite(10L, 1L);

        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void addFavorite_throwsWhenPartyMissing() {
        when(partyRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> partyFavoriteService.addFavorite(10L, 99L))
                .isInstanceOf(PartyNotFoundException.class);
        verify(favoriteRepository, never()).save(any());
    }

    @Test
    void removeFavorite_delegatesToRepository() {
        partyFavoriteService.removeFavorite(10L, 1L);

        verify(favoriteRepository).deleteByUserIdAndPartyId(10L, 1L);
    }

    @Test
    void isFavorited_reflectsRepository() {
        when(favoriteRepository.existsByUserIdAndPartyId(10L, 1L)).thenReturn(true);

        assertThat(partyFavoriteService.isFavorited(10L, 1L)).isTrue();
    }
}
