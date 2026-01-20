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
            @org.springframework.messaging.handler.annotation.DestinationVariable String gameId,
            String teamId,
            java.security.Principal principal) {

        if (principal == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }

        // Increment vote with point deduction
        battleService.vote(gameId, teamId, principal.getName());

        // Return updated stats for this game
        return battleService.getGameStats(gameId);
    }
}
