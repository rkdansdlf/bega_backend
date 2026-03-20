package com.example.auth.service;

import com.example.auth.entity.UserEntity;
import com.example.auth.repository.UserRepository;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public CustomUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Spring Security의 username 파라미터를 이메일로 해석한다.
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {

        // 1. DB에서 이메일을 기준으로 사용자 조회.
        UserEntity userData = userRepository.findByEmail(username)
                .orElseThrow(() -> {
                    return new UsernameNotFoundException("사용자 이메일 " + username + "을 찾을 수 없습니다.");
                });

        // 사용자를 찾으면, UserDetails 구현체인 CustomUserDetails에 담아서 보내기
        return new CustomUserDetails(userData);
    }
}
