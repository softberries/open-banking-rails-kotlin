# Contributing: repository conventions

Every migration unit (M-0xx) relies on these rules. Behaviour parity with the Ruby service is the definition of done. Deviations
are allowed only where `migration-spec/docs/decisions.md` records them (deviation register X-01…X-27).

## Layout

- Package by feature (DDD aggregates), not by layer: `com.softberries.openbanking.<feature>.{domain,persistence,web,api}`.
  Cross-cutting setup lives in `com.softberries.openbanking.config`.
- JTE templates (`.kte`): `src/main/jte/admin/<feature>/`. They are precompiled by the Gradle `generateJte` task, and the development profile hot-reloads them.
- Test fixtures: one `src/testFixtures/kotlin/com/softberries/openbanking/<feature>/<Feature>Fixtures.kt` per feature, with no shared factory file.
- `migration-spec/` is a verbatim copy of the investigation (docs, inventory, oracle). Do not edit it. Tests read oracle samples from
  `migration-spec/oracle/http/` (`support.Oracle`).

## Build files and configuration

- Only M-001 edits `build.gradle.kts`, `settings.gradle.kts` and `gradle/libs.versions.toml`. The catalog is complete.
- Only chains C01/C03 edit `application.yml`. Feature units declare `@ConfigurationProperties` classes with defaults in code. ENV values reach them through
  relaxed binding (`FOO_BAR_BAZ` → `foo.bar-baz`).
- Keep the Ruby ENV names (see README).

## Database

- `V1__baseline.sql` is the only shared migration. A later migration uses its unit number as its version: `V14__…` for M-014, `V33__…` for M-033.
- `spring.flyway.out-of-order=true` in the `development` and `test` profiles (parallel chains merge in any order). In production it is `false`, with
  `baselineOnMigrate=true` and `baselineVersion=1` for existing Rails databases (D-005, U-107).
- FKs stay `NO ACTION`. Rails `dependent:` behaviour is implemented in services (50-data.md section 4).
- JPA: `ddl-auto=validate`, `open-in-view=false`, explicit `@Transactional` boundaries in services.

## Time

- Inject `AppClock`: `now(): Instant`, `today(): LocalDate` and `zone`. Never call `LocalDate.now()`, `Instant.now()` or `ZoneId.systemDefault()` in domain code.
- "Today", periods and schedules use `APP_TIME_ZONE` (default `Europe/Warsaw`, D-007). Timestamps are stored as UTC `timestamp` (Rails
  `timestamp(6) without time zone`), with `hibernate.jdbc.time_zone=UTC`.
- Tests that compare against the oracle pin `APP_TIME_ZONE=UTC` (the `IntegrationTest` default).

## Web

- `/up` (`health.web.UpController.PATH`) is a liveness probe. It must stay public and stateless, and touch no database. Any later filter or interceptor
  (first-run `/setup` redirect, modern-browser gate, host authorization, SSL redirect) must exclude it.
- Servlet filters run in Rails middleware order before Spring Security (`web.WebConfiguration`): CORS → host check → assume SSL/HSTS →
  Rails default headers and `Cache-Control` → `_method` override → modern-browser gate. Router-level controllers that Rails does not run
  through `ApplicationController` are annotated `@SkipBrowserGate`.
- Every new `SecurityFilterChain` starts with `WebSecurityConventions.apply(http)` (CSRF with `authenticity_token` / `X-CSRF-Token` → 422,
  unrouted requests and error dispatches permitted so they keep their 404, Spring's header writers and `/logout` off), then adds its URL rules.
  Order it before the fallback chain (`@Order(Int.MAX_VALUE)`), which keeps every routed request closed.
- Error responses are the Ruby static pages (`src/main/resources/public/*.html`, `web.HtmlErrorPages`) in every profile, never a stack trace.
  Throw `web.RecordNotFoundException` (404) and `web.ParameterMissingException` (400); anything else unhandled is a 500 page.
- `PATCH` and `PUT` on the same path map to one handler: `@RequestMapping(method = [RequestMethod.PATCH, RequestMethod.PUT])`.

## Tests

- JUnit 5 + Kotest assertions + MockK/springmockk. Integration tests extend `support.IntegrationTest` (Testcontainers `postgres:17`).
- Port the Ruby specs the issue names, and add a test for every acceptance criterion. Where an oracle sample exists, assert status, shape and key values
  against it. Never use a superseded oracle case (deviation register) as the Kotlin expectation.
- Run `./gradlew ktlintFormat ktlintCheck build` before pushing. Do not skip or weaken tests.

## Comments

Comment only where intent is not obvious, and cite the rule or decision id (`BR-###`, `D-###`, `U-###`).
