package com.example.mate.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.homepage.FeaturedMateCardDto;
import com.example.mate.service.PartyService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class HomepageFeaturedMateQueryAdapterTest {

    @Test
    void getFeaturedMateCardsDelegatesToExistingPartyQuery() {
        PartyService partyService = mock(PartyService.class);
        HomepageFeaturedMateQueryAdapter adapter = new HomepageFeaturedMateQueryAdapter(partyService);
        LocalDate date = LocalDate.of(2026, 3, 15);
        List<FeaturedMateCardDto> expected = List.of(FeaturedMateCardDto.builder().id(99L).build());
        when(partyService.getFeaturedMateCards(date, 4)).thenReturn(expected);

        List<FeaturedMateCardDto> result = adapter.getFeaturedMateCards(date, 4);

        assertThat(result).isSameAs(expected);
        verify(partyService).getFeaturedMateCards(date, 4);
    }
}
