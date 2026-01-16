package com.example.demo.service;

import com.example.demo.entity.UserEntity;
import com.example.demo.repo.UserRepository;

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

    // LoginFilter에서 받은 이메일로 사용자 정보 가져오기
    // loadUserByUsername 메서드의 파라미터는 'username', 하지만 실제론 이메일로 처리
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
