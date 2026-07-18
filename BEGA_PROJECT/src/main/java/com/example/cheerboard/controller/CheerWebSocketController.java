package com.example.cheerboard.controller;

import com.example.common.realtime.RealtimeMessagePublisher;
import com.example.cheerboard.service.CheerBattleService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.stereotype.Controller;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class CheerWebSocketController {

    private final CheerBattleService battleService;
    private final RealtimeMessagePublisher realtimeMessagePublisher;

    @MessageMapping("/battle/vote/{gameId}")
    public void vote(
            @org.springframework.messaging.handler.annotation.DestinationVariable String gameId,
            String teamId,
            java.security.Principal principal) {

        if (principal == null) {
            throw new IllegalArgumentException("로그인이 필요합니다.");
        }

        Long userId;
        try {
            userId = Long.valueOf(principal.getName());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("잘못된 인증 정보입니다.");
        }

        // Increment vote with point deduction
        battleService.vote(gameId, teamId, userId);

        Map<String, Integer> stats = battleService.getGameStats(gameId);
        realtimeMessagePublisher.broadcast("/topic/battle/" + gameId, stats);
    }
}
