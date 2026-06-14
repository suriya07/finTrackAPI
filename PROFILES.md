# Spring Profiles (dev / prod)

This project supports Spring profiles to separate development and production configuration. The default active profile is `dev`.

Files:
- `src/main/resources/application.properties` - common properties and profile activation. Uses `SPRING_PROFILES_ACTIVE` environment variable with default `dev`.
- `src/main/resources/application-dev.properties` - development settings (local DB, verbose logging).
- `src/main/resources/application-prod.properties` - production settings (expects secrets via environment variables).

Running locally with the dev profile (default):

```bash
# default (dev)
./mvnw spring-boot:run

# explicitly set profile to dev
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
```

Running with the prod profile:

```bash
# set required environment variables and run
export SPRING_PROFILES_ACTIVE=prod
export PROD_DATASOURCE_URL=jdbc:postgresql://prod-host:5432/finance_manager
export PROD_DATASOURCE_USERNAME=prod_user
export PROD_DATASOURCE_PASSWORD=supersecret
export JWT_SECRET="<very-long-secret>"
./mvnw -DskipTests spring-boot:run
```

Notes:
- Never commit production secrets into the repository. Use environment variables, a secrets manager, or an external config server.
- Review `application-prod.properties` and override values as necessary for your deployment environment.

