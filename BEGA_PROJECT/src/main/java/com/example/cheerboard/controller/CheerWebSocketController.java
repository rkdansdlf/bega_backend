package com.example.cheerboard.controller;

import com.example.cheerboard.service.CheerBattleService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class CheerWebSocketController {

    private final CheerBattleService battleService;

    @MessageMapping("/battle/vote/{gameId}")
    @SendTo("/topic/battle/{gameId}")
    public Map<String, Integer> vote(
            @org.springframework.messaging.handler.annotation.DestinationVariable String gameId, String teamId) {
        // Increment vote
        battleService.vote(gameId, teamId);
        // Return updated stats for this game
        return battleService.getGameStats(gameId);
    }
}
