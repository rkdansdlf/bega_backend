<div id="top"> <!-- HEADER STYLE: CLASSIC --> <div align="center">

# 백엔드

<em>BEGA 야구 커뮤니티 플랫폼의 핵심 엔진</em>

<!-- BADGES --> <img src="https://img.shields.io/github/last-commit/rkdansdlf/bega_backend?style=flat&logo=git&logoColor=white&color=0080ff" alt="last-commit"> <img src="https://img.shields.io/github/languages/top/rkdansdlf/bega_backend?style=flat&color=0080ff" alt="repo-top-language"> <img src="https://img.shields.io/github/languages/count/rkdansdlf/bega_backend?style=flat&color=0080ff" alt="repo-language-count">

<em>사용된 기술 스택:</em>

<img src="https://img.shields.io/badge/Java-ED8B00.svg?style=flat&logo=openjdk&logoColor=white" alt="Java"> <img src="https://img.shields.io/badge/Spring%20Boot-6DB33F.svg?style=flat&logo=Spring-Boot&logoColor=white" alt="Spring Boot"> <img src="https://img.shields.io/badge/PostgreSQL-316192.svg?style=flat&logo=postgresql&logoColor=white" alt="PostgreSQL"> <img src="https://img.shields.io/badge/Oracle-Autonomous%20DB-F80000.svg?style=flat&logo=oracle&logoColor=white" alt="Oracle Autonomous DB"> <img src="https://img.shields.io/badge/OCI-Object%20Storage-1F2A44.svg?style=flat&logo=oracle&logoColor=white" alt="OCI Object Storage"> <img src="https://img.shields.io/badge/Docker-2496ED.svg?style=flat&logo=Docker&logoColor=white" alt="Docker"> <img src="https://img.shields.io/badge/Gradle-02303A.svg?style=flat&logo=Gradle&logoColor=white" alt="Gradle"> <img src="https://img.shields.io/badge/Nginx-009639.svg?style=flat&logo=NGINX&logoColor=white" alt="Nginx"> <img src="https://img.shields.io/badge/Amazon%20AWS-232F3E.svg?style=flat&logo=Amazon-AWS&logoColor=white" alt="AWS"> </div> <br>

----------

## 목차

