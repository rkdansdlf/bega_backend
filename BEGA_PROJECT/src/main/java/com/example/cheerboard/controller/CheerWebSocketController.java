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

    @MessageMapping("/battle/vote")
    @SendTo("/topic/battle/stats")
    public Map<String, Integer> vote(String teamId) {
        // Increment vote
        battleService.vote(teamId);
        // Return updated stats for all teams
        return battleService.getStats();
    }
}
