# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build
./gradlew build

# Run application
./gradlew bootRun

# Run tests
./gradlew test

# Clean build
./gradlew clean build
```

## Prerequisites

Requires a local MariaDB instance with a database named `jiki`. Default credentials are `root/root` (see `src/main/resources/application.properties`). Schema is auto-generated via `spring.jpa.hibernate.ddl-auto=update`.

Swagger UI is available at `http://localhost:8080/swagger-ui.html` when the app is running.

## Architecture

Spring Boot 3.2.5 REST API organized into four feature packages under `src/main/java/jiki/jiki/`:

- **user/** — registration, login (BCrypt passwords), account balance
- **friend/** — friend requests with `FriendStatus` enum (PENDING / ACCEPTED / REJECTED); relationships stored as `Friend` entity with `user1`/`user2` fields
- **promise/** — core feature: create appointments with penalty amounts and lat/long location; `Participant` entity joins users to promises and tracks late-arrival status via `ParticipantStatus`
- **payment/** — penalty collection and reward settlement; transaction history stored in `MoneyRecord`
- **config/** — `SecurityConfig` (Spring Security, CSRF disabled, all routes permitted), `GlobalExceptionHandler`, `SwaggerConfig`, `WebMvcConfig` (CORS all origins)

Each package follows the same layered pattern: `Entity` → `Repository` (JpaRepository) → `Service` → `Controller` (@RestController) → DTOs.

Authentication is header-based: controllers read `@RequestHeader("username")` rather than a security context. JWT dependencies (jjwt 0.12.3) are present but not yet wired into `SecurityConfig`.
