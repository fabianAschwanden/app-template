# Blueprint — Technologie-Stack & Architektur-Prinzipien

**Verbindliche Vorgabe für alle Apps aus diesem Template** ([fabianAschwanden/app-template](https://github.com/fabianAschwanden/app-template)). Beschreibt *wie* gebaut wird (Stack, Schichten, Regeln, Konventionen), nicht *was* fachlich gebaut wird. Versionen sind ein Snapshot (Stand 2026-06); beim Aufsetzen auf die jeweils aktuelle LTS/stabile Version heben und hier dokumentieren.

## 1. Deployment-Form: ein Backend-for-Frontend (BFF), ein Deployable

Eine einzelne Maven-Modul-Einheit enthält Backend und Frontend. Das Quarkus-Backend serviert die SPA und dient ihr als Backend-for-Frontend: die SPA spricht ausschliesslich mit dem eigenen Backend, nie direkt mit Drittsystemen oder dem Identity-Provider.

- Ein Build-Artefakt (`quarkus-run.jar` im Multi-Stage JVM-Image).
- Frontend-Integration über **Quinoa**: Dev-Modus proxyt Quarkus (:8080) auf den Angular-Dev-Server (:4200); Production-Build packt das kompilierte Frontend als statische Ressource mit.
- Vorteile: keine separate Frontend-Pipeline, kein CORS, Session-Cookies statt Tokens im Browser (§8).

## 2. Technologie-Stack

### Backend

| Bereich | Wahl | Version |
|---|---|---|
| Sprache | Java | 25 (`maven.compiler.release`) |
| Framework | Quarkus (`quarkus-bom`) | 3.36.x |
| REST | `quarkus-rest` + `quarkus-rest-jackson` (JAX-RS, reaktiv) | — |
| Persistenz-ORM | Hibernate ORM mit Panache | — |
| Datenbank | PostgreSQL (`quarkus-jdbc-postgresql`) | — |
| Schema-Migration | Liquibase (`quarkus-liquibase`) | — |
| Validierung | Hibernate Validator | — |
| Auth | OIDC (`quarkus-oidc`) gegen Keycloak | — |
| API-Doku | `quarkus-smallrye-openapi` (+ Swagger-UI) | — |
| Health | `quarkus-smallrye-health` | — |
| DI | CDI / ArC (`quarkus-arc`) | — |

Bewusst nicht enthalten (KISS — erst hinzufügen, wenn ein echter Use Case es braucht):
Kafka/Messaging (`quarkus-smallrye-reactive-messaging`), S3 (`quarkus-amazon-s3`), Scheduler (`quarkus-scheduler`), Cucumber/BDD-Runner.

### Frontend

| Bereich | Wahl | Version |
|---|---|---|
| Framework | Angular (standalone, Signals) | 22.x |
| Sprache | TypeScript (strict) | 6.0.x |
| Styling | TailwindCSS (+ `@tailwindcss/postcss`) | 4.x |
| Reaktivität | RxJS (nur an REST-/Stream-Grenzen) | 7.8.x |
| Build | `@angular/build` (esbuild-basiert) | 22.x |
| Linting | ESLint (`angular-eslint` + `typescript-eslint`) — erzwingt die Frontend-Konventionen (§5) | — |
| Formatierung | Prettier (print width 100, single quotes) + EditorConfig | — |

Plain SPA, keine PWA.

### Test & Qualität

| Ebene | Werkzeug |
|---|---|
| Backend Unit | JUnit 5 + Mockito |
| Backend REST/Integration | `@QuarkusTest` + REST-assured, gegen Dev Services (PostgreSQL) |
| Architektur-Invarianten | ArchUnit (als reguläre Jupiter-Tests — ArchUnit-Engine läuft unter JUnit Platform 6 nicht) |
| Frontend Unit/Component | Vitest |
| Frontend E2E | Playwright |
| Coverage-Gate | JaCoCo (`jacoco:check`, 50 % Line-Coverage) |
| Auth-Tests | `quarkus-test-security` (Identitäten stubben) |

Bewusst nicht enthalten: Cucumber/BDD-Runner, `quarkus-test-keycloak-server` (Real-OIDC-Smoke-Test) — gemäss KISS erst hinzufügen, wenn gebraucht.

### Build, Lieferung, Betrieb

| Bereich | Wahl |
|---|---|
| Build | Maven (mvnw) + npm (über Quinoa) |
| Container | Multi-Stage `src/main/docker/Dockerfile.jvm` (`eclipse-temurin:25`) |
| CI | GitHub Actions (ubuntu-latest) — verify als Merge-Gate |
| Release | Tag `v*` → `flyctl deploy --remote-only` → Rolling-Deploy auf Fly.io |
| Deployment | Fly.io (shared-cpu-1x, 512 MB, Region ams); HTTPS automatisch |
| Datenbank (Prod) | Neon PostgreSQL (serverless, free tier); Secrets via `flyctl secrets set` |
| Dependency-Updates | Dependabot (Maven/npm/Actions wöchentlich); Framework-Majors manuell via `ng update` |

## 3. Architektur: Hexagonal (Ports & Adapters) + DDD Tactical Design

Hexagonal definiert, *wo* Code lebt; DDD Tactical Design definiert, *welche Form* der Code im Inneren hat. KISS: Pakete/Bausteine erst anlegen, wenn ein echter Use Case sie braucht.

### 3.1 Backend-Paketstruktur

```
<base-package>/
├── domain/                    # Inneres Hexagon — reine Geschäftslogik, KEINE Framework-Imports
│   ├── model/                 # Aggregates, Entities, Value Objects
│   ├── event/                 # Domain Events            (anlegen wenn gebraucht)
│   ├── service/               # Domain Services          (anlegen wenn gebraucht)
│   ├── factory/               # Factories                (anlegen wenn gebraucht)
│   └── port/
│       ├── in/                # Driving Ports  — Use-Case-Interfaces
│       └── out/               # Driven Ports   — Repository-/Publisher-Interfaces
├── application/
│   └── service/               # Application Services — orchestrieren Use Cases, Transaktionsgrenze
└── adapter/
    ├── in/
    │   ├── rest/              # Driving Adapter (JAX-RS Resources)
    │   │   └── dto/           # Transport-Objekte der REST-Schicht
    │   └── security/          # SecurityIdentityAugmentor (Rollen-Mapping)
    └── out/
        └── persistence/       # Driven Adapter (JPA-Entities, Panache-Repositories)
```

### 3.2 Abhängigkeitsregel (unverhandelbar)

`adapter → application → domain`; Adapter implementieren `domain/port/out`. `domain/` hat null Framework-Abhängigkeiten (kein Quarkus, kein JPA, kein Jackson); Domänen-Modelle sind reine Java-`records`. Use-Case-Interfaces in `port/in/`, Repository-Interfaces in `port/out/`. Adapter hängen an der Domäne, nie umgekehrt. Adapter referenzieren einander nicht.

### 3.3 DDD-Bausteine

| Baustein | Lebt in | Regel |
|---|---|---|
| Aggregate Root | `domain/model/` | Konsistenz-/Transaktionsgrenze; einziger Einstiegspunkt ins Aggregat |
| Entity | `domain/model/` | Stabile Identität; Gleichheit per ID |
| Value Object | `domain/model/` | Immutable `record`; Invarianten im Compact-Constructor (fail fast) |
| Domain Event | `domain/event/` | Immutable `record`, Vergangenheitsform; publiziert über Driven Port |
| Domain Service | `domain/service/` | Zustandslose Domänenlogik über Aggregate hinweg; pur |
| Repository | Port `domain/port/out/`, Impl `adapter/out/persistence/` | Pro Aggregate Root; nimmt/liefert Domänen-Modelle, nie JPA-Entities |
| Factory | `domain/factory/` | Komplexe Aggregat-Erzeugung |
| Application Service | `application/service/` | Orchestriert Use Case, hält Transaktionsgrenze, keine Geschäftsregeln |

Regeln: Aggregate per ID referenzieren, nie per Objektreferenz · ganze Aggregate laden/speichern · Invarianten im Aggregat erzwingen · Application ≠ Domain Service strikt trennen · Domänen-Modelle immutable («Mutation» liefert neue Instanz).

### 3.4 Architektur-Invarianten erzwingen (ArchUnit)

`HexagonalArchitectureTest` bricht den Build bei Schichtverletzungen:
- Framework-Import in `domain/`
- `application/` greift auf Adapter zu
- Adapter referenzieren einander (`adapter.in.*` ↔ `adapter.out.*`)
- REST-DTOs ausserhalb `adapter/in/rest/dto/`
- JPA-Entity ausserhalb `adapter/out/persistence/`

Implementiert als reguläre JUnit-Jupiter-Tests mit `ClassFileImporter` (nicht `@ArchTest`/`@AnalyzeClasses` — die ArchUnit-JUnit-Engine wird unter JUnit Platform 6 nicht ausgeführt).

## 4. Backend-Konventionen (Java)

`records` für Domänen-Modelle, Events und DTOs · Value-Object-Invarianten im Compact-Constructor · JPA-Entities nur in `adapter/out/persistence/` (öffentliche Felder OK) · Panache-Repository-Pattern hinter `port/out/` · `@ApplicationScoped` · Konstruktor-Injection · kein `null` als Rückgabe (`Optional`) · früh am Systemrand validieren, internem Code vertrauen.

## 5. Frontend-Konventionen (Angular)

Frontend spiegelt die REST-DTOs (publizierte Sprache des Backends), nicht das Domänen-Modell — keine Invarianten, keine Aggregat-Regeln.

```
webapp/src/app/
├── core/
│   ├── models/      # Interfaces, spiegeln die REST-DTOs
│   └── services/    # Use-Case-Logik, REST-Zugriff
├── features/        # Driving Adapter — UI-Komponenten je Route
└── shared/          # Wiederverwendbare UI-Bausteine (anlegen wenn gebraucht)
```

Standalone Components (nie NgModules; `standalone: true` nicht setzen) · Signals (`signal()`, `computed()`, `effect()`) · `inject()` statt Konstruktor-Injection · `input()`/`output()` statt Decorators · `OnPush` überall · Native Control Flow `@if`/`@for`/`@switch` · `providedIn: 'root'` · strict TS, kein `any` · feature-basierte Ordner, `app-*`-Präfix · ESLint erzwingt diese Konventionen als Build-Baseline.

## 6. Persistenz-Prinzipien

- **Liquibase besitzt das Schema** (`migrate-at-start=true`); Hibernate läuft im `validate`-Modus.
- Migrationen append-only & unveränderlich; jede Änderung = neue Datei + Change-Log-Eintrag.
- **Dev Services** starten PostgreSQL automatisch (Container-Runtime nötig).
- **Prod**: Neon PostgreSQL (serverless); Connection Pooling über den Neon-Proxy-Endpunkt (`?sslmode=require`), kurze `max-lifetime` (3 Min) wegen aggressivem Verbindungs-Timeout.
- **JSONB-Snapshots** für eingebettete, versionierte Wertobjekte; Persistenz-Modell = Domänen-record. Feldänderungen erfordern schema-bewusste Datenmigration.
- DB-Spalten: `snake_case`.

## 7. Messaging

Bewusst nicht im Template enthalten — erst hinzufügen, wenn ein echter Use Case es braucht.

Konventionen für den Einsatz: Kafka als Event-Backbone (SmallRye Reactive Messaging). Domain Events nach dem Persistieren über Driven Port publizieren (Application Service = Transaktionsgrenze). `%test`-Profil: Outgoing-Channels auf In-Memory-Connector. Transaktionale Observer (`@Observes(AFTER_SUCCESS)`) mit `@Transactional(REQUIRES_NEW)`, ggf. Worker-Thread.

## 8. Authentifizierung & Autorisierung (OIDC BFF-Pattern)

- **BFF-Session-Cookie statt Token im Browser**: `application-type=web-app`, PKCE, SameSite, Session-Verlängerung.
- **Rollen-Mapping am OIDC-Boundary**: `SecurityIdentityAugmentor` mappt IdP-Rollennamen auf interne, stabile Rollen; `@RolesAllowed`, Frontend und Tests nutzen nur interne Namen.
- IdP-Rollen als Client-Rollen mit App-Präfix (`<app>-<rolle>`), aus `resource_access/<client>/roles`.
- Auth in Dev/Test aus (Ersatz-Identität), in Prod an — über Profile (§9).
- In Prod: `authenticated`-Policy auf `/*`; `/q/health/*` ohne Auth für den Orchestrator (Fly.io Health-Check).
- **Row-Level-Security serverseitig** aus der authentifizierten Identität, nie aus dem Request-Body.
- Security-Header in Prod: CSP (`style-src 'unsafe-inline'` für Angular-Laufzeit-Styles), Referrer-Policy, Permissions-Policy.

## 9. Konfiguration & Profile

| Profil | Zweck |
|---|---|
| `%dev` | Frontend-Dev-Server, Dev Services (PostgreSQL), Auth aus |
| `%test` | zufälliger HTTP-Port, kein Frontend-Dev-Server, Auth aus |
| `%prod` | Auth an, Security-Header, `authenticated`-Policy |
| `%fly` | additiv zu `%prod` — Neon-DB-URL, Connection-Pool-Limits, Fly-Health-Pfade ohne Auth |
| `%<idp>` (optional) | OIDC gegen echten IdP, additiv kombinierbar (`-Dquarkus.profile=dev,<idp>`) |

`fly.toml` setzt `QUARKUS_PROFILE=prod,fly`.

## 10. Test-Strategie

Unit (Domäne + Application Services, Ports gemockt, kein Container) · `@QuarkusTest` gegen Dev Services · Verhalten testen, nicht Implementierung · ArchUnit- und Konsistenz-Tests in der Suite · geteilte Test-DB: keine positions-/zählungsabhängigen Assertions auf Fremddaten, dedizierte Fixtures, Zeitbezüge relativ.

## 11. CI/CD & Betrieb

CI (`ci.yml`: backend + frontend) auf jeden PR — verify als Merge-Gate. Release: Tag `v*` → `deploy.yml` → `flyctl deploy --remote-only` → Rolling-Deploy auf Fly.io (kein eigenes Container-Registry nötig, Fly baut remote). Dependabot hebt Maven/npm/Actions-Versionen wöchentlich; Framework-Majors (Angular, TypeScript) werden manuell via `ng update` gehoben. CI-Diagnose: erst klären ob Failure PR-eigen oder flaky, statt blind re-runnen.

## 12. Clean Code & Naming

Single Responsibility · Dependency Inversion · KISS/YAGNI · DRY erst ab 3+ Vorkommen · Fail Fast an Systemgrenzen · Klassen = was sie sind, Methoden = was sie tun · keine technischen Suffixe (`*Aggregate`, `*VO`) · nur öffentliche APIs dokumentieren (Warum, nicht Was) · kein toter Code, keine TODOs ohne Issue, keine Magic Numbers, frühe Returns.

## 13. Checkliste neue App

1. Template klonen, `init.sh <app-name> <base-package>` ausführen
2. Beispiel-Durchstich `Note` durch Fachlichkeit ersetzen
3. `./mvnw verify` — alle Tests, ArchUnit, Coverage-Gate grün
4. Neon-Projekt anlegen, `flyctl apps create`, Secrets setzen (DB_URL, OIDC_*)
5. `FLY_API_TOKEN` als GitHub-Secret hinterlegen
6. `fly.toml`: `app`-Name und `primary_region` anpassen
7. `git tag v0.1.0 && git push --tags` — erstes Deployment
8. OIDC BFF + Rollen-Mapping-Augmentor konfigurieren (IdP, Client-ID, Rollen)
9. Dependabot läuft automatisch; CI verifiziert jeden Update-PR
