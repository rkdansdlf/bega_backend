package com.example.cheerboard.controller;

import com.example.cheerboard.config.CurrentUser;
import com.example.cheerboard.domain.AppUser;
import com.example.cheerboard.repo.AppUserRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dev/me")
@RequiredArgsConstructor
public class DevMeController {
    private final CurrentUser current;
    private final AppUserRepo users;

    record MeRes(Long id, String email, String displayName, String favoriteTeamId, String role) {}
    record UpdateTeamReq(String teamId) {}

    @GetMapping
    public MeRes me() {
        AppUser me = current.get();
        return new MeRes(me.getId(), me.getEmail(), me.getDisplayName(), me.getFavoriteTeamId(), me.getRole());
    }

    @PatchMapping("/favorite-team")
    public MeRes setTeam(@RequestBody UpdateTeamReq req) {
        AppUser me = current.get();
        me.setFavoriteTeamId(req.teamId());
        users.save(me);
        return new MeRes(me.getId(), me.getEmail(), me.getDisplayName(), me.getFavoriteTeamId(), me.getRole());
    }
}