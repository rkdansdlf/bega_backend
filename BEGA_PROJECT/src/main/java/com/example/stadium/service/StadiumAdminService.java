package com.example.stadium.service;

import com.example.stadium.dto.PlaceDto;
import com.example.stadium.entity.Place;
import com.example.stadium.entity.Stadium;
import com.example.stadium.exception.StadiumNotFoundException;
import com.example.stadium.repository.PlaceRepository;
import com.example.stadium.repository.StadiumRepository;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StadiumAdminService {

    private final PlaceRepository placeRepository;
    private final StadiumRepository stadiumRepository;

    @Transactional
    public PlaceDto createPlace(String stadiumId, StadiumPlaceCommand command) {
        Stadium stadium = stadiumRepository.findById(stadiumId)
                .orElseThrow(() -> new StadiumNotFoundException(stadiumId));
        Place place = Place.builder()
                .stadium(stadium)
                .name(command.name())
                .category(command.category())
                .description(command.description())
                .address(command.address())
                .phone(command.phone())
                .lat(command.lat())
                .lng(command.lng())
                .rating(toRating(command.rating()))
                .openTime(command.openTime())
                .closeTime(command.closeTime())
                .build();

        return toDto(placeRepository.save(place));
    }

    @Transactional
    public PlaceDto updatePlace(Long placeId, StadiumPlaceCommand command) {
        Place place = placeRepository.findById(placeId)
                .orElseThrow(() -> new EntityNotFoundException("장소를 찾을 수 없습니다. id=" + placeId));
        place.setName(command.name());
        place.setCategory(command.category());
        place.setDescription(command.description());
        place.setAddress(command.address());
        place.setPhone(command.phone());
        place.setLat(command.lat());
        place.setLng(command.lng());
        place.setRating(toRating(command.rating()));
        place.setOpenTime(command.openTime());
        place.setCloseTime(command.closeTime());

        return toDto(placeRepository.save(place));
    }

    @Transactional
    public void deletePlace(Long placeId) {
        if (!placeRepository.existsById(placeId)) {
            throw new EntityNotFoundException("장소를 찾을 수 없습니다. id=" + placeId);
        }
        placeRepository.deleteById(placeId);
    }

    private BigDecimal toRating(Double rating) {
        return rating != null ? BigDecimal.valueOf(rating) : null;
    }

    private PlaceDto toDto(Place place) {
        return PlaceDto.builder()
                .id(place.getId())
                .stadiumName(place.getStadium().getStadiumName())
                .category(place.getCategory())
                .name(place.getName())
                .description(place.getDescription())
                .lat(place.getLat())
                .lng(place.getLng())
                .address(place.getAddress())
                .phone(place.getPhone())
                .rating(place.getRating() != null ? place.getRating().doubleValue() : null)
                .openTime(place.getOpenTime())
                .closeTime(place.getCloseTime())
                .build();
    }
}
