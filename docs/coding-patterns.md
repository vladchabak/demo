## Entity / Lombok
- `@Builder.Default` on any initialized field (enum defaults, `BigDecimal.ZERO`) — builder ignores initializers without it → null → NOT NULL violation.
- `insertable=false, updatable=false` on `createdAt`/`updatedAt` — DB trigger owns them.
- `@ToString(exclude=...)` + `@EqualsAndHashCode(exclude=...)` on both sides of `@OneToMany`/`@ManyToOne` — prevents circular toString → StackOverflow.

## MapStruct
- Update mappers: always `NullValuePropertyMappingStrategy.IGNORE` — PATCH semantics.

## Spring Security
- `FilterRegistrationBean.setEnabled(false)` for every `OncePerRequestFilter` — prevents double-execution.
- Coarse `SecurityConfig` (`permitAll` on GET patterns) + fine-grained `@CurrentUser` checks.
- CORS: read from `ALLOWED_ORIGINS` env var — never use wildcard `*` with `allowCredentials(true)`.
- Dev-token backdoor: guarded by `app.dev-mode=true` AND `firebaseApp == null`. Never active in prod.

## Hibernate / JPA
- Lazy associations in controller → `LazyInitializationException`. Fix: `JOIN FETCH` in repo query.
- Haversine for distance — do NOT add GeoTools (100 MB+).
- Category tree: `findAll()` + build in Java (safe for < 1000 rows).
- `@ManyToOne` fields: load entity via repo, never set from raw UUID.

## WebSocket / STOMP
- Two-step Principal resolution (both required):
  1. `JwtHandshakeInterceptor` — HTTP handshake → userId in session attributes
  2. `ChannelInterceptor` on CONNECT — wraps userId in `StompPrincipal`
- `beforeHandshake` always returns `true` — auth rejected at CONNECT level (intentional).
- Keep `getOrCreateChat()` (entity) and `buildSummary()` (DTO) separate.

## Exception Handling
- `IllegalArgumentException` → 400 · `EntityNotFoundException` → 404 · `AccessDeniedException` → 403
