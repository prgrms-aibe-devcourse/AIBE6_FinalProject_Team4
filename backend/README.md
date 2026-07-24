# kiwobollae-backend (api)

Backend for 키워볼래 (kiwobollae), a plant-growing/reward platform. Spring Boot + JPA,
schema modeled after `docs/erd.dbml`. JWT login/signup and CORS are implemented; most
domain controllers are still empty scaffolding for the team to build on.

## Requirements

- Java 21
- MySQL 8.0 (local instance, or point at AWS RDS via env vars)

## 실행 전에 꼭 해야 할 것들

이 프로젝트는 DB나 스키마를 자동으로 만들어주지 않습니다. 처음 받아서 실행하기 전에
아래를 순서대로 해주세요.

1. **로컬 MySQL이 떠 있는지 확인** (기본 포트 3306)
2. **`kiwobollae` 데이터베이스를 직접 생성** — 앱이 알아서 만들어주지 않습니다.
   MySQL 클라이언트로 접속해서:
   ```sql
   CREATE DATABASE kiwobollae CHARACTER SET utf8mb4;
   ```
   (Hibernate의 `ddl-auto: update`는 이 DB *안의 테이블*만 자동으로 만들어줍니다.
   DB 자체가 없으면 `Unknown database 'kiwobollae'` 에러로 기동에 실패해요.)
3. **로컬 시크릿 파일 준비** — `src/main/resources/application-secret.yaml.example`을
   같은 폴더에 `application-secret.yaml`로 복사하고, 자신의 로컬 MySQL 계정/비밀번호로
   채워주세요. (`.gitignore`에 등록되어 있어 커밋되지 않습니다.) 이 파일 대신 `.env` +
   환경변수 방식을 써도 됩니다 (`.env.example` 참고).
4. **Java 21 툴체인 확인** — `java -version`으로 21인지 확인.

이 네 가지만 되어 있으면 `./gradlew bootRun`으로 바로 기동됩니다.

## Running

```
./gradlew bootRun
```

기본적으로 `local` 프로필로 실행하는 걸 권장합니다 (SQL 로그 출력, DEBUG 로깅):

```
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

## Required environment variables

| Variable | Purpose | Local default |
|---|---|---|
| `DB_USERNAME` | MySQL username | `root` |
| `DB_PASSWORD` | MySQL password | `password` |
| `JWT_SECRET` | JWT signing secret | placeholder, dev-only |
| `JWT_ACCESS_EXPIRATION` | Access token TTL (ms) | `3600000` (1h) |
| `JWT_REFRESH_EXPIRATION` | Refresh token TTL (ms) | `1209600000` (14d) |

`local` 프로필에서는 위 값들이 없어도 `application-secret.yaml`이나 기본값으로 굴러가지만,
`prod` 프로필은 `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/`JWT_*`를 전부 실제 인프라(비밀 관리
서비스 등)에서 주입받아야 하며 기본값이 없습니다.

## Schema management

`spring.jpa.hibernate.ddl-auto`는 현재 모든 프로필(`local`/`prod` 공통)에서 `update`로
되어 있습니다. 즉 엔티티에 컬럼/인덱스를 추가하면 다음 기동 시 실제 테이블에 그대로
반영됩니다 — 아직 별도 마이그레이션 도구가 없어서 이렇게 돼 있는 것이고, 실제 운영
배포 전에는 반드시 Flyway나 Liquibase 같은 마이그레이션 도구를 도입해 `ddl-auto`를
`validate`로 바꾸는 작업이 필요합니다 (지금은 운영 DB도 앱이 기동될 때마다 스키마를
바꿀 수 있는 상태라는 뜻이라, 이 상태로 실제 서비스에 배포하면 안 됩니다).

## Auth / Security

- `POST /api/v1/auth/signup`, `POST /api/v1/auth/login` 구현되어 있고, 로그인 성공 시
  액세스 토큰(JWT)을 발급합니다.
- 모든 요청은 `Authorization: Bearer <token>` 헤더로 전역 인증됩니다 (`/api/v1/auth/**`
  제외). 토큰이 없거나 유효하지 않으면 403.
- CORS는 `http://localhost:*`, `http://127.0.0.1:*` 오리진을 허용하도록 설정되어 있어
  로컬 프론트(포트 무관)에서 바로 호출 가능합니다.
- 리프레시 토큰 재발급, 소셜 로그인(GOOGLE/KAKAO/NAVER)은 아직 미구현입니다.

## Package structure

Base package: `com.kiwobollae.api`.

- `global/` — cross-cutting concerns shared by every domain: `config` (security, CORS),
  `security` (JWT provider/filter), `exception` (global exception handling), `common`
  (`BaseTimeEntity`, `ApiResponse`).
- One package per ERD domain, each following `entity` / `entity/enums` / `repository` /
  `service` / `controller` / `dto`:
  - `auth` — users, refresh tokens (User is the shared root entity referenced by every
    other domain).
  - `mypage` — user addresses.
  - `notification` — notifications, notification settings.
  - `content` — plant species/profile/journal, journal images, journal completion logs,
    inquiries, reports.
  - `commerce` — products, exchange products, cart, orders, cards, exchange orders.
  - `point` — wallet, point transactions.
  - `payment` — charge products, payments, payment refunds.
  - `infra` — idempotency keys (cross-cutting persistence concern, not tied to one domain).
  - `admin` — placeholder for admin-only endpoints (`/api/v1/admin/**`); admin business
    logic mostly lives in the owning domain's service (inquiry answers, report processing,
    product/card management, etc.) and this controller is a shell for the team to expand.

Every entity uses Lombok (`@Getter`, `@NoArgsConstructor(PROTECTED)`, `@AllArgsConstructor`,
`@Builder`), `IDENTITY` primary keys, `FetchType.LAZY` on all associations, and `@Enumerated
(EnumType.STRING)` for enum columns. No setters, no validation beyond `auth`. Most domain
service/controller method bodies are still empty — this is scaffolding for a team to build
features on top of.
