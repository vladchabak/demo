## Backend Integration Tests

Use `@Testcontainers(disabledWithoutDocker = true)` + `@Container PostgreSQLContainer` + `@DynamicPropertySource`. Tests skip gracefully when Docker is unreachable.

```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
// → postgres container + @DynamicPropertySource wires datasource/flyway
// → app.dev-mode=true in application-test.yml → "Bearer dev-token" auth works
```

**Windows:** Testcontainers needs Docker on TCP localhost:2375, or `~/.testcontainers.properties` with `docker.host=...`. Run `mvn test` from WSL2 if Docker is only available there.

## Flutter Tests

Widget tests: `ServiceCard`, `ChatBubble`, `FilterBar`  
Integration tests: auth flow, map screen (mocked API)
