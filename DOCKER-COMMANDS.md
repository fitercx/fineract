# Fineract Docker - Quick Commands

## Build & Start
```bash
cd /Users/sauravsolanki/Documents/Repos/fineract

# Build custom image (includes all custom modules)
./gradlew :custom:docker:jibDockerBuild -x test

# Start
docker-compose -f docker-compose-local.yml up -d

# Logs
docker-compose -f docker-compose-local.yml logs -f fineract
```

## After Code Changes
```bash
# Rebuild & restart
./gradlew :custom:docker:jibDockerBuild -x test && \
docker-compose -f docker-compose-local.yml up -d --force-recreate fineract
```

## Test
```bash
# Health
curl -k "https://localhost:8443/fineract-provider/actuator/health"

# Creditlines API
curl -k -u mifos:password \
  -H "Fineract-Platform-TenantId: default" \
  "https://localhost:8443/fineract-provider/api/v1/clients/1/creditlines"
```

## Stop
```bash
docker-compose -f docker-compose-local.yml down
```

See `DOCKER-LOCAL-DEV.md` for detailed documentation.

IMPORTANT CHECKS

Please run the following commands for custom checks, 2 checks are failing here for custom folder

./gradlew --no-daemon --console=plain spotlessCheck checkstyleMain

./gradlew --no-daemon --console=plain spotlessCheck spotBugsMain
