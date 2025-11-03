package com.example.demo.service;

import java.util.Map;
import java.util.Optional;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.example.demo.dto.UserDto;
import com.example.demo.dto.SignupDto; 
import com.example.demo.entity.UserEntity;
import com.example.demo.entity.TeamEntity; 
import com.example.demo.entity.Role;
import com.example.demo.jwt.JWTUtil;
import com.example.demo.repo.UserRepository;
import com.example.demo.repo.TeamRepository; 


@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class); 

    private final UserRepository userRepository;
    private final TeamRepository teamRepository; 
    private final BCryptPasswordEncoder bCryptPasswordEncoder;
    private final JWTUtil jwtUtil;
    private static final long ACCESS_EXPIRATION_TIME = 1000L * 60 * 60;

    public UserService(UserRepository userRepository, TeamRepository teamRepository, BCryptPasswordEncoder bCryptPasswordEncoder, JWTUtil jwtUtil) {
        this.userRepository = userRepository;
        this.teamRepository = teamRepository; 
        this.bCryptPasswordEncoder = bCryptPasswordEncoder;
        this.jwtUtil = jwtUtil;
    }
    
    // * 선호 팀 이름(한글)에 따라 String 타입의 Role Key를 결정하는 헬퍼 메서드
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
    
 //[신규 추가] 선호 팀 ID(약어)에 따라 Role Key를 결정하는 헬퍼 메서드 ⭐
    private String getRoleKeyByTeamId(String teamId) {
        if (teamId == null || teamId.trim().isEmpty()) {
            return Role.USER.getKey(); // 팀 선택 안 할 시 ROLE_USER
        }

        // Team ID("KT") -> "ROLE_KT" 형태로 변환합니다.
        // Role Enum의 키 정의 방식(ROLE_약칭)과 일치하도록 생성합니다.
        return "ROLE_" + teamId.toUpperCase();
    }
    
    private String getTeamIdByFavoriteTeamName(String teamName) {
        if (teamName == null || "없음".equals(teamName) || teamName.trim().isEmpty()) {
            return null;
        }

        return switch (teamName) {
            case "삼성 라이온즈" -> "SS";
            case "롯데 자이언츠" -> "LT";
            case "LG 트윈스" -> "LG";
            case "두산 베어스" -> "OB";
            case "키움 히어로즈" -> "WO";
            case "한화 이글스" -> "HH";
            case "SSG 랜더스" -> "SK";
            case "NC 다이노스" -> "NC";
            case "KT 위즈" -> "KT";
            case "기아 타이거즈" -> "HT";
            default -> null; 
        };
    }

    // [MyPage 핵심 메서드]
    @Transactional(readOnly = true)
    public UserEntity findUserById(Long id) {
        return userRepository.findById(id)
                // ID에 해당하는 사용자가 없으면 런타임 예외를 발생시킵니다.
                .orElseThrow(() -> new RuntimeException("ID " + id + "에 해당하는 사용자가 없습니다."));
    }

    @Transactional
    public UserEntity updateProfile(Long id, String nickname, String profileImageUrl, String favoriteTeamId) {
        // 1. 사용자 조회 (없으면 예외 발생)
        UserEntity user = findUserById(id); 

        // 2. 닉네임 및 이미지 업데이트
        user.setName(nickname);
        user.setProfileImageUrl(profileImageUrl);

        // 3. 응원팀 업데이트
        if (favoriteTeamId != null && !favoriteTeamId.trim().isEmpty()) {
            // 팀 ID가 유효한 경우, TeamEntity를 조회하여 매핑합니다.
            TeamEntity favoriteTeam = teamRepository.findById(favoriteTeamId)
                // TeamEntity가 없으면 프로필 수정 실패
                .orElseThrow(() -> new RuntimeException("유효하지 않은 응원팀 ID입니다: " + favoriteTeamId));
            
            user.setFavoriteTeam(favoriteTeam); 
        } else {
            // favoriteTeamId가 null이거나 비어있으면 (프론트에서 '없음'을 선택), TeamEntity를 null로 설정합니다.
            user.setFavoriteTeam(null);
        }
        
        // ⭐ 4. Role 업데이트: 추가된 헬퍼 메서드를 사용합니다. ⭐
        String newRoleKey = getRoleKeyByTeamId(favoriteTeamId); 
        user.setRole(newRoleKey);
        
        // 5. DB에 변경 사항 저장 및 업데이트된 Entity 반환 (핵심 수정 부분)
        // save()를 명시적으로 호출하고 반환하여, Controller가 최신 Role 값을 가진 Entity를 사용하도록 보장합니다.
        return userRepository.save(user);
    }

    // [회원가입 로직: SignupDto 사용]
    @Transactional
    public UserEntity saveUser(SignupDto signupDto) {
        // 1. 비밀번호 일치 확인 (SignupDto에만 있는 로직)
        if (!signupDto.getPassword().equals(signupDto.getConfirmPassword())) {
             throw new IllegalArgumentException("비밀번호와 비밀번호 확인이 일치하지 않습니다.");
        }
        
        // 2. DTO 변환 후 핵심 로직 호출
        UserDto userDto = signupDto.toUserDto();
        this.signUp(userDto);
        
        // 새로 가입된 사용자를 다시 찾아서 반환 (ID가 포함된 엔티티 반환)
        return userRepository.findByEmail(userDto.getEmail())
            .orElseThrow(() -> new RuntimeException("회원가입 후 사용자 조회 실패"));
    }



   //  * [핵심 로직] 일반 회원가입 및 소셜 연동/역연동 처리 로직.
    @Transactional
    public void signUp(UserDto userDto) {
        
        log.info("--- [SignUp] Attempt ---");
        log.info("DTO Email: {}", userDto.getEmail());

        // 1. 이메일로 기존 사용자 조회
        Optional<UserEntity> existingUserOptional = userRepository.findByEmail(userDto.getEmail());

        // A. 기존 사용자가 존재하는 경우 (중복 처리 및 연동)
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
            
            return; 
        }

        // 2. 이메일이 존재하지 않는 경우 (신규 회원가입)
        log.info("New User Creation: Email '{}' not found in DB. Creating new account.", userDto.getEmail());

        String favoriteTeamName = userDto.getFavoriteTeam();
        String assignedRoleKey = getRoleKeyByFavoriteTeam(favoriteTeamName);
        String favoriteTeamId = getTeamIdByFavoriteTeamName(favoriteTeamName);

        // 2-3. TeamEntity 조회
        TeamEntity favoriteTeam = null;
        if (favoriteTeamId != null) {
            log.info("Fetching TeamEntity with ID: {}", favoriteTeamId);
            favoriteTeam = teamRepository.findById(favoriteTeamId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 팀 ID입니다: " + favoriteTeamId));
        }
        
        // 비밀번호 암호화 (로컬 가입 시에만 필요)
        String encodedPassword = null;
        if (userDto.getPassword() != null) {
             encodedPassword = bCryptPasswordEncoder.encode(userDto.getPassword());
        }

        // 3. UserEntity 생성 및 DB 저장
        UserEntity user = UserEntity.builder()
                .name(userDto.getName()) 
                .email(userDto.getEmail())
                .password(encodedPassword) 
                .favoriteTeam(favoriteTeam) // TeamEntity 객체 설정
                .role(assignedRoleKey)             
                .provider(userDto.getProvider() != null ? userDto.getProvider() : "LOCAL")
                .providerId(userDto.getProviderId())
                .build();

        userRepository.save(user);
        log.info("New account saved. Email: {}, ID: {}", user.getEmail(), user.getId());
    }
    

    @Transactional(readOnly = true)
    public Map<String, Object> authenticateAndGetToken(String email, String password) {
        
        Optional<UserEntity> userOptional = userRepository.findByEmail(email);
        
        if (userOptional.isEmpty()) {
            throw new IllegalArgumentException("존재하지 않는 사용자입니다.");
        }
        
        UserEntity user = userOptional.get();
        
        // 2. 비밀번호 검증 (로컬 로그인이 가능한 경우에만 비밀번호 검증)
        if (user.getPassword() != null && !bCryptPasswordEncoder.matches(password, user.getPassword())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        
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
            "name", user.getName()
        );
    }


