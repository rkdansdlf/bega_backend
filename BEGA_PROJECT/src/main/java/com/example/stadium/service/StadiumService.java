package com.example.stadium.service;

import com.example.stadium.dto.PlaceDto;
import com.example.stadium.dto.StadiumDto;
import com.example.stadium.dto.StadiumDetailDto;
import com.example.stadium.entity.Place;
import com.example.stadium.entity.Stadium;
import com.example.stadium.exception.StadiumNotFoundException;
import com.example.stadium.repository.PlaceRepository;
import com.example.stadium.repository.StadiumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.stadium.exception.StadiumNotFoundException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class StadiumService {
    
    private final StadiumRepository stadiumRepository;
    private final PlaceRepository placeRepository;
    
    public List<StadiumDto> getAllStadiums() {
        return stadiumRepository.findAll().stream()
                .filter(stadium -> stadium.getLat() != null && stadium.getLng() != null) 
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public StadiumDetailDto getStadiumDetail(String stadiumId) {  
        Stadium stadium = stadiumRepository.findById(stadiumId)
                .orElseThrow(() -> new StadiumNotFoundException(stadiumId));
        
        List<Place> places = placeRepository.findByStadiumIdWithSort(stadiumId);
        
        return StadiumDetailDto.builder()
                .stadiumId(stadium.getStadiumId())
                .stadiumName(stadium.getStadiumName())
                .team(stadium.getTeam())
                .lat(stadium.getLat())
                .lng(stadium.getLng())
                .address(stadium.getAddress())
                .phone(stadium.getPhone())
                .places(places.stream().map(this::convertPlaceToDto).collect(Collectors.toList()))
                .build();
    }
    
    public StadiumDetailDto getStadiumDetailByName(String stadiumName) {
        Stadium stadium = stadiumRepository.findByStadiumName(stadiumName)
                .orElseThrow(() -> new StadiumNotFoundException("경기장명", stadiumName));
        
        return getStadiumDetail(stadium.getStadiumId());
    }
    
    public List<PlaceDto> getPlacesByStadiumAndCategory(String stadiumId, String category) {
        // 변경: findByStadiumStadiumIdAndCategory → findByStadium_StadiumIdAndCategory
        return placeRepository.findByStadium_StadiumIdAndCategory(stadiumId, category).stream()
                .map(this::convertPlaceToDto)
                .collect(Collectors.toList());
    }
    
    public List<PlaceDto> getPlacesByStadiumNameAndCategory(String stadiumName, String category) {
        return placeRepository.findByStadiumNameAndCategory(stadiumName, category).stream()
                .map(this::convertPlaceToDto)
                .collect(Collectors.toList());
    }
    
    public List<PlaceDto> getAllPlaces() {
        return placeRepository.findAll().stream()
                .map(this::convertPlaceToDto)
                .collect(Collectors.toList());
    }
    
    private StadiumDto convertToDto(Stadium stadium) {
        return StadiumDto.builder()
                .stadiumId(stadium.getStadiumId())
                .stadiumName(stadium.getStadiumName())
                .team(stadium.getTeam())
                .lat(stadium.getLat())
                .lng(stadium.getLng())
                .address(stadium.getAddress())
                .phone(stadium.getPhone())
                .build();
    }
    
    private PlaceDto convertPlaceToDto(Place place) {
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