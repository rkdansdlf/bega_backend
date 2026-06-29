package com.example.homepage;

import com.example.kbo.validation.ManualBaseballDataRequest;
import java.util.List;

record HomePageGamesResult(
        List<HomePageGameDto> games,
        ManualBaseballDataRequest manualDataRequest) {

    static HomePageGamesResult success(List<HomePageGameDto> games) {
        return new HomePageGamesResult(games == null ? List.of() : games, null);
    }

    static HomePageGamesResult empty() {
        return success(List.of());
    }
}
