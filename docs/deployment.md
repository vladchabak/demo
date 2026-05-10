## Railway (Backend)

- Env vars: `FIREBASE_SERVICE_ACCOUNT_JSON` · `ALLOWED_ORIGINS` (prod origin) · all `PG*` vars
- Flyway must pass · Swagger disabled (`springdoc.swagger-ui.enabled: false` in prod profile)

## App Store (Flutter)

- `API_BASE_URL` → prod Railway URL
- Prod `google-services.json` / `GoogleService-Info.plist`
- App icon + splash · privacy policy URL
- `flutter build appbundle --release` must pass
