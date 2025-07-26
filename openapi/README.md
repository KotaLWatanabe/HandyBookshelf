# HandyBookshelf OpenAPI Documentation

This directory contains the OpenAPI specification and tools for the HandyBookshelf API.

## Files

- `handybookshelf-api.yaml` - OpenAPI 3.0.3 specification for the HandyBookshelf API
- `README.md` - This documentation file

## Usage

### View API Documentation with Swagger UI

#### Option 1: Using Podman Compose (Recommended)

From the project root directory:

```bash
# Start Swagger UI
podman-compose -f docker-compose.swagger.yml up -d swagger-ui

# Access Swagger UI at: http://localhost:8081
# Stop when done
podman-compose -f docker-compose.swagger.yml down
```

#### Option 2: Using Podman directly

```bash
# From the project root directory
podman run -p 8081:8080 \
  -e SWAGGER_JSON=/openapi/handybookshelf-api.yaml \
  -v $(pwd)/openapi:/openapi:ro \
  swaggerapi/swagger-ui
```

#### Option 3: Online Swagger Editor

1. Go to https://editor.swagger.io/
2. Copy the contents of `handybookshelf-api.yaml`
3. Paste into the editor

### Testing the API

1. Start the HandyBookshelf application:
   ```bash
   sbt "project controller" run
   ```

2. The API will be available at `http://localhost:8080`

3. Use Swagger UI at `http://localhost:8081` to interact with the API

### Current API Endpoints

#### Authentication
- `POST /api/v1/auth/login` - Login user
- `POST /api/v1/auth/logout` - Logout user  
- `GET /api/v1/auth/status/{userAccountId}` - Get user login status

#### Utility
- `GET /joke` - Get a random joke (example endpoint)

## API Design Notes

### Authentication
- Uses ULID format for user account IDs
- Currently no authentication tokens - endpoints are public
- Future versions may include JWT or session-based authentication

### Data Formats
- All API endpoints use JSON except `/joke` which returns plain text
- ULID format: 26-character base32 encoded string (e.g., `01HKDP7VWXYZ123456789ABCDE`)
- Error responses follow a consistent format with `error` and optional `details` fields

### Event Sourcing Architecture
The API is built on an event sourcing architecture with:
- Actor-based user session management
- Domain events for all state changes
- ULID-based entity identifiers

## Development

### Updating the OpenAPI Spec

When adding new endpoints:

1. Update the Scala code in `controller/src/main/scala/com/handybookshelf/controller/api/`
2. Update `handybookshelf-api.yaml` with the new endpoint definitions
3. Test using Swagger UI

### Validation

The OpenAPI spec can be validated using:

```bash
# Using swagger-codegen (if installed)
swagger-codegen validate -i openapi/handybookshelf-api.yaml

# Or online at https://validator.swagger.io/
```