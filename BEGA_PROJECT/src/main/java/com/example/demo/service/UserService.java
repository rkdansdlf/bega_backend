package com.example.demo.service;

import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.demo.dto.UserDto;
import com.example.demo.entity.UserEntity;
import com.example.demo.entity.Role;
import com.example.demo.jwt.JWTUtil;
import com.example.demo.repo.UserRepository;


@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class); // 로거 추가

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final JWTUtil jwtUtil;
    private static final long ACCESS_EXPIRATION_TIME = 1000L * 60 * 60;

    public UserService(UserRepository userRepository, BCryptPasswordEncoder bCryptPasswordEncoder, JWTUtil jwtUtil) {
        this.userRepository = userRepository;
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.jwtUtil = jwtUtil;
    }

    // 기존 관리자 회원가입 메서드 (로직 변경 없음)
    public void joinProcess(UserDto userDto) {
        String username = userDto.getName();
        String password = userDto.getPassword();

        Boolean isExist = userRepository.existsByUsername(username);

        if (isExist) {
            return;
        }

        UserEntity data = new UserEntity();
        data.setUsername(username);
        data.setPassword(bCryptPasswordEncoder.encode(password));
        data.setRole("ROLE_ADMIN");

        userRepository.save(data);
    }
    
    /**
     * 선호 팀 이름(한글)에 따라 String 타입의 Role Key를 결정하는 헬퍼 메서드
     */
    private String getRoleKeyByFavoriteTeam(String teamName) {
        if (teamName == null || "없음".equals(teamName) || teamName.trim().isEmpty()) {
            return Role.USER.getKey();
        }

        Role selectedRoleEnum = switch (teamName) {
            case "삼성 라이온즈" -> Role.Role_SS;
            case "롯데 자이언츠" -> Role.Role_LT;
            case "LG 트윈스" -> Role.Role_LG;
            case "두산 베어스" -> Role.Role_OB;
            case "키움 히어로즈" -> Role.Role_WO;
            case "한화 이글스" -> Role.Role_HH;
            case "SSG 랜더스" -> Role.Role_SK;
            case "NC 다이노스" -> Role.Role_NC;
            case "KT 위즈" -> Role.Role_KT;
            case "기아 타이거즈" -> Role.Role_HT;
            default -> Role.USER;
        };
        
        return selectedRoleEnum.getKey();
    }


    /**
     * 일반 회원가입 및 소셜 연동/역연동 처리 로직 (제한적 연동 정책 적용)
     */
    @Transactional
    public void signUp(UserDto userDto) {
        
        log.info("--- [SignUp] Attempt ---");
        log.info("DTO Email: {}", userDto.getEmail());

        // 1. 이메일로 기존 사용자 조회
        Optional<UserEntity> existingUserOptional = userRepository.findByEmail(userDto.getEmail());

        // A. 기존 사용자가 존재하는 경우 (중복 처리)
        if (existingUserOptional.isPresent()) {
            UserEntity existingUser = existingUserOptional.get();
            
            log.info("Existing User Found. ID: {}, DB Email: {}, DB Provider: {}", 
                     existingUser.getId(), existingUser.getEmail(), existingUser.getProvider());
            
            boolean isLocalSignupAttempt = userDto.getProvider() == null || "LOCAL".equals(userDto.getProvider());
            
            // 🚨 로컬 회원가입 시도 시
            if (isLocalSignupAttempt) {
                if (existingUser.isOAuth2User()) {
                    // **Case 1: Provider가 google, kakao 등 소셜인 경우**
                    log.warn("Attempted Local Signup with existing Social Account. Blocked.");
                    throw new IllegalArgumentException("이 이메일은 소셜 로그인 계정으로 사용 중입니다. 소셜 로그인을 이용해 주세요.");
                } else {
                    // **Case 2: Provider가 LOCAL 또는 null인 경우**
                    log.warn("Attempted Local Signup with existing Local/Linked Account. Blocked.");
                    throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
                }
            } 
            
            // B. 소셜 로그인 시도 (userDto.providerId != null)
            else if (userDto.getProviderId() != null) {
                // 🚀 순방향 연동: 기존 로컬 계정에 소셜 정보 추가 (기존 로직 유지)
                if (existingUser.getProvider() == null || "LOCAL".equals(existingUser.getProvider())) {
                    log.info("Executing Forward Link: Adding Social Provider '{}' to Local Account. Email: {}", 
                             userDto.getProvider(), userDto.getEmail());
                    existingUser.setProvider(userDto.getProvider());
                    existingUser.setProviderId(userDto.getProviderId());
                    userRepository.save(existingUser);
                }
                // 이미 연동된 계정이거나, 순방향 연동 완료 후에는 아무것도 하지 않고 종료
                return;
            }
            
            return; // 예외를 던지거나 연동을 처리했으므로 종료
        }

        // 2. 이메일이 존재하지 않는 경우 (신규 회원가입)
        log.info("New User Creation: Email '{}' not found in DB. Creating new account.", userDto.getEmail());

        // 선호 팀에 따라 Role 결정 및 String Key 추출
        String favoriteTeam = userDto.getFavoriteTeam();
        String assignedRoleKey = getRoleKeyByFavoriteTeam(favoriteTeam);
        
        // 비밀번호 암호화 (로컬 가입 시에만 필요)
        String encodedPassword = null;
        if (userDto.getPassword() != null) {
             encodedPassword = bCryptPasswordEncoder.encode(userDto.getPassword());
        }

        // 3. UserEntity 생성 및 DB 저장
        UserEntity user = UserEntity.builder()
                .name(userDto.getUsername())
                .email(userDto.getEmail())
                .password(encodedPassword) 
                .favoriteTeam(favoriteTeam)
                .role(assignedRoleKey)             
                .provider(userDto.getProvider() != null ? userDto.getProvider() : "LOCAL")
                .providerId(userDto.getProviderId())
                .build();

        userRepository.save(user);
        log.info("New account saved. Email: {}, ID: {}", user.getEmail(), user.getId());
    }
    
    // ... (authenticateAndGetToken 및 isEmailExists, findUserByEmail 메서드는 변경 없음) ...

    @Transactional(readOnly = true)
    public Map<String, Object> authenticateAndGetToken(String email, String password) {
        
        // 1. 이메일로 사용자 조회
        Optional<UserEntity> userOptional = userRepository.findByEmail(email);
        
        if (userOptional.isEmpty()) {
            throw new IllegalArgumentException("존재하지 않는 사용자입니다.");
        }
        
        UserEntity user = userOptional.get();
        
        // 2. 비밀번호 검증 (로컬 로그인이 가능한 경우에만 비밀번호 검증)
        if (user.getPassword() != null && !bCryptPasswordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        
        // 비밀번호가 null이면 소셜 계정이므로, 로컬 로그인을 시도하면 비밀번호가 없다는 오류를 발생시킵니다.
        if (user.getPassword() == null) {
            throw new IllegalArgumentException("이 계정은 소셜 로그인 전용입니다. 비밀번호로 로그인할 수 없습니다.");
        }

        // 3. 인증 성공 시 JWT 토큰 생성 및 데이터 반환
        
        String accessToken = jwtUtil.createJwt(
            user.getEmail(),
            user.getRole(),
            ACCESS_EXPIRATION_TIME
        );
        
        return Map.of(
            "accessToken", accessToken, 
            "username", user.getName()
        );
    }

    /**
     * 이메일 중복 체크 (컨트롤러에서 사용)
     */
    @Transactional(readOnly = true)
    public boolean isEmailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    /**
     * CustomOAuth2UserService에서 최종 사용자 정보(UserDto)를 가져오기 위한 메서드 추가
     */
    @Transactional(readOnly = true)
    public UserDto findUserByEmail(String email) {
        return userRepository.findByEmail(email)
            .map(userEntity -> UserDto.builder()
                .id(userEntity.getId())
                .username(userEntity.getName())
                .email(userEntity.getEmail())
                // 비밀번호는 노출하지 않음
                .favoriteTeam(userEntity.getFavoriteTeam())
                .role(userEntity.getRole())
                .provider(userEntity.getProvider())
                .providerId(userEntity.getProviderId())
                .build())
            .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
    }
}
