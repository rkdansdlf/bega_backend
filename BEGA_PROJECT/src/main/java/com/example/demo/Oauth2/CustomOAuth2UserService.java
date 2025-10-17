package com.example.demo.Oauth2;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import com.example.demo.dto.CustomOAuth2User;
import com.example.demo.dto.GoogleResponse;
import com.example.demo.dto.KaKaoResponse;
import com.example.demo.dto.OAuth2Response;
import com.example.demo.dto.UserDto;
import com.example.demo.entity.UserEntity;
import com.example.demo.repo.UserRepository;



@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {
	
	 private final UserRepository userRepository;

	    public CustomOAuth2UserService(UserRepository userRepository) {

	        this.userRepository = userRepository;
	    }

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {

        OAuth2User oAuth2User = super.loadUser(userRequest); 

        System.out.println("🚨 oAuth2User Attributes: " + oAuth2User.getAttributes());
        System.out.println(oAuth2User); 
        
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        System.out.println(registrationId);
        OAuth2Response oAuth2Response = null;
        if (registrationId.equals("kakao")) {
        	System.out.println("카카오 로그인 완료");

            oAuth2Response = new KaKaoResponse(oAuth2User.getAttributes());
        }
        else if (registrationId.equals("google")) {
        	System.out.println("구글 로그인 완료");

            oAuth2Response = new GoogleResponse(oAuth2User.getAttributes());
        }
        else {

            return null;
        }

		//리소스 서버에서 발급 받은 정보로 사용자를 특정할 아이디값을 만듬
        String username = oAuth2Response.getProvider()+" "+oAuth2Response.getProviderId();
        UserEntity existData = userRepository.findByUsername(username);

        if (existData == null) {

            UserEntity userEntity = new UserEntity();
            userEntity.setUsername(username);
            userEntity.setEmail(oAuth2Response.getEmail());
            userEntity.setName(oAuth2Response.getName());
            userEntity.setRole("ROLE_USER");

            userRepository.save(userEntity);

            UserDto userDto = new UserDto();
            userDto.setUsername(username);
            userDto.setName(oAuth2Response.getName());
            userDto.setRole("ROLE_USER");

            return new CustomOAuth2User(userDto);
        }
        else {

            existData.setEmail(oAuth2Response.getEmail());
            existData.setName(oAuth2Response.getName());

            userRepository.save(existData);

            UserDto userDto = new UserDto();
            userDto.setUsername(existData.getUsername());
            userDto.setName(oAuth2Response.getName());
            userDto.setRole(existData.getRole());

            return new CustomOAuth2User(userDto);
        }
    }
}