# I-ITSM: Incident and IT Service Management

A Spring Boot web application for reporting, classifying, assigning and resolving IT incidents.
Employees report problems, the system prioritises and assigns them to a technician automatically,
technicians work them through a fixed lifecycle, and admins monitor everything on a dashboard.

Built for the CS F213 / MAC F212 Object-Oriented Programming group project.

## Requirements

- Java 17 or newer
- Nothing else to install: the Maven wrapper (`mvnw`) downloads Maven and all libraries on first run

## Run the app

From the project folder:

```
./mvnw spring-boot:run
```

On Windows you can also double-click `run.bat`.

Wait for the line `Started IitsmApplication`, then open <http://localhost:8080>.
Press `Ctrl+C` in the terminal to stop the app.

Data is stored in a local H2 database file in the `data/` folder, so incidents survive restarts.
To start from a clean database, stop the app and delete the `data/` folder.

## Run the tests

```
./mvnw test
```

## Demo logins

| Role | Username | Password |
|------|----------|----------|
| Employee | `employee1`, `employee2` | `Demo@1234` |
| Technician | `tech1`, `tech2`, `tech3` | `Demo@1234` |
| Admin | `admin` | `ChangeMe123!` |

Technician skills: `tech1` Network and Security; `tech2` Database and Application;
`tech3` Authentication, Cloud and Network.

## What each role can do

**Employee**
- Report an incident: description, category, impact, urgency and the affected service.
- The system warns about possibly duplicate open incidents, then classifies the priority (P1 to P4),
  picks a technician by skill and workload, and sets the response and resolution deadlines.
- See their own incidents, with status, assigned technician and SLA state.

**Technician**
- See incidents assigned to them, with SLA status and deadlines.
- Move an incident through Acknowledge, Start work and Mark resolved (resolution notes are required).

**Admin**
- See all incidents and filter by status, priority, category, technician, SLA status and text.
- Verify resolved incidents and close verified ones.
- Dashboard: counts by status and priority, SLA at risk and violated, technician workload.
- Switch the technician assignment strategy while the app runs.

## Incident lifecycle

```
NEW -> CLASSIFIED -> ASSIGNED -> ACKNOWLEDGED -> IN_PROGRESS -> RESOLVED -> VERIFIED -> CLOSED
```

Illegal jumps (for example ASSIGNED straight to CLOSED) are rejected by the State pattern.

## Project structure

All code is under `src/main/java/com/iitsm/iitsm/`:

| Package | Purpose |
|---------|---------|
| `model` | Domain classes: Incident, Technician, Employee, priorities, statuses |
| `factory` | `IncidentFactory`: validated incident creation (Factory pattern) |
| `state` | One class per lifecycle status (State pattern) |
| `strategy` | Interchangeable priority, assignment and duplicate-matching rules (Strategy pattern) |
| `service` | Incident processing, SLA calculation, escalation, duplicate detection |
| `persistence` | JPA entities, repositories and persistence services (H2 database) |
| `security` | Login, password hashing and role handling (Spring Security) |
| `web` | Controllers, page view models and the incident workflow used by the pages |

Pages are Thymeleaf templates in `src/main/resources/templates/` (Bootstrap 5 from a CDN,
so the browser needs an internet connection to load the styling).

## Known limitations

- Runs on one machine: the database is a local file, so separate copies of the app do not share data.
- Demo passwords and the default admin password are in the code; change them before any real use.
- CSRF protection is disabled and the H2 console is enabled, which is fine for a demo but not for production.
- The scheduled SLA monitor reads from an in-memory source, so the dashboard computes SLA status on demand instead.
- The incident id counter restarts at 1000 on every start; the web layer skips ids already in the database.
- There is no user registration; accounts are created by the demo data initializer.
