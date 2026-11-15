package com.example.stadium.service;

import static com.example.common.config.CacheConfig.STADIUMS;
import static com.example.common.config.CacheConfig.STADIUM_PLACES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;

import com.example.stadium.dto.StadiumDetailDto;
import com.example.stadium.entity.Place;
import com.example.stadium.entity.Stadium;
import com.example.stadium.repository.PlaceRepository;
import com.example.stadium.repository.StadiumRepository;
import com.example.stadium.favorite.repository.UserStadiumFavoriteRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

@SpringJUnitConfig(classes = StadiumAdminCacheInvalidationTest.CacheTestConfig.class)
class StadiumAdminCacheInvalidationTest {

    private static final String STADIUM_ID = "TEST_STADIUM";

    @jakarta.annotation.Resource
    private StadiumService stadiumService;

    @jakarta.annotation.Resource
    private StadiumAdminService stadiumAdminService;

    @jakarta.annotation.Resource
    private StadiumRepository stadiumRepository;

    @jakarta.annotation.Resource
    private PlaceRepository placeRepository;

    @jakarta.annotation.Resource
    private CacheManager cacheManager;

    private final List<Place> places = new ArrayList<>();
    private Stadium stadium;

    @BeforeEach
    void setUp() {
        clearCache(STADIUMS);
        clearCache(STADIUM_PLACES);
        places.clear();
        stadium = Stadium.builder()
                .stadiumId(STADIUM_ID)
                .stadiumName("Test Stadium")
                .build();

        when(stadiumRepository.findById(STADIUM_ID)).thenReturn(Optional.of(stadium));
        when(placeRepository.findByStadiumIdWithSort(STADIUM_ID)).thenAnswer(invocation -> List.copyOf(places));
        when(placeRepository.findByStadium_StadiumIdAndCategory(anyString(), anyString()))
                .thenAnswer(invocation -> places.stream()
                        .filter(place -> STADIUM_ID.equals(invocation.getArgument(0))
                                && invocation.getArgument(1).equals(place.getCategory()))
                        .toList());
        when(placeRepository.save(any(Place.class))).thenAnswer(invocation -> {
            Place place = invocation.getArgument(0);
            if (place.getId() == null) {
                place.setId(1L);
            }
            places.removeIf(existing -> existing.getId().equals(place.getId()));
            places.add(place);
            return place;
        });
        when(placeRepository.findById(anyLong())).thenAnswer(invocation -> places.stream()
                .filter(place -> place.getId().equals(invocation.getArgument(0)))
                .findFirst());
        when(placeRepository.existsById(anyLong())).thenAnswer(invocation -> places.stream()
                .anyMatch(place -> place.getId().equals(invocation.getArgument(0))));
        doAnswer(invocation -> {
            Long placeId = invocation.getArgument(0);
            places.removeIf(place -> place.getId().equals(placeId));
            return null;
        }).when(placeRepository).deleteById(anyLong());
    }

    @Test
    void placeCrudEvictsCachedStadiumDetailsAndPlaceLists() {
        assertThat(stadiumService.getStadiumDetail(STADIUM_ID).getPlaces()).isEmpty();
        assertThat(stadiumService.getPlacesByStadiumAndCategory(STADIUM_ID, "food")).isEmpty();

        stadiumAdminService.createPlace(STADIUM_ID, command("Fixture kiosk", "food"));

        StadiumDetailDto afterCreate = stadiumService.getStadiumDetail(STADIUM_ID);
        assertThat(afterCreate.getPlaces()).extracting(place -> place.getName()).containsExactly("Fixture kiosk");
        assertThat(stadiumService.getPlacesByStadiumAndCategory(STADIUM_ID, "food"))
                .extracting(place -> place.getName())
                .containsExactly("Fixture kiosk");

        assertThat(stadiumService.getPlacesByStadiumAndCategory(STADIUM_ID, "drink")).isEmpty();
        stadiumAdminService.updatePlace(1L, command("Fixture stand", "drink"));

        assertThat(stadiumService.getStadiumDetail(STADIUM_ID).getPlaces())
                .extracting(place -> place.getName())
                .containsExactly("Fixture stand");
        assertThat(stadiumService.getPlacesByStadiumAndCategory(STADIUM_ID, "food")).isEmpty();
        assertThat(stadiumService.getPlacesByStadiumAndCategory(STADIUM_ID, "drink"))
                .extracting(place -> place.getName())
                .containsExactly("Fixture stand");

        stadiumAdminService.deletePlace(1L);

        assertThat(stadiumService.getStadiumDetail(STADIUM_ID).getPlaces()).isEmpty();
        assertThat(stadiumService.getPlacesByStadiumAndCategory(STADIUM_ID, "drink")).isEmpty();
    }

    private void clearCache(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.clear();
        }
    }

    private StadiumPlaceCommand command(String name, String category) {
        return new StadiumPlaceCommand(
                name,
                category,
                "Fixture description",
                "Fixture address",
                "000-0000",
                0.0,
                0.0,
                null,
                null,
                null);
    }

    @Configuration
    @EnableCaching
    static class CacheTestConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(STADIUMS, STADIUM_PLACES);
        }

        @Bean
        StadiumRepository stadiumRepository() {
            return mock(StadiumRepository.class);
        }

        @Bean
        PlaceRepository placeRepository() {
            return mock(PlaceRepository.class);
        }

        @Bean
        UserStadiumFavoriteRepository userStadiumFavoriteRepository() {
            return mock(UserStadiumFavoriteRepository.class);
        }

        @Bean
        StadiumService stadiumService(
                StadiumRepository stadiumRepository,
                PlaceRepository placeRepository,
                UserStadiumFavoriteRepository favoriteRepository) {
            return new StadiumService(stadiumRepository, placeRepository, favoriteRepository);
        }

        @Bean
        StadiumAdminService stadiumAdminService(PlaceRepository placeRepository, StadiumRepository stadiumRepository) {
            return new StadiumAdminService(placeRepository, stadiumRepository);
        }
    }
}
