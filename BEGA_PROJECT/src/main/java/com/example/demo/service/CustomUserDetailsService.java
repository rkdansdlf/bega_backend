package com.example.demo.service;

import com.example.demo.entity.UserEntity;
import com.example.demo.repo.UserRepository;
import com.example.demo.service.CustomUserDetails;
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

    /**
     * LoginFilter에서 전달받은 식별자(이메일)를 사용하여 DB에서 사용자 정보를 로드합니다.
     * Spring Security의 loadUserByUsername 메서드의 파라미터는 'username'이지만,
     * 실제로는 이메일 식별자를 전달받아 처리합니다.
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        
        // 1. DB에서 이메일(username 파라미터로 넘어온 값)을 기준으로 사용자 조회.
        // UserRepository의 findByEmail 메서드와 Optional 처리를 사용합니다.
        UserEntity userData = userRepository.findByEmail(username)
                .orElseThrow(() -> {
                    // System.err를 사용하여 콘솔에 에러 로그 출력
                    System.err.println("🚨 사용자 인증 실패: " + username + " (이메일)을(를) DB에서 찾을 수 없습니다.");
                    return new UsernameNotFoundException("사용자 이메일 " + username + "을(를) 찾을 수 없습니다.");
                });

		// 2. 사용자를 찾았다면, UserDetails 구현체인 CustomUserDetails에 담아서 반환
        // CustomUserDetails는 UserEntity를 받아서 UserDetails 객체를 생성합니다.
        return new CustomUserDetails(userData);
    }
}
