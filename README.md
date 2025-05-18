# SafeTrack API

Spring Boot backend API for the SafeTrack personal safety platform.

## Features

- RESTful API endpoints
- Real-time location tracking
- Emergency alert system
- User management and authentication
- Emergency contact management
- Push notification service
- Geofencing capabilities
- Activity monitoring

## Prerequisites

- Java 17+
- Maven 3.6+
- PostgreSQL 13+
- Redis (optional, for caching)

## Quick Start

1. Configure database:
   ```bash
   cp src/main/resources/application.example.yml src/main/resources/application.yml
   ```
   Update database credentials in `application.yml`

2. Build the project:
   ```bash
   ./mvnw clean install
   ```

3. Run the application:
   ```bash
   ./mvnw spring-boot:run
   ```

The API will be available at http://localhost:8080

## Project Structure

```
safetrack-api/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/safetrack/api/
│   │   │       ├── config/        # Configuration classes
│   │   │       ├── controller/    # REST controllers
│   │   │       ├── model/         # Domain models
│   │   │       ├── repository/    # Data access layer
│   │   │       ├── service/       # Business logic
│   │   │       └── util/          # Utility classes
│   │   └── resources/
│   │       ├── db/migration/      # Flyway migrations
│   │       └── application.yml    # Application config
│   └── test/                      # Test files
└── pom.xml                        # Dependencies
```

## API Documentation

- Swagger UI: http://localhost:8080/swagger-ui.html
- API Docs: http://localhost:8080/api-docs

## Key Endpoints

### Authentication
- POST /api/auth/login
- POST /api/auth/register
- POST /api/auth/refresh-token

### Users
- GET /api/users/{id}
- PUT /api/users/{id}
- GET /api/users/me

### Emergency
- POST /api/emergency/sos
- POST /api/emergency/cancel
- GET /api/emergency/active

### Location
- POST /api/location/update
- GET /api/location/history
- POST /api/location/geofence

## Development

### Database Migrations

Run migrations:
```bash
./mvnw flyway:migrate
```

Create new migration:
```bash
./mvnw flyway:baseline
```

### Testing

Run tests:
```bash
./mvnw test
```

Run integration tests:
```bash
./mvnw verify
```

### Code Style

The project uses:
- Google Java Style Guide
- Checkstyle for style enforcement
- SonarLint for code quality

## Security

- JWT-based authentication
- Role-based access control
- Input validation
- XSS protection
- CORS configuration
- Rate limiting

## Monitoring

The application includes:
- Actuator endpoints
- Prometheus metrics
- Health checks
- Performance monitoring

## Environment Variables

Required environment variables:

```properties
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/safetrack
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=password
JWT_SECRET=your-secret-key
PUSH_NOTIFICATION_KEY=your-push-key
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Write tests for new features
4. Ensure all tests pass
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For support and inquiries:
- Email: support@safetrack.com
- Documentation: https://docs.safetrack.com
- Issue Tracker: GitHub Issues

