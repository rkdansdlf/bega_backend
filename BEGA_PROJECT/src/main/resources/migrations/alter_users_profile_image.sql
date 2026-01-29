-- OCI 프로필 이미지 URL 길이를 2048자로 확장
ALTER TABLE users MODIFY profile_image_url VARCHAR2(2048 CHAR);
