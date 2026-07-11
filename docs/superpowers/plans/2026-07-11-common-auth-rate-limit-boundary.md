# Common/Auth Rate-Limit Boundary Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the concrete `common.ratelimit -> auth.service` dependency while preserving rate-limit enforcement and auth security metrics.

**Architecture:** The common rate-limit aspect will emit an auth-rate-limit rejection through a common-owned port. An auth-owned Spring adapter will translate that port call to the existing `AuthSecurityMonitoringService`, leaving dependency direction as `auth -> common`.

**Tech Stack:** Java 21, Spring Boot, AspectJ, JUnit 5, Mockito, ArchUnit 1.4.2, Gradle

## Global Constraints

- Preserve `RateLimitAspect` key generation, fail-open/fail-closed behavior, exception type, Korean message, and logging.
- Preserve the `auth_security_events_total{event="AUTH_RATE_LIMIT_REJECT"}` metric behavior.
- Do not change authentication, JWT, OAuth2, CORS, cookie, or endpoint behavior.
- Do not touch baseball data sources or add external baseball network access.
- Do not stage, revert, or commit unrelated pre-existing worktree changes.

---

### Task 1: Add an Executable Common-to-Auth Boundary Rule

**Files:**
- Modify: `BEGA_PROJECT/build.gradle:162`
- Create: `BEGA_PROJECT/src/test/java/com/example/architecture/BackendBoundaryArchitectureTest.java`

**Interfaces:**
- Consumes: compiled classes under package `com.example`
- Produces: ArchUnit rule `COMMON_RATE_LIMIT_MUST_NOT_DEPEND_ON_AUTH`

- [ ] **Step 1: Add the ArchUnit test dependency**

Add this line next to the existing test dependencies:

```groovy
testImplementation 'com.tngtech.archunit:archunit-junit5:1.4.2'
```

- [ ] **Step 2: Write the failing architecture test**

Create the following file:

```java
package com.example.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

class BackendBoundaryArchitectureTest {

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .importPackages("com.example");

    @Test
    void commonRateLimitMustNotDependOnAuthImplementations() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("com.example.common.ratelimit..")
                .should().dependOnClassesThat().resideInAPackage("com.example.auth..");

        rule.check(PRODUCTION_CLASSES);
    }
}
```

- [ ] **Step 3: Run the architecture test and verify RED**

Run from `bega_backend/BEGA_PROJECT`:

```bash
./gradlew test --tests "*BackendBoundaryArchitectureTest.commonRateLimitMustNotDependOnAuthImplementations"
```

Expected: FAIL reporting that `RateLimitAspect` depends on `AuthSecurityMonitoringService`.

### Task 2: Invert the Auth Security Metric Dependency

**Files:**
- Create: `BEGA_PROJECT/src/main/java/com/example/common/ratelimit/AuthRateLimitSecurityEventReporter.java`
- Create: `BEGA_PROJECT/src/main/java/com/example/auth/service/AuthRateLimitSecurityEventReporterAdapter.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/common/ratelimit/RateLimitAspect.java:3-29`
- Create: `BEGA_PROJECT/src/test/java/com/example/common/ratelimit/RateLimitAspectTest.java`

**Interfaces:**
- Produces: `AuthRateLimitSecurityEventReporter.recordRejected()`
- Consumes: `AuthSecurityMonitoringService.recordAuthRateLimitReject()`

- [ ] **Step 1: Create the common-owned reporting port**

```java
package com.example.common.ratelimit;

@FunctionalInterface
public interface AuthRateLimitSecurityEventReporter {

    void recordRejected();
}
```

- [ ] **Step 2: Write the focused aspect behavior test**

