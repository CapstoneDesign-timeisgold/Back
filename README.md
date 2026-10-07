# Jiki

**약속 참여 여부와 도착 상태에 따라 벌금과 보상을 정산하는 서비스**

친구를 약속에 초대하고 참여 여부와 도착 상태를 관리하는 서비스입니다. 참여 마감 이후 정산 대상을 확정하고, 약속 결과에 따라 벌금과 보상을 계산해 내부 잔액과 거래 내역에 반영합니다.

## 주요 기능

| 기능 | 설명 |
| --- | --- |
| 약속 관리 | 약속 생성과 목록 및 상세 조회, 삭제 |
| 참여 관리 | 친구 초대, 초대 수락과 거절, 참여 취소, 참여 마감 설정 |
| 도착 상태 관리 | 참여자의 도착 상태를 기록하고 약속 결과에 반영 |
| 벌금과 보상 정산 | 참여 여부와 도착 상태를 기준으로 금액을 계산하고 내부 잔액에 반영 |
| 정산 내역 조회 | 약속별 정산 결과와 개인 거래 내역 조회 |
| 회원과 친구 관리 | 회원가입과 로그인, JWT 인증, 친구 요청과 수락 및 거절, 친구 목록 조회 |

## 담당 역할

**강현준: 백엔드 개발, 팀장**

- 약속 생성과 조회, 참여자 초대 및 참여 상태 변경을 처리하는 API 구현
- 참여 마감 규칙과 요청자의 접근 권한을 확인하는 로직 구현
- 벌금과 보상 계산, 잔액 반영, 정산 결과 및 거래 내역 조회 구현
- 참여 변경과 정산을 처리하는 트랜잭션 설계 및 동작 검증
- 약속 목록 조회 쿼리 작성과 연관 데이터 조회 방식 개선

## 기술

- Java 17, Spring Boot 3.2.5
- Spring Data JPA, Spring Security, JWT
- MariaDB
- JUnit, H2, k6
- Nginx, Amazon EC2

## 시스템 구조

[![Jiki 서비스 시스템 구조](assets/jiki-architecture.png)](assets/jiki-architecture.png)

## 저장소 구성

```text
assets/                 시스템 구조 이미지
back/
  src/main/java/        백엔드 애플리케이션
  src/main/resources/   애플리케이션 설정
  src/test/             테스트
  docs/                 설계 및 검증 기록
  deploy/nginx/         Nginx 설정과 실행 안내
```

## 로컬 실행

Java 17과 MariaDB가 필요합니다. 사용할 데이터베이스와 계정을 준비한 뒤 `back/src/main/resources/application.properties.example`을 같은 폴더의 `application.properties`로 복사하고 DB 접속 정보를 입력합니다.

```bash
cd back
./gradlew bootRun
```

Windows에서는 `./gradlew` 대신 `gradlew.bat`을 사용합니다.

일반 테스트 실행:

```bash
cd back
./gradlew test
```

Nginx 설정: [배포 안내](back/deploy/nginx/README.md)

## 포트폴리오

[구현 과정과 검증 결과](https://unfl1.github.io/portfolio/#jiki)
