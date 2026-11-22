# IncidentIQ: Intelligent IT Service Management and Incident Resolution

A Spring Boot web application that manages the full lifecycle of IT incidents:
**report → classify → prioritise → assign → investigate → resolve → verify → close.**
Employees report problems, the system prioritises them and assigns a suitable technician
automatically, technicians work them through an enforced lifecycle, SLAs are monitored in the
background, and admins monitor and configure everything from a live dashboard.

Built for the CS F213 / MAC F212 Object-Oriented Programming group project (Semester I, 2026-27).

## Requirements

- Java 17 or newer
- Nothing else to install: the Maven wrapper (`mvnw`) downloads Maven and all libraries on first run
- An internet connection in the browser (Bootstrap, icons and charts load from a CDN)

## Run the app

```
./mvnw spring-boot:run
```

On Windows you can also double-click `run.bat`. Wait for `Started IitsmApplication`, then open
<http://localhost:8080>. Press `Ctrl+C` to stop.

Data is stored in an H2 database file in `data/`, so it survives restarts. To start from a clean
database, stop the app and delete the `data/` folder; demo accounts and defaults are recreated.

## Run the tests

```
./mvnw test
```

160+ JUnit tests: unit tests for the domain rules, patterns and strategies, and Spring integration
tests that run against a temporary in-memory database (they never touch `data/`).

## Demo accounts

| Role | Username | Password | Notes |
|------|----------|----------|-------|
| Employee | `employee1`, `employee2` | `Demo@1234` | New employees can also sign up |
| Technician | `tech1` (Ravi) | `Demo@1234` | Network, Security |
| Technician | `tech2` (Meera) | `Demo@1234` | Database, Application |
| Technician | `tech3` (Arjun) | `Demo@1234` | Authentication, Cloud, Network |
| Admin | `admin` | `ChangeMe123!` | Set `APP_ADMIN_PASSWORD` outside local demos |

Usernames are case-insensitive.

## What each role can do

**Employee**
- Sign up with a company e-mail address (accounts are always Employee accounts).
- Report an incident with a short subject and a description; the category is suggested automatically
  while typing. Not sure which service is affected? Choose "I don't know": the system identifies it
  from the text (or uses "Not specified", medium criticality) and the technician can correct it,
  which recalculates the priority.
- Track own incidents: progress tracker, technician, expected resolution time, history.
- Add information, answer the technician's questions, confirm the fix or reopen it ("still broken"),
  and rate the service (1-5 stars).

**Technician**
- Work queue sorted by deadline, with live SLA countdowns and own workload figures.
- Acknowledge, start work and resolve; write internal investigation notes, messages to the
  employee, or requests for more information.
- Request re-routing when the category is wrong; confirm or dismiss duplicate suggestions.

**Admin**
- Live dashboard (auto-refresh): open problems, SLA at risk / violated, charts, "needs attention",
  service analysis, technician workload, employee satisfaction.
- All incidents with 9 filters (status, priority, category, technician, SLA, service, date range,
  text or ID), paginated; change priority (with reason); approve re-routing; close an incident
  without the employee's confirmation (override with reason).
- Users: create employees, technicians (with skills) and admins; deactivate accounts (open
  incidents are handed over); set technicians available/unavailable; assign queued incidents.
- Configure IT services (criticality, dependencies), incident categories (with suggester keywords),
  SLA policies per priority and service-specific SLAs, the assignment strategy and the priority strategy.
- Audit log (paginated, searchable) and simulated e-mail outbox.

## Incident lifecycle

```
NEW -> CLASSIFIED -> ASSIGNED -> ACKNOWLEDGED -> IN_PROGRESS -> RESOLVED -> VERIFIED -> CLOSED
```

Each status is a State class that knows its legal next statuses; anything else throws
`InvalidStateTransitionException`. Deliberate extra transitions: ACKNOWLEDGED/IN_PROGRESS → ASSIGNED
(handover or re-routing to a new technician) and RESOLVED → IN_PROGRESS (employee reopens).

Who closes: the **employee confirms** the fix (RESOLVED → VERIFIED → CLOSED); if they do not answer
within 3 days (`app.auto-close-after`) it closes automatically; the admin can override with a reason.

## AI category suggestions (Google Gemini, optional)

While an employee types, the category is suggested. With a Gemini API key the suggestion comes from
**Google's Gemini API** (third-party integration); without one, or if Gemini is slow, down or gives an
answer that is not an active category, local keyword rules answer instead, so it always works.
`GeminiCategorySuggester` is an **Adapter** from Gemini's REST API to our `CategorySuggester` interface,
chained before the keyword rules by `FallbackCategorySuggester`.

To turn it on, create a free key in Google AI Studio and start the app with it:

```
export GEMINI_API_KEY=your-key-here      # Windows: set GEMINI_API_KEY=your-key-here
./mvnw spring-boot:run
```

The key is read only from that environment variable (never stored in the repository). The model is
set by `app.ai.gemini.model`. The admin dashboard shows whether AI suggestions are on. Note: with AI
on, the incident description is sent to Google for classification; a production deployment would
need a data-processing agreement.

## Key rules

