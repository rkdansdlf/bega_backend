package com.example.stadium.repository;

import com.example.stadium.entity.UserStadiumFavorite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserStadiumFavoriteRepository extends JpaRepository<UserStadiumFavorite, Long> {

    List<UserStadiumFavorite> findByUserId(Long userId);

    Optional<UserStadiumFavorite> findByUserIdAndStadiumId(Long userId, String stadiumId);

    boolean existsByUserIdAndStadiumId(Long userId, String stadiumId);

    @Transactional("stadiumTransactionManager")
    void deleteByUserIdAndStadiumId(Long userId, String stadiumId);
}
