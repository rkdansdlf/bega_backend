package com.example.prediction;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VoteFinalResultRepository extends JpaRepository<VoteFinalResult, String>{

}
