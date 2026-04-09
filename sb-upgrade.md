# Spring Boot 4 upgrade guide for `zmq-play`

This document outlines a **project-specific** path to upgrade `zmq-play` from Spring Boot `3.5.13` to Spring Boot
`4.0.5`.

> Status note: This guide now targets Spring Boot `4.0.5` on Java `21`.

## 1) Current baseline in this repository

- Java toolchain: `21` (project SDK updated; root `build.gradle` should match)
- Spring Boot version catalog: `3.5.13` (`gradle/libs.versions.toml`)
- Dependency management plugin: `1.1.7` (`gradle/libs.versions.toml`)
- Main Spring module: `zmq-pub-sub`
- API docs dependency: `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.15`
- Custom logging setup: `spring-boot-starter-log4j2` + `log4j2-spring.xml`
- CI Java version in workflows: update from `17` to `21` (`.github/workflows/ci.yml`,
  `.github/workflows/release-main.yml`)

## 2) Prerequisites and compatibility gates

1. **Use Java 21 for SB4**
    - The project SDK is already updated to `21`; keep `21` as the required target.
    - Ensure root `build.gradle` toolchain is:
        - `JavaLanguageVersion.of(21)`

2. **Upgrade Gradle wrapper first**
    - Bump Gradle wrapper to an SB4-compatible version (latest supported 8.x/9.x at the time of migration).
    - Validate with:
      ```powershell
      .\gradlew.bat --version
      ```

3. **Pin the exact SB target version**
    - In `gradle/libs.versions.toml`, set:
        - `springBootVersion` -> `4.0.5`
        - `springDependencyManagementVersion` -> version compatible with your chosen SB4 and Gradle.

## 3) Implementation steps

### Step A - Update version catalog and plugins

Edit `gradle/libs.versions.toml`:

- `springBootVersion = "4.0.5"`
- `springDependencyManagementVersion = "..."` (SB4-compatible)
- `springDocVersion = "..."` (must explicitly support Spring Boot 4)

Then refresh dependencies:

```powershell
.\gradlew.bat --refresh-dependencies
```

### Step B - Update Java and CI pipelines

1. Root build toolchain (`build.gradle`)
    - Set Java language version to `21`.

2. GitHub Actions
    - Update Java setup in:
        - `.github/workflows/ci.yml`
        - `.github/workflows/release-main.yml`
    - Replace `java-version: '17'` with `java-version: '21'`.

3. (If used) check `.github/workflows/release-patch.yml` for the same Java pin and update accordingly.

### Step C - Resolve Spring/Spring Boot API/config changes

Focus areas in this project:

1. **Web + validation layer**
    - Controllers already use `jakarta.validation.Valid` (`Controller.java`), which is the right namespace family.
    - Re-run compile and fix any SB4-driven Spring MVC signature changes.

2. **CORS configuration**
    - Review `IgnoreCorsConfig.java` (`WebMvcConfigurer`) for changed defaults/deprecations in Spring Framework 7.

3. **Configuration properties**
    - Re-check `zmq-pub-sub/src/main/resources/application.yml` for renamed/removed properties under:
        - `logging.*`
        - `spring.jackson.*`
        - `springdoc.*`

4. **Logging stack**
    - Keep `spring-boot-starter-log4j2` and excluded default logging behavior in `zmq-pub-sub/build.gradle`, but verify
      no SB4 changes are required in `log4j2-spring.xml` patterns/properties.

5. **OpenAPI/Swagger**
    - Confirm the selected `springdoc-openapi` version is SB4-compatible.
    - Verify Swagger UI path behavior (`/swagger-ui.html`) still matches the desired endpoint.

### Step D - Test and runtime validation

Run in this order:

```powershell
.\gradlew.bat clean test
.\gradlew.bat :zmq-pub-sub:bootRun
```

Validate:

1. Application starts cleanly on port `8088`.
2. Swagger UI loads at configured path.
3. Integration tests pass, especially:
    - `ControllerIntegrationTest`
    - `ControllerEdgeCaseTest`
    - `ControllerLuaIntegrationTest`
4. Concurrency/path regressions pass:
    - `ZmqServiceConcurrencyTest`
    - `ZmqServiceSaveToFileTest`
5. Manual smoke test of publish/subscribe flows (including file output in `messages/`).

## 4) Suggested execution strategy (low risk)

1. Create branch `upgrade/spring-boot-4`.
2. Upgrade Gradle wrapper + Java toolchain first, commit.
3. Upgrade SB4 versions in catalog, commit.
4. Fix compile/runtime/config issues, commit incrementally.
5. Run full tests and smoke checks before merge.

## 5) Known risk checklist for this repository

- [ ] `springdoc-openapi` SB4 compatibility confirmed
- [ ] CI workflows moved from Java 17 to Java 21
- [ ] `log4j2-spring.xml` works without runtime property resolution errors
- [ ] No broken endpoint mappings or validation behavior changes
- [ ] `bootRun` + all Java tests pass

## 6) Definition of done

Upgrade is complete when all are true:

1. Project builds and tests on the target Java version locally and in CI.
2. `zmq-pub-sub` starts and serves API endpoints normally.
3. Swagger/OpenAPI is reachable and correctly generated.
4. Existing controller/service test suites pass without disabled tests.
5. Pub/sub runtime behavior remains functional (manual smoke test).
