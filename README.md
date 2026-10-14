# Employee Leave Management API

A Spring Boot REST API for creating, retrieving and listing employee leave requests, with manager approval.

Built with Spring Boot 4.1, Spring Data JPA (H2 in-memory database), Spring Security (HTTP Basic) and springdoc (Swagger UI).

## Running it

Requires JDK 21 or newer.

```bash
./mvnw spring-boot:run      # start the app on http://localhost:8080
./mvnw test                 # run the test suite
```

Open **http://localhost:8080/swagger-ui.html**, click **Authorize** and log in with one of the seeded users below.

The database is in memory and refilled with sample data on every start.

## Seeded users

All users have the password `password123`.

| Username | Role | Leave balance |
|---|---|---|
| `alice` | EMPLOYEE | 17 days |
| `bob` | EMPLOYEE | 15 days |
| `carol` | MANAGER | 25 days |
| `dave` | MANAGER | 25 days |

Any manager can decide on any request except their own. Employees have no fixed line manager.

Sample leave requests are also loaded, including two **overlapping** pending requests for `bob` (ids 3 and 4) so the concurrency rules can be tried by hand.

## Endpoints

| Method and path | Who | Purpose |
|---|---|---|
| `POST /api/leave-requests` | any user | Create a leave request for yourself |
| `GET /api/leave-requests/{id}` | owner or manager | Retrieve one request |
| `GET /api/leave-requests?status=&employeeId=&page=&size=` | any user | List requests; managers see all, employees only their own |
| `POST /api/leave-requests/{id}/approve` | manager | Approve a pending request |
| `POST /api/leave-requests/{id}/reject` | manager | Reject a pending request |
| `POST /api/leave-requests/{id}/cancel` | owner | Withdraw your own pending request |
| `GET /api/leave-requests/working-days?startDate=&endDate=` | any user | Preview how many days a range costs |
| `GET /api/employees/me` | any user | Your profile and remaining balance |
| `GET /api/employees` | manager | List all employees |

Errors use the standard problem-details JSON format. Common statuses: `400` invalid input, `401` not logged in, `403` not allowed, `404` not found, `409` conflict with the current state, `422` business rule broken (for example, not enough balance).

## Project layout

```
src/main/java/com/example/demo
├── entity/        JPA entities: Employee, LeaveRequest, PublicHoliday
├── model/         Enums: Role, LeaveStatus
├── dto/           Request and response records
├── repository/    Spring Data repositories, including the locking queries
├── service/       Business logic: LeaveRequestService, WorkingDayCalculator
├── controller/    REST controllers
├── security/      Security configuration and database-backed login
├── exception/     Exceptions and the global error handler
└── config/        Swagger configuration and the startup data seeder
```

---

## Logic 1 — Date calculation

> A request runs from Friday to the following Tuesday. How many leave days should be deducted?

**Answer: 3 working days.**

The range includes both the first and last day. Saturdays, Sundays and public holidays are not charged.

| Day | Fri | Sat | Sun | Mon | Tue |
|---|---|---|---|---|---|
| Charged? | ✅ | — | — | ✅ | ✅ |

5 calendar days, 3 working days.

**How it is implemented** — [`WorkingDayCalculator`](src/main/java/com/example/demo/service/WorkingDayCalculator.java):

```java
return (int) start.datesUntil(end.plusDays(1))   // inclusive range
        .filter(d -> !isWeekend(d))
        .filter(d -> !holidays.contains(d))
        .count();
```

- Public holidays are stored in the `public_holidays` table, so a holiday on the Monday makes the same request cost 2 days.
- The number of days is calculated once, when the request is created, and saved with it. Approval deducts exactly that number from the balance.
- A range with no working days (for example, a weekend only) is rejected with `422`.

Try it: `GET /api/leave-requests/working-days?startDate=2026-10-16&endDate=2026-10-20` returns `"calendarDays": 5, "workingDays": 3`.

---

## Logic 2 — Concurrent approvals

> Two managers approve overlapping requests at nearly the same time. How would you prevent both requests from being approved?

**Answer: each approval runs in one transaction that locks the employee's database row first. Two approvals for the same employee therefore run one after the other, never at the same time. The second one sees the first approval and is refused with `409 Conflict`.**

### Transaction boundary

`LeaveRequestService.approve()` is `@Transactional`. Everything happens inside that one transaction: reading the request, checking for overlaps, checking the balance, deducting days and changing the status. Either all of it is saved or none of it is.

### Locking

Inside the transaction, rows are locked with `SELECT ... FOR UPDATE` (pessimistic write lock), always in the same order:

