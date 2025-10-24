package com.example.demo.service;

import com.example.demo.entity.UserEntity;
import com.example.demo.repo.UserRepository;
import com.example.demo.security.CustomUserDetails;
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

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
				
		// 1. DB에서 조회
        UserEntity userData = userRepository.findByUsername(username);

        // 2. [핵심 수정]: 사용자를 찾지 못하면 예외를 던집니다.
        if (userData == null) {
            System.err.println("🚨 사용자 인증 실패: " + username + "을(를) DB에서 찾을 수 없습니다.");
            throw new UsernameNotFoundException("사용자 이름 " + username + "을(를) 찾을 수 없습니다.");
        }

		// 3. 사용자를 찾았다면, UserDetails에 담아서 반환
        return new CustomUserDetails(userData);
    }
}
