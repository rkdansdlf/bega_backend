package com.example.homepage.port;

import com.example.homepage.FeaturedMateCardDto;
import java.time.LocalDate;
import java.util.List;

@FunctionalInterface
public interface FeaturedMateQuery {

    List<FeaturedMateCardDto> getFeaturedMateCards(LocalDate baseDate, int limit);
}
