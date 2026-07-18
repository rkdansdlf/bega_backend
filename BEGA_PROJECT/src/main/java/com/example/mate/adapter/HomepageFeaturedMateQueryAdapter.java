package com.example.mate.adapter;

import com.example.homepage.FeaturedMateCardDto;
import com.example.homepage.port.FeaturedMateQuery;
import com.example.mate.service.PartyService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HomepageFeaturedMateQueryAdapter implements FeaturedMateQuery {

    private final PartyService partyService;

    @Override
    public List<FeaturedMateCardDto> getFeaturedMateCards(LocalDate baseDate, int limit) {
        return partyService.getFeaturedMateCards(baseDate, limit);
    }
}
