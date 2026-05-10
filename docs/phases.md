## Phase Checklist

**Backend:** Phase 0 Scaffold · 1 Auth+User · 2 Listings CRUD · 3 PostGIS geo · 4 Media · 5 Chat+FCM · 6 Reviews · 7 Deploy

**Frontend** (after Backend Ph1 deployed): Phase 0 Scaffold · 1 Auth · 2 Map · 3 Listing detail · 4 Chat · 5 Provider dashboard · 6 Polish

## Workflow

**Backend:** Flyway migration → entity/repo → service/tests → controller/integration test → `mvn verify` → commit  
**Flutter:** Repository+API → Riverpod providers → Screen/Widgets → `flutter run` → `flutter test` → commit  
**Git:** `feat:` `fix:` `test:` `chore:` `docs:`
