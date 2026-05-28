package com.example.stadium.repository;

import com.example.stadium.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PlaceRepository extends JpaRepository<Place, Long> {

    @Query("SELECT p FROM Place p LEFT JOIN FETCH p.stadium s WHERE s.stadiumId = :stadiumId")
    List<Place> findByStadium_StadiumId(@Param("stadiumId") String stadiumId);

    @Query("SELECT p FROM Place p LEFT JOIN FETCH p.stadium s WHERE s.stadiumId = :stadiumId AND p.category = :category")
    List<Place> findByStadium_StadiumIdAndCategory(@Param("stadiumId") String stadiumId, @Param("category") String category);

    @Query("SELECT p FROM Place p LEFT JOIN FETCH p.stadium s WHERE s.stadiumId = :stadiumId ORDER BY p.category ASC NULLS LAST, p.rating DESC NULLS LAST")
    List<Place> findByStadiumIdWithSort(@Param("stadiumId") String stadiumId);

    @Query("SELECT p FROM Place p JOIN FETCH p.stadium s WHERE s.stadiumName = :stadiumName AND p.category = :category")
    List<Place> findByStadiumNameAndCategory(@Param("stadiumName") String stadiumName,
                                             @Param("category") String category);

    @Query("SELECT p FROM Place p LEFT JOIN FETCH p.stadium")
    List<Place> findAllWithStadium();
}