```java
package com.example.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.common.exception.RateLimitExceededException;
import com.example.common.web.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class RateLimitAspectTest {

    private final RateLimitService rateLimitService = mock(RateLimitService.class);
    private final ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
    private final AuthRateLimitSecurityEventReporter reporter = mock(AuthRateLimitSecurityEventReporter.class);
    private final RateLimitAspect aspect = new RateLimitAspect(rateLimitService, clientIpResolver, reporter);

    @BeforeEach
    void setUpRequest() {
        HttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.clearContext();
        when(clientIpResolver.resolveOrUnknown(request)).thenReturn("127.0.0.1");
        when(rateLimitService.isAllowed(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(false);
    }

    @AfterEach
    void clearRequest() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectedAuthLimitReportsSecurityEvent() throws Exception {
        JoinPoint joinPoint = joinPointFor("authLimited");

        assertThatThrownBy(() -> aspect.checkRateLimit(joinPoint))
                .isInstanceOf(RateLimitExceededException.class);

        verify(reporter).recordRejected();
    }

    @Test
    void rejectedNonAuthLimitDoesNotReportAuthSecurityEvent() throws Exception {
        JoinPoint joinPoint = joinPointFor("generalLimited");

        assertThatThrownBy(() -> aspect.checkRateLimit(joinPoint))
                .isInstanceOf(RateLimitExceededException.class);

        verify(reporter, never()).recordRejected();
    }

    private JoinPoint joinPointFor(String methodName) throws Exception {
        Method method = Fixture.class.getDeclaredMethod(methodName);
        MethodSignature signature = mock(MethodSignature.class);
        JoinPoint joinPoint = mock(JoinPoint.class);
        when(signature.getMethod()).thenReturn(method);
        when(signature.toShortString()).thenReturn("Fixture." + methodName + "()");
        when(joinPoint.getSignature()).thenReturn(signature);
        when(joinPoint.getTarget()).thenReturn(new Fixture());
        return joinPoint;
    }

    private static final class Fixture {

        @RateLimit(limit = 1, window = 60, key = "auth:test")
        void authLimited() {
        }

        @RateLimit(limit = 1, window = 60, key = "general:test")
        void generalLimited() {
        }
    }
}
```

- [ ] **Step 3: Replace the concrete auth dependency in the aspect**

Remove:

```java
import com.example.auth.service.AuthSecurityMonitoringService;
```

Replace the field:

```java
private final AuthRateLimitSecurityEventReporter authRateLimitSecurityEventReporter;
```

Replace the metric call inside the rejected-auth branch:

```java
authRateLimitSecurityEventReporter.recordRejected();
```

Do not change any other aspect logic.

- [ ] **Step 4: Implement the auth-owned adapter**

```java
package com.example.auth.service;

import com.example.common.ratelimit.AuthRateLimitSecurityEventReporter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthRateLimitSecurityEventReporterAdapter implements AuthRateLimitSecurityEventReporter {

    private final AuthSecurityMonitoringService authSecurityMonitoringService;

    @Override
    public void recordRejected() {
        authSecurityMonitoringService.recordAuthRateLimitReject();
    }
}
```

- [ ] **Step 5: Run focused tests and verify GREEN**

Run:

```bash
./gradlew test --tests "*RateLimitAspectTest" --tests "*BackendBoundaryArchitectureTest"
```

Expected: PASS. The architecture test no longer reports a `common.ratelimit -> auth` dependency, and both behavior tests pass.

- [ ] **Step 6: Run auth rate-limit regression coverage**

Run:

```bash
./gradlew test --tests "*AuthRateLimitIntegrationTest" --tests "*RateLimitFilterTest" --tests "*RateLimitServiceTest"
```

Expected: PASS with no changed HTTP or metric assertions.

- [ ] **Step 7: Check formatting and intentional diff**

Run:

```bash
git diff --check -- BEGA_PROJECT/build.gradle BEGA_PROJECT/src/main/java/com/example/common/ratelimit BEGA_PROJECT/src/main/java/com/example/auth/service/AuthRateLimitSecurityEventReporterAdapter.java BEGA_PROJECT/src/test/java/com/example/architecture BEGA_PROJECT/src/test/java/com/example/common/ratelimit/RateLimitAspectTest.java
```

Expected: no output.

- [ ] **Step 8: Commit only this boundary slice**

```bash
git add BEGA_PROJECT/build.gradle \
  BEGA_PROJECT/src/main/java/com/example/common/ratelimit/RateLimitAspect.java \
  BEGA_PROJECT/src/main/java/com/example/common/ratelimit/AuthRateLimitSecurityEventReporter.java \
  BEGA_PROJECT/src/main/java/com/example/auth/service/AuthRateLimitSecurityEventReporterAdapter.java \
  BEGA_PROJECT/src/test/java/com/example/architecture/BackendBoundaryArchitectureTest.java \
  BEGA_PROJECT/src/test/java/com/example/common/ratelimit/RateLimitAspectTest.java
git commit -m "refactor: invert auth rate-limit monitoring dependency"
```

Before committing, confirm no unrelated path is staged.

## Plan Self-Review

- The architecture rule directly proves removal of the reported `common.ratelimit -> auth` edge.
- Behavior tests cover both auth and non-auth rejection paths.
- The adapter preserves the exact existing metric method.
- No endpoint, security, database, Frontend, AI-service, or baseball-data contract changes are included.