1. **The employee who owns the leave** — `EmployeeRepository.findByIdForUpdate`
2. **The leave request itself** — `LeaveRequestRepository.findByIdForUpdate`

The employee row is shared by all of that person's requests, so it is the right thing to lock. Locking only the two request rows would not work, because the two managers are approving *different* requests.

What happens in the race:

```
Manager A (request 3)                Manager B (request 4, overlaps 3)
─────────────────────                ─────────────────────────────────
lock employee bob   ✔
lock request 3      ✔                lock employee bob   ⏳ waits...
no approved overlap ✔
balance ok, deduct 5
status = APPROVED
commit, locks released  ───────────► lock employee bob   ✔
                                     lock request 4      ✔
                                     overlap with approved request 3 ✘
                                     → 409 Conflict, rollback
```

Taking the locks in a fixed order (employee, then request) means two transactions can never each hold a lock the other is waiting for, so there are no deadlocks. A lock wait times out after 10 seconds.

The same lock also protects the leave balance. Two approvals that don't overlap but together exceed the balance are also handled correctly: the second one sees the reduced balance.

### Optimistic concurrency as a backstop

`Employee` and `LeaveRequest` both have a `@Version` column. If any code path ever writes these rows without taking the lock, the second writer fails with an optimistic-lock error (returned as `409`) instead of silently overwriting the first.

### Database constraints

The database itself enforces:

| Constraint | Rule |
|---|---|
| `ck_employees_balance_non_negative` | `leave_balance >= 0` — the balance can never go negative |
| `ck_leave_requests_date_range` | `end_date >= start_date` |
| `ck_leave_requests_working_days` | `working_days > 0` |

On PostgreSQL in production, an **exclusion constraint** could also make the database reject two overlapping approved ranges for the same employee. H2 does not support this, so here the row lock is the guard.

```sql
ALTER TABLE leave_requests ADD CONSTRAINT no_overlapping_approved_leave
  EXCLUDE USING gist (employee_id WITH =, daterange(start_date, end_date, '[]') WITH &&)
  WHERE (status = 'APPROVED');
```

---

## Logic 3 — Duplicate approval

> A manager clicks Approve twice because the frontend freezes. What should the API do?

**Answer: the second click should succeed without changing anything. The API returns `200 OK` with the request as it already is, adds the header `Idempotent-Replayed: true`, and does not deduct the balance a second time.**

### Safe state transitions

A leave request can only leave the `PENDING` state once ([`LeaveStatus`](src/main/java/com/example/demo/model/LeaveStatus.java)):

```
            ┌──► APPROVED
PENDING ────┼──► REJECTED
            └──► CANCELLED
```

`APPROVED`, `REJECTED` and `CANCELLED` are final. The entity checks every transition and throws if it is not allowed.

### Handling a repeated decision

| Current status | Action | Response |
|---|---|---|
| `PENDING` | approve | `200`, approved, balance deducted, `Idempotent-Replayed: false` |
| `APPROVED` | approve again | `200`, unchanged, **no second deduction**, `Idempotent-Replayed: true` |
| `REJECTED` / `CANCELLED` | approve | `409 Conflict` — a final decision cannot be reversed |
| `REJECTED` | reject again | `200`, unchanged, `Idempotent-Replayed: true` |
| `APPROVED` | reject | `409 Conflict` |

On a replay, the original decision is kept as it was: the same `decidedBy`, `decidedAt` and comment.

Returning `200` (not an error) for a repeat is deliberate. The client gets the result it wanted, a frozen frontend can safely retry, and nothing is applied twice.

### Clicks that arrive at the same moment

If both clicks reach the server at exactly the same time, the row locks from Logic 2 make them run one after the other. The first one approves; the second one waits, then sees `APPROVED` and returns the replay. The balance is deducted once.

---

## Tests

| Test class | What it checks |
|---|---|
| `WorkingDayCalculatorTest` | Friday to Tuesday = 3; a holiday on the Monday = 2; weekend only = 0; a full week = 5; invalid ranges |
| `LeaveApprovalConcurrencyTest` | Two managers approve overlapping requests on two threads at once: exactly one succeeds, the balance is deducted once. Two simultaneous approve clicks: one real approval, one replay. Approving a rejected request is a conflict. |
| `LeaveRequestControllerTest` | Login required (`401`), employees cannot approve (`403`), employees only see their own requests, validation (`400`), and the full create → approve → approve-again → reject flow over HTTP |

The concurrency tests use real database transactions on separate threads, not mocks.
