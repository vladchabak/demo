## API Contract

Base URL: `http://localhost:8080` (dev) · `https://localpro-api.railway.app` (prod)  
Auth: `Authorization: Bearer <firebase_id_token>`

```json
{ "data": {}, "page": { "number": 0, "size": 20, "totalElements": 0, "totalPages": 0 } }
{ "code": "LISTING_NOT_FOUND", "message": "...", "fieldErrors": [], "timestamp": "..." }
```

## Environment Variables

```
FIREBASE_SERVICE_ACCOUNT_JSON=<json>   ALLOWED_ORIGINS=http://localhost:3000
SPRING_DATASOURCE_URL/USERNAME/PASSWORD   CLOUDINARY_URL=cloudinary://...
SERVER_PORT=8080
# app.dev-mode=true  → application-dev.yml and application-test.yml only
#                      enables "Bearer dev-token" auth when Firebase is not configured
```

Frontend dart-define: `API_BASE_URL=http://10.0.2.2:8080` (Android) / `http://localhost:8080` (iOS)
