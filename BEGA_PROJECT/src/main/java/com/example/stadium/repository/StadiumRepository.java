package com.example.stadium.repository;

import com.example.stadium.entity.Stadium;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.List;

@Repository
public interface StadiumRepository extends JpaRepository<Stadium, String> {
    
    // Spring Data JPA 메서드 네이밍 컨벤션 사용 - 자동으로 쿼리 생성
    Optional<Stadium> findByStadiumName(String stadiumName);
    
    // 정렬이 필요한 경우 메서드 네이밍으로 처리
    List<Stadium> findAllByOrderByStadiumIdAsc();
}