// 이메일 중복 체크 (컨트롤러에서 사용)

    @Transactional(readOnly = true)
    public boolean isEmailExists(String email) {
        return userRepository.existsByEmail(email);
    }

    //[JWTFilter 지원] 이메일로 Long ID를 조회하는 메서드
    @Transactional(readOnly = true)
    public Long getUserIdByEmail(String email) {
        return userRepository.findByEmail(email)
                .map(UserEntity::getId)
                .orElseThrow(() -> new IllegalArgumentException("이메일로 사용자를 찾을 수 없습니다: " + email));
    }


//  CustomOAuth2UserService에서 최종 사용자 정보(UserDto)를 가져오기 위한 메서드 추가
    @Transactional(readOnly = true)
    public UserDto findUserByEmail(String email) {
        return userRepository.findByEmail(email)
            .map(userEntity -> UserDto.builder()
                .id(userEntity.getId())
                .name(userEntity.getName()) 
                .email(userEntity.getEmail())
                // UserEntity에 추가된 getFavoriteTeamId() 사용 가정 (null-safe)
                .favoriteTeam(userEntity.getFavoriteTeamId()) 
                .role(userEntity.getRole())
                .provider(userEntity.getProvider())
                .providerId(userEntity.getProviderId())
                .build())
            .orElseThrow(() -> new IllegalArgumentException("사용자 정보를 찾을 수 없습니다."));
    }
}