- **Priority** (default, score-based) = impact × urgency × the affected service's business criticality
  (each 1-3); 18+ = P1, 9+ = P2, 4+ = P3, otherwise P4. Alternative **weighted** strategy:
  0.5 × impact + 0.3 × urgency + 0.2 × criticality; 2.6+ = P1, 2.0+ = P2, 1.5+ = P3, otherwise P4.
  The admin switches strategies on the dashboard (new incidents only; saved and audited) and can
  change one incident's priority with a reason.
- **Assignment**: skill-based (default) or load-based strategy, switchable at runtime and saved.
  Technicians at the workload limit (8 active incidents, `app.max-active-incidents`) are skipped.
  If nobody fits, the report is **queued** (never lost) and assigned automatically when someone is free.
- **SLA**: response and resolution targets per priority (admin-configurable), optionally per service.
  A background job checks every open incident every minute: at-risk at 70% of the time, violated at
  100%; alerts are never repeated (remembered in the database). Incidents resolved late count as violations.
- **Escalation**: an unacknowledged P1 climbs one level per response period
  (technician → team lead → system admin → IT manager), notifying the admins.
- **Duplicates**: similar open incidents (text similarity with synonyms) are suggested, never linked
  automatically; suspected duplicates go to the same technician; linked reports follow their parent
  and do not count as extra workload.
- **Workload counters** are recounted from the incidents every 5 minutes (self-correcting).

## Design

Layered architecture: Thymeleaf pages and controllers (`web`) → services and strategies
(`service`, `strategy`) → domain model (`model`, `state`, `factory`) → repositories and JPA entities
(`repository`, `persistence`) → H2 database.

| Package | Purpose |
|---------|---------|
| `model` | Domain classes: `User` hierarchy, `Incident`, `Technician`, priorities, statuses |
| `state` | One class per lifecycle status (State pattern) |
| `factory` | `IncidentFactory`: validated incident creation (Factory pattern) |
| `strategy` | Priority, assignment, similarity and category-suggestion strategies (Strategy pattern) |
| `service` | Processing, SLA calculation and monitoring, escalation, duplicates, catalogues, background jobs |
| `observer`, `notification` | SLA/escalation listeners and incident events → notification channels (Observer pattern) |
| `repository`, `persistence` | Spring Data repositories and JPA entities (Repository pattern) |
| `security` | Login, roles, account rules, deactivation check |
| `web` | Controllers, view models, error handling, and the incident use-case services (below) |

The pages talk to one **facade**, `IncidentWorkflowService` (reporting and the core lifecycle), which
delegates specialised use cases to focused services: `TechnicianHandoverService` (queue, workload,
handover), `ResolutionService` (employee confirmation, reopen, auto-close, admin close),
`ReroutingService` and `DuplicateLinkingService`. Rules they share (picking within the workload
limit, moving an incident, keeping linked reports in step) live in `IncidentOperations`.

Design patterns: **State** (lifecycle), **Strategy** (priority, assignment, similarity, category
suggestion), **Observer** (SLA alerts, escalations and incident events → notifications), **Factory**
(incident creation), **Repository** (persistence), **Facade** (the workflow service), **Adapter** (Gemini API).

Notifications go through every `NotificationChannel`: **in-app** (bell icon) and **simulated e-mail**
(an outbox the admin can read, addressed to each user's registered e-mail). Adding a channel is one
new class.

## Security

- Spring Security login; passwords hashed with BCrypt; role-based access on every page
- CSRF protection on every form; input validation on all forms and in the domain model
- Self sign-up only as Employee, only with a company e-mail address (`app.signup-email-domain`),
  reserved names blocked, strong passwords required; rate limit on reports (`app.max-reports-per-hour`)
- Deactivated users are logged out on their next request
- Full audit trail: who, what, when, previous and new state
- H2 web console disabled by default (admin-only when enabled); a warning is logged when the
  default admin password is used

## Error handling

Custom exceptions for each case in the brief (invalid transition, invalid incident data, technician
unavailable, workload exceeded, missing SLA configuration, invalid SLA/catalogue/account input).
Users see friendly pages for 400, 403, 404 and 503 (**database unavailable**, via
`GlobalExceptionHandler`); stack traces are never shown. **Network/notification failures** are
isolated: a failing channel is logged and skipped, and the incident action still succeeds.

## Configuration (`src/main/resources/application.properties`)

| Property | Default | Meaning |
|----------|---------|---------|
| `app.brand` | IncidentIQ | Product name shown in the UI |
| `app.max-reports-per-hour` | 20 | Reports per employee per hour |
| `app.max-active-incidents` | 8 | Workload limit per technician |
| `app.auto-close-after` | 3d | Auto-close of unconfirmed resolved incidents |
| `app.signup-email-domain` | company.com | Allowed sign-up e-mail domain |
| `APP_ADMIN_USERNAME` / `APP_ADMIN_PASSWORD` | admin / ChangeMe123! | Initial admin (environment variables) |

## Known limitations and future work

- Single instance with a local H2 file; production would use PostgreSQL (a configuration change,
  thanks to JPA) and several instances behind a load balancer.
- Dashboard and catalogue pages still aggregate over all incidents; production would use count
  queries. Lists that grow with use (incidents, audit log) are paginated.
- E-mail is simulated (outbox); there is no e-mail verification at sign-up, no password reset and no
  lockout after repeated wrong passwords; no REST API of our own.
- Two reports of the same problem in the same second do not see each other as duplicates.
- Removing a technician's skill does not move their open incidents of that category (the admin can).
