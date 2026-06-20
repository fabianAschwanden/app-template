# app-template

Lauffähiges Gerüst für neue Applikationen — **ohne fachlichen Inhalt**. Setzt den
[Blueprint](docs/blueprint.md) als Code um:
ein Deployable (BFF), Quarkus + Angular via Quinoa, Hexagonal + DDD, erzwungen per ArchUnit,
deployt auf Fly.io mit Neon PostgreSQL.

## Stack

| Bereich | |
|---|---|
| Backend | Java 25, Quarkus 3.36 (REST, Hibernate/Panache, Liquibase, OIDC BFF) |
| Frontend | Angular 22 (standalone, Signals), TypeScript 6, TailwindCSS 4 |
| Datenbank | PostgreSQL — Dev/Test via Dev Services, Prod via Neon (serverless) |
| Tests & Qualität | JUnit 5, Mockito, `@QuarkusTest`/REST-assured, ArchUnit, Vitest, Playwright, JaCoCo-Gate, ESLint |
| Deployment | Fly.io — Tag `v*` → `flyctl deploy`, Rolling-Deploy, HTTPS automatisch |
| Delivery | GitHub Actions, Dependabot |

## Neue App erstellen

1. Auf GitHub **„Use this template"** klicken (Repo ist als Template-Repository markiert —
   falls noch nicht: Settings → General → „Template repository" aktivieren).
2. Im neuen Repo App-Namen und Basis-Paket setzen:
   ```bash
   ./scripts/init.sh my-app io.github.fabianaschwanden.myapp
   ```
3. Beispiel-Durchstich **Note** durch echte Fachlichkeit ersetzen (Suche nach `Note`).
   Er existiert nur als Referenz: Domäne → Ports → Application Service → REST → Liquibase → Angular-Feature.
4. `./mvnw verify` und loslegen.

### Fly.io + Neon einrichten (einmalig, ~5 Min)

```bash
# 1. Neon: https://neon.tech → neues Projekt anlegen → Connection string kopieren

# 2. Fly.io App registrieren
flyctl apps create my-app

# 3. Secrets setzen
flyctl secrets set \
  DB_URL='jdbc:postgresql://<host>.neon.tech/<db>?sslmode=require' \
  OIDC_AUTH_SERVER_URL='https://<idp>/realms/<realm>' \
  OIDC_CLIENT_ID='my-app' \
  OIDC_CLIENT_SECRET='<secret>' \
  --app my-app

# 4. Deploy-Token als GitHub-Secret hinterlegen
flyctl tokens create deploy -a my-app
# → GitHub Repo → Settings → Secrets → New secret: FLY_API_TOKEN

# 5. Erstes Deployment auslösen
git tag v0.1.0 && git push --tags
```

Liquibase migriert das Neon-Schema automatisch beim ersten Start.
`fly.toml` und `src/main/docker/Dockerfile.jvm` sind bereits konfiguriert.

## Entwickeln

```bash
./mvnw quarkus:dev        # Backend :8080, proxyt auf Angular-Dev-Server (:4200, startet automatisch)
```

- App: http://localhost:8080 · Swagger-UI: /q/swagger-ui · Health: /q/health
- PostgreSQL kommt automatisch über Dev Services (Container-Runtime nötig).
- Auth ist in `%dev`/`%test` aus, in `%prod` an (OIDC BFF, Konfiguration via Env-Variablen).

```bash
./mvnw verify                  # Backend-Tests, ArchUnit, Coverage-Gate, Frontend-Build
cd webapp && npm test          # Frontend-Unit-Tests (Vitest)
cd webapp && npm run lint      # ESLint (Frontend-Konventionen)
cd webapp && npm run e2e       # Playwright gegen laufende Instanz (E2E_BASE_URL)
```

## Struktur

```
src/main/java/.../
├── domain/              # inneres Hexagon — framework-frei, records, Ports
├── application/service/ # Application Services — Orchestrierung, Transaktionsgrenze
└── adapter/
    ├── in/rest/         # JAX-RS Resources + dto/
    ├── in/security/     # SecurityIdentityAugmentor (Rollen-Mapping)
    └── out/persistence/ # JPA-Entities, Panache-Repositories
src/main/resources/db/changelog/  # Liquibase — besitzt das Schema, append-only
src/main/docker/Dockerfile.jvm    # Multi-Stage JVM-Image für Fly.io
webapp/src/app/
├── core/                # models (spiegeln REST-DTOs) + services
├── features/            # UI-Komponenten je Route
└── shared/              # wiederverwendbare Bausteine (anlegen wenn gebraucht)
fly.toml                 # Fly.io App-Konfiguration (Region, VM, Health-Check)
docs/blueprint.md        # verbindliche Architektur- und Stack-Vorgaben
```

Die Abhängigkeitsregel `adapter → application → domain` und weitere Invarianten
bricht der Build (`HexagonalArchitectureTest`).

## Aktuell bleiben

- **Dependabot** hebt wöchentlich Maven-, npm- und Actions-Versionen (gruppiert);
  Framework-Majors (Angular, TypeScript) werden manuell via `ng update` gehoben.
- Verbesserungen aus laufenden Projekten gehören hierher zurück — **fachlicher Code nie**.
- Abgeleitete Apps ziehen Template-Änderungen bei Bedarf gezielt nach (Cherry-Pick/Diff).

## Bewusst nicht enthalten

Kafka/Messaging, S3/Object-Storage, Scheduler, Cucumber/BDD-Runner sowie der
Real-OIDC-Smoke-Test (`quarkus-test-keycloak-server`): gemäss Blueprint (KISS) erst
hinzufügen, wenn ein echter Use Case sie braucht — die Konventionen stehen im Blueprint.
