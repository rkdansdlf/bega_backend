package com.example.stadium.repository;

import com.example.stadium.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PlaceRepository extends JpaRepository<Place, Long> {

    
    List<Place> findByStadium_StadiumId(String stadiumId);
  
    List<Place> findByStadium_StadiumIdAndCategory(String stadiumId, String category);

    @Query(value = "SELECT * FROM public.places WHERE stadium_id = :stadiumId " +
                   "ORDER BY category, rating DESC NULLS LAST", nativeQuery = true)
    List<Place> findByStadiumIdWithSort(@Param("stadiumId") String stadiumId);

    @Query("SELECT p FROM Place p JOIN p.stadium s " +
           "WHERE s.stadiumName = :stadiumName AND p.category = :category")
    List<Place> findByStadiumNameAndCategory(@Param("stadiumName") String stadiumName, 
                                             @Param("category") String category);
}