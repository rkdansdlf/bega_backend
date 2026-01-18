package com.example.cheerboard.service;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class CheerBattleService {

    // Team ID -> Vote Count
    private final Map<String, AtomicInteger> votes = new ConcurrentHashMap<>();

    // Initialize with 0 for all teams (optional, can be lazy)
    public CheerBattleService() {
        // Initialize KBO teams
        String[] teams = { "LG", "OB", "SK", "KT", "WO", "NC", "SS", "LT", "HT", "HH" };
        for (String team : teams) {
            votes.put(team, new AtomicInteger(0));
        }
    }

    public int vote(String teamId) {
        return votes.computeIfAbsent(teamId, k -> new AtomicInteger(0)).incrementAndGet();
    }

    public Map<String, Integer> getStats() {
        Map<String, Integer> result = new java.util.HashMap<>();
        votes.forEach((k, v) -> result.put(k, v.get()));
        return result;
    }
}
