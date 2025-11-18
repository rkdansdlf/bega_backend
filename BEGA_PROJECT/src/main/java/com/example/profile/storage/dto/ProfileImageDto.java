package com.example.profile.storage.dto;

/**
 * 프로필 이미지 응답 DTO
 */
public record ProfileImageDto(
		Long userId,
	    String storagePath,
	    String publicUrl,
	    String mimeType,
	    Long bytes
	    ) {

}
