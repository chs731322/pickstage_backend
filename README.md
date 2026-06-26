# 🎵 Pickstage Backend

> 페스티벌 · 콘서트 정보 통합 웹 서비스 **Pickstage**의 백엔드 서버입니다.

---

## 🛠 기술 스택

| 분류 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1.0 |
| ORM | Spring Data JPA / Hibernate |
| Database | MySQL 8.0 (AWS EC2) |
| Build Tool | Maven |
| 인증 | Spring Security + JWT |
| 기타 | Lombok, DevTools |

---

## 📁 프로젝트 구조

```
src/main/java/com/pickstage/backend/
├── PickstageApplication.java       # 앱 진입점
│
├── controller/                     # API 엔드포인트 (요청/응답 처리)
├── service/                        # 비즈니스 로직
├── repository/                     # DB 접근 (JPA Repository)
├── entity/                         # DB 테이블과 매핑되는 Java 클래스
├── dto/                            # 요청/응답 데이터 객체
│
└── global/                         # 공통 모듈
    ├── config/                     # Security, JWT 등 설정
    ├── exception/                  # 공통 예외 처리
    ├── response/                   # 공통 응답 포맷 (ApiResponse)
    └── util/                       # 유틸 클래스
```

---

## 📋 공통 응답 포맷

모든 API는 아래 형식으로 응답합니다.

**성공**
```json
{
  "success": true,
  "data": { ... },
  "message": null
}
```

**실패**
```json
{
  "success": false,
  "data": null,
  "message": "에러 메시지"
}
```

---

## 🗂 데이터베이스

- MySQL 8.0 기반 / 총 20개 테이블
- 1차 MVP: 13개 테이블 (회원, 공연, 공연장, 아티스트, 찜, 후기 등)
- 2차 MVP: 7개 테이블 (알림, 체크리스트, 커뮤니티, 공지사항)

### 설계 원칙
- 공연 데이터는 `source` 컬럼으로 출처 구분 (`kopis` / `crawl` / `admin`)
- 외부 데이터 중복 방지를 위해 `source + source_id` UNIQUE 제약 적용
- 회원·후기·게시글·댓글은 소프트 삭제(`deleted_at`) 적용
- JWT 기반 인증을 고려하여 Refresh Token 저장 구조 포함

---

## ⚙️ 환경 설정

### application.properties (공통)
```properties
spring.application.name=pickstage
spring.profiles.active=local

spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true
spring.jpa.open-in-view=false
```

### application-local.properties (로컬 전용, git 제외)
```properties
spring.datasource.url=jdbc:mysql://{HOST}:3306/{DB_NAME}?serverTimezone=Asia/Seoul&characterEncoding=UTF-8
spring.datasource.username={USERNAME}
spring.datasource.password={PASSWORD}
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

spring.jpa.hibernate.ddl-auto=none
```

> `application-local.properties`는 `.gitignore`에 등록되어 있어 git에 올라가지 않습니다.

---

## 🚀 실행 방법

```bash
# 1. 프로젝트 클론
git clone https://github.com/chs731322/pickstage_backend.git
cd pickstage_backend

# 2. application-local.properties 생성 후 DB 정보 입력
# src/main/resources/application-local.properties

# 3. 빌드 및 실행
./mvnw spring-boot:run
```

실행 후 `http://localhost:8080` 접속 확인

---

## 👥 팀원 역할

| 이름 | 역할 |
|---|---|
| 해솔 | 인프라(AWS EC2·RDS·S3), Spring Security·JWT, KOPIS API 연동, 프로젝트 총괄 |
| 연서 | 1차 도메인 API 개발 (회원, 공연, 찜, 후기) |

---

## 📌 API 명세서

> Notion에서 관리합니다.
> [API 명세서 바로가기](https://app.notion.com/p/33a8e993af7280a1832ee005a148724d)

### Base URL
```
http://localhost:8080  (개발)
https://api.pickstage.com  (운영 예정)
```

### 인증 방식
```
Authorization: Bearer {accessToken}
```

### 주요 도메인

| 도메인 | Base Path | 담당 |
|---|---|---|
| 인증 | `/api/auth` | 해솔 |
| 공연 | `/api/concerts` | 연서 |
| 공연장 | `/api/venues` | 연서 |
| 아티스트 | `/api/artists` | 연서 |
| 찜 | `/api/bookmarks` | 연서 |
| 후기 | `/api/reviews` | 연서 |