-   [개요](#개요)
-   [주요 기능](#주요-기능)
-   [아키텍처](#아키텍처)
-   [시작하기](#시작하기)
    -   [사전 요구사항](#사전-요구사항)
    -   [설치](#설치)
    -   [환경 설정](#환경-설정)
    -   [실행](#실행)
-   [API 문서](#api-문서)
-   [배포](#배포)

----------

## 개요

BEGA(Baseball Guide) 백엔드는 한국 야구 팬 커뮤니티 플랫폼의 핵심 REST API 서버입니다. Spring Boot로 구축되어 경기 데이터 관리, 사용자 상호작용, KBO 야구 정보를 제공하는 강력하고 확장 가능한 서비스를 제공합니다.

야구 데이터 운영 원칙:
- 야구 데이터의 소유자는 크롤러([KBO_playwright](https://github.com/rkdansdlf/KBO_playwright))이며, 이 저장소는 그 데이터를 읽기만 합니다.
- 외부 야구 웹 조회나 크롤링은 사용하지 않습니다.
- 내부 DB 동기화와 운영자 제공 데이터만 사용합니다.
- 데이터가 부족하거나 시즌 문맥이 어긋나면 `MANUAL_BASEBALL_DATA_REQUIRED` 계약으로 전환합니다.

**BEGA인 이유**

이 프로젝트는 다음을 지원하는 고성능 백엔드 인프라를 제공합니다:

-   ⚾ **KBO 경기 데이터:** 일정, 결과, 통계 관리 및 실시간 업데이트(예정)
-   🔐 **안전한 인증:** JWT 기반 토큰 인증 및 쿠키 세션 관리
-   📝 **경기 다이어리:** 관람 경험 기록 및 통계 시스템 제공
-   🤝 **메이트 매칭:** 같은 경기 관람 팬 연결 서비스
-   🏟️ **구장 가이드:** KBO 10개 구장 정보 및 좌석 안내
-   💬 **응원 게시판:** 댓글, 좋아요, 조회수, 필터링 기능
-   🎯 **예측 & 투표:** 시즌 순위 예측 및 경기 승부 투표
-   📦 **클라우드 저장소:** OCI Object Storage 기반 이미지 관리

----------

## 주요 기능

### 핵심 서비스

-   **사용자 관리**
    
    -   안전한 쿠키 처리를 통한 JWT 기반 인증
    -   사용자 프로필 및 설정
    -   소셜 기능 및 사용자 상호작용
-   **경기 데이터 서비스**
    
    -   KBO 경기 일정 및 결과
    -   팀 및 선수 통계
    -   과거 경기 데이터 및 분석
    -   실시간 경기 업데이트 (예정)
-   **커뮤니티 기능**
    
    -   **경기 다이어리:** 이미지와 함께 개인 경기 경험 기록
    -   **메이트 매칭:** 같은 경기를 관람하는 팬 찾기
    -   **응원 게시판:** 팀 응원 공유, 댓글, 좋아요, 조회 수 기능
    -   **필터링 시스템:** 팀별, 인기순 콘텐츠 정렬
-   **인터랙티브 기능**
    
    -   **순위 예측:** 시즌별 팀 순위 예측 투표
    -   **승부 투표:** 경기별 승패 예측 및 결과 집계
    -   **통계 대시보드:** 사용자의 메이트 및 다이어리 기분 통계
-   **구장 정보**
    
    -   종합 구장 가이드
    -   시설 세부사항 및 편의시설

### 기술적 특징

-   **데이터베이스 최적화**
    
    -   데이터베이스 수준 집계를 통한 효율적인 쿼리 설계
    -   N+1 쿼리 방지 전략
    -   커넥션 풀링 및 캐싱
-   **파일 관리**
    
    -   OCI Object Storage(S3 호환) 통합
    -   안전한 이미지 접근을 위한 서명 URL 생성
    -   이미지 업로드 및 삭제 처리
-   **API 아키텍처**
    
    -   REST API 설계
    -   포괄적인 에러 처리
    -   요청 검증 및 정제
    -   크로스 오리진 요청을 위한 CORS 설정

----------

## 아키텍처

### 기술 스택

-   **프레임워크:** Spring Boot 4.1
-   **언어:** Java 21
-   **데이터베이스:** PostgreSQL(`app-db`, 사용자 생성 데이터) + 별도 야구 데이터소스(`BASEBALL_DB_*`, 읽기 전용 사용)
-   **ORM:** Spring Data JPA
-   **보안:** Spring Security + JWT
-   **스토리지:** OCI Object Storage(S3 호환)
-   **웹 서버:** Nginx (리버스 프록시)
-   **배포:** AWS EC2
-   **컨테이너화:** Docker

### 시스템 설계

```
프론트엔드 (Cloudflare Worker 정적 자산)
    ↓ HTTPS
Nginx (EC2)
    ↓
Spring Boot 애플리케이션
    ├─ PostgreSQL (app-db: 사용자 생성 데이터)
    ├─ 야구 데이터소스 (BASEBALL_DB_*: 읽기 전용)
    └─ OCI Object Storage (이미지/파일)

```

----------

## 시작하기

### 사전 요구사항

이 프로젝트는 다음 종속성을 필요로 합니다:

-   **Java:** 21 (Gradle Wrapper가 포함되어 있어 Gradle은 따로 설치하지 않아도 됩니다)
-   **데이터베이스:** PostgreSQL (CI는 16으로 검증합니다). `local` 프로필은 H2 인메모리라 DB 없이도 실행됩니다
-   **컨테이너 런타임:** Docker (선택사항)

### 설치

소스에서 백엔드를 빌드하고 종속성을 설치합니다:

1.  **저장소 클론:**
    
    ```sh
    ❯ git clone https://github.com/rkdansdlf/bega_backend
    ```
    
2.  **프로젝트 디렉토리로 이동:** (Gradle 프로젝트는 `BEGA_PROJECT/` 아래에 있습니다)
    
    ```sh
    ❯ cd bega_backend/BEGA_PROJECT
    ```
    
3.  **종속성 설치:**
    
    **Gradle 사용:**
    
    ```sh
    ❯ ./gradlew build
    ```
    
    **Docker 사용:**
    
    ```sh
    ❯ docker build -t bega-backend .
    ```
    

### 환경 설정

Spring 프로필에 따라 필요한 설정이 다릅니다. 기본 프로필은 `dev`입니다.

| 프로필 | 용도 | 데이터베이스 |
|---|---|---|
| `local` | 빠른 로컬 확인 | H2 인메모리 (DB 설치 불필요) |
| `dev` | 기본 개발 환경 (`./gradlew bootRun` 기본값) | PostgreSQL (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`) |
| `prod` | 배포 환경 | PostgreSQL `app-db` + 별도 야구 데이터소스 |

공통 설정이 `.env`(현재 디렉토리 또는 저장소 루트)를 자동으로 읽으므로, 로컬에서는 `.env`에 값을 넣어 두면 됩니다.

**운영(`prod`)에서 반드시 주입해야 하는 값** — 누락되면 기동이 중단됩니다.

```sh
# 사용자 데이터 DB (app-db)
export SPRING_DATASOURCE_URL=jdbc:postgresql://<host>:5432/<db>
export SPRING_DATASOURCE_USERNAME=your_db_username
export SPRING_DATASOURCE_PASSWORD=your_db_password
export SPRING_DATASOURCE_DRIVER_CLASS_NAME=org.postgresql.Driver
export SPRING_FLYWAY_LOCATIONS=classpath:db/migration_postgresql/   # 데이터소스 엔진과 반드시 일치시킬 것

# 야구 데이터소스 (읽기 전용)
export BASEBALL_DB_URL=...
export BASEBALL_DB_USERNAME=...
export BASEBALL_DB_PASSWORD=...

# 인증/보안
export JWT_SECRET=your_jwt_secret
export OAUTH2_COOKIE_SECRET=your_cookie_secret
export APP_REFRESH_TOKEN_PEPPER=...        # 32자 이상
export APP_MAIL_ENABLED=true                # prod 에서 필수 (OAuth 이메일 확인/가입 인증 메일)

# OCI Object Storage (S3 호환)
export OCI_S3_ACCESS_KEY=your_access_key
export OCI_S3_SECRET_KEY=your_secret_key
export OCI_S3_ENDPOINT=https://<namespace>.compat.objectstorage.<region>.oraclecloud.com
export OCI_S3_REGION=ap-seoul-1
export OCI_S3_BUCKET=your_bucket
```

> `prod` 프로필은 쿠키 `Secure` 속성과 위 보안 값들을 기동 시 검증합니다(`SecurityStartupValidator`).
> 운영의 `SPRING_FLYWAY_LOCATIONS` 기본값은 레거시 Oracle 세트이므로, PostgreSQL을 쓸 때는 위처럼 명시해야 합니다.

**선택 설정**

-   `APP_AUTH_EMAIL_VERIFICATION_REQUIRED` (기본 `false`): `true`로 켜면 이메일 인증 전에는 비밀번호 로그인이 `403 EMAIL_NOT_VERIFIED`로 막힙니다. 인증 메일 링크를 처리하는 프런트 화면이 배포된 뒤에 켜세요.
-   `JOBRUNR_DASHBOARD_ENABLED` (기본 `false`): `prod`에서 켜려면 `JOBRUNR_DASHBOARD_USERNAME`과 12자 이상의 `JOBRUNR_DASHBOARD_PASSWORD`가 반드시 필요합니다. 없으면 기동이 거부됩니다.

### 실행

**Gradle 사용:**

```sh
# 기본(dev) 프로필 — PostgreSQL 연결 정보 필요
❯ ./gradlew bootRun

# DB 없이 빠르게 확인하려면 local 프로필 (H2 인메모리)
❯ ./gradlew bootRun --args='--spring.profiles.active=local'
```

**Docker 사용:**

```sh
❯ docker run -p 8080:8080 --env-file .env -e SPRING_PROFILES_ACTIVE=prod bega-backend
```

API는 `http://localhost:8080`에서 사용 가능합니다

### 검증 게이트

개발/CI에서 권장하는 백엔드 검증 명령:

```sh
# 전체 테스트
./gradlew test

# Flyway 중복 버전 + 오래된 빌드 리소스 빠른 검사 (DB 연결 불필요)
./gradlew migrationSafetyCheck

# Flyway 안전성 + 실제 스키마 검증 전체 검사 (DB 연결 필요)
./gradlew dbSafetyTest

# API 변경 후 OpenAPI 계약(contracts/openapi.*) 재생성
./gradlew updateOpenApiContract
```

----------

## API 문서

### 인증 엔드포인트

```
POST   /api/auth/signup       - 사용자 등록 (항상 LOCAL 계정으로 생성, 이미 등록된 이메일이면 409)
POST   /api/auth/login        - 사용자 로그인
POST   /api/auth/logout       - 사용자 로그아웃
GET    /oauth2/authorization/{provider} - 소셜 로그인 시작 (provider: google, kakao)
```
### 이메일 인증 엔드포인트
```
POST   /api/auth/email-verification/confirm - 메일 링크의 토큰으로 이메일 인증 완료
POST   /api/auth/email-verification/resend  - 인증 메일 재발송 (계정 존재 여부는 노출하지 않음)
```
> 가입 시 인증 메일이 발송됩니다. 인증 전 로그인 차단은 `APP_AUTH_EMAIL_VERIFICATION_REQUIRED=true`일 때만 동작합니다.

### 사용자 설정 엔드포인트
```
POST   /api/auth/password/reset/request - 비밀번호 재설정 이메일 발송
POST   /api/auth/password/reset/confirm - 비밀번호 재설정 완료
```
### 마이페이지 엔드포인트
```
GET    /api/diary/statistics  - 마이페이지 통계 조회

GET    /api/auth/mypage        - 프로필 정보 조회
PUT    /api/auth/mypage        - 프로필 정보 수정
POST   /api/profile/image      - 프로필 사진 업로드
```


### 경기 데이터 엔드포인트

```
GET    /api/kbo/schedule              - 경기 목록 조회
GET    /api/kbo/rankings/{seasonYear} - 시즌 팀 순위 조회
GET    /api/kbo/league-start-dates    - 리그 단계별 시작일 조회
```

### 커뮤니티 엔드포인트

**다이어리**
```
GET    /api/diary/entries     - 경기 다이어리 전체 조회
GET    /api/diary/games       - 경기 조회
POST   /api/diary/save        - 경기 다이어리 저장
GET    /api/diary/{id}        - 경기 다이어리 특정 조회
POST   /api/diary/{id}/images - 경기 다이어리 사진 저장
POST   /api/diary/{id}/modify - 경기 다이어리 수정
POST   /api/diary/{id}/delete - 경기 다이어리 삭제
```

**메이트 매칭** 
``` 
# 파티 관리 
GET    /api/parties                 - 파티 전체 조회 
GET    /api/parties/{id}            - 파티 특정 조회 
GET    /api/parties/status/{status} - 상태별 파티 조회 
GET    /api/parties/search          - 파티 검색 
GET    /api/parties/upcoming        - 경기 예정 파티 조회 
GET    /api/parties/my              - 내가 참여한 파티 조회 
GET    /api/parties/my/history      - 내가 참여한 파티 이력 조회 
GET    /api/parties/search-terms/popular?limit=5 - 최근 7일 인기 검색어 조회
POST   /api/parties/search-terms    - 검색어 일별 집계 기록(공개, 60 req/min/IP)
POST   /api/parties                 - 파티 생성 
PATCH  /api/parties/{id}            - 파티 업데이트 
DELETE /api/parties/{id}            - 파티 삭제 

# 신청 관리 
GET    /api/applications/party/{partyId}          - 파티별 신청 목록 조회 
GET    /api/applications/my                       - 내 신청 목록 조회 
GET    /api/applications/party/{partyId}/pending  - 대기중인 신청 목록 조회 
GET    /api/applications/party/{partyId}/approved - 승인된 신청 목록 조회 
GET    /api/applications/party/{partyId}/rejected - 거절된 신청 목록 조회 
POST   /api/applications                          - 신청 생성 
POST   /api/applications/{applicationId}/approve  - 신청 승인
POST   /api/applications/{applicationId}/reject   - 신청 거절 
DELETE /api/applications/{applicationId}          - 신청 취소 

# 체크인 
POST   /api/checkin - 체크인 
GET    /api/checkin/party/{partyId}       - 파티별 체크인 기록 조회 
GET    /api/checkin/user/{userId}         - 사용자별 체크인 기록 조회 
GET    /api/checkin/check                 - 체크인 여부 확인 
GET    /api/checkin/party/{partyId}/count - 파티별 체크인 인원 수 조회 

# 채팅 (WebSocket) 
POST   /api/chat/messages               - 메시지 전송 
GET    /api/chat/party/{partyId}        - 파티별 채팅 메시지 조회 
GET    /api/chat/party/{partyId}/latest - 파티별 최근 메시지 조회 

WebSocket: /chat/{partyId} 
Subscribe: /topic/party/{partyId} 
``` 

**응원 게시판** 
``` 
# 게시글 관리 
GET    /api/cheer/posts         - 게시글 목록 조회 
GET    /api/cheer/posts/{id}    - 게시글 상세 조회 
POST   /api/cheer/posts         - 게시글 작성 
PUT    /api/cheer/posts/{id}    - 게시글 수정 
DELETE /api/cheer/posts/{id}    - 게시글 삭제 

# 댓글 관리 
GET    /api/cheer/posts/{id}/comments    - 댓글 목록 조회
POST   /api/cheer/posts/{id}/comments    - 댓글 작성 
DELETE /api/cheer/comments/{commentId}   - 댓글 삭제 
POST   /api/cheer/posts/{postId}/comments/{parentCommentId}/replies 
- 대댓글 작성 

# 좋아요 
POST   /api/cheer/posts/{postId}/like       - 게시글 좋아요 
POST   /api/cheer/comments/{commentId}/like - 댓글 좋아요 

# 이미지 관리 
GET    /api/posts/{postId}/images        - 이미지 목록 조회 
POST   /api/posts/{postId}/images        - 이미지 업로드 
DELETE /api/images/{imageId}             - 이미지 삭제 
POST   /api/images/{imageId}/signed-url  - 서명된 URL 생성 
```

**응원 팀 테스트**
```
POST   /api/quiz/result       - 응원 팀 추천 테스트 결과 계산
```



### 투표 및 예측 엔드포인트

```
# 승부 예측
GET    /api/games/past             - 과거 경기 목록 조회
GET    /api/matches                - 특정 날짜의 경기 조회
POST   /api/predictions/vote       - 순위 예측 투표 or 투표 변경
GET    /api/predictions/status/{gameId}  - 특정 경기 실시간 투표 현황
DELETE /api/predictions/{gameId}   - 사용자 특정 경기 투표 취소
POST   /api/predictions/my-votes         - 사용자 투표 일괄 조회

> 사용자 투표 조회는 일괄 API(`POST /api/predictions/my-votes`)만 제공합니다. 단건 API(`GET /api/predictions/my-vote/{gameId}`)는 제거되었습니다.

### 예측 페이지 회귀 점검

- [x] 예측 페이지 진입 시 `GET /api/predictions/my-vote/{gameId}` 호출 0회
- [x] 예측 페이지 진입 시 `POST /api/predictions/my-votes` 호출 1회 이상
- [x] 예측 페이지에서 `my-vote` 단건 API 사용 재출현 시 경보

# 순위 예측
GET    /api/predictions/ranking/current-season - 현재 시즌 연도 조회
POST   /api/predictions/ranking          - 사용자 예측 결과 저장, 수정
GET    /api/predictions/ranking          - 특정 시즌 사용자 예측 결과 조회
GET    /api/predictions/ranking/share/{shareId}/{seasonYear} - 특정 시즌 사용자 예측 결과를 공유 목적으로 조회
```

### 구장 엔드포인트

```
GET    /api/stadiums     - 모든 구장 조회
GET    /api/stadiums/{stadiumsId}       - 구장 아이디로 조회
GET    /api/stadiums/name/{stadiumName} - 구장 이름으로 조회
GET    /api/stadiums/{stadiumId}/places - 구장 장소 조회
GET    /api/stadiums/name/{stadiumName}/places - 구장 이름으로 장소 조회
GET    /api/stadiums/places/all - 전체 장소 조회
```

----------

## 배포

### AWS EC2 배포

1.  **서버 설정:**
    
    ```sh
    # 시스템 업데이트
    sudo apt update && sudo apt upgrade -y
    
    # Docker 설치
    curl -fsSL https://get.docker.com -o get-docker.sh
    sudo sh get-docker.sh
    ```
    
2.  **SSL 인증서 (Let's Encrypt):**
    
    ```sh
    sudo apt install certbot
    sudo certbot certonly --standalone -d your-domain.com
    ```
    
3.  **Nginx 설정:**
    
    ```nginx
    server {
        listen 443 ssl;
        server_name your-domain.com;
        
        ssl_certificate /etc/letsencrypt/live/your-domain.com/fullchain.pem;
        ssl_certificate_key /etc/letsencrypt/live/your-domain.com/privkey.pem;
        
        location / {
            proxy_pass http://localhost:8080;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
        }
    }
    ```
    
4.  **애플리케이션 실행:**
    
    운영용 Compose 파일(`docker-compose.prod.yml`)은 이 저장소가 아니라 배포 저장소
    [KBO_platform-deploy](https://github.com/rkdansdlf/KBO_platform-deploy)에 있습니다. 그 저장소의 절차를 따르세요.
    Compose는 `SPRING_PROFILES_ACTIVE=prod`를 고정합니다. 프로필을 지정하지 않으면 `dev`로 기동하므로
    직접 컨테이너를 띄울 때는 반드시 `prod`를 지정하세요.
    

----------

<div align="left"><a href="#top">⬆ 돌아가기</a></div>

----------
