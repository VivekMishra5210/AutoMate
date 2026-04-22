# AutoMate Backend — Complete Function-by-Function Explanation
## IIIT Nagpur Campus Transport App

---

# TABLE OF CONTENTS
1. [Project Architecture Overview](#1-project-architecture-overview)
2. [config.py — Application Configuration](#2-configpy--application-configuration)
3. [database.py — Database Connection Manager](#3-databasepy--database-connection-manager)
4. [app.py — Main Application Entry Point](#4-apppy--main-application-entry-point)
5. [models.py — Data Models (Dataclasses)](#5-modelspy--data-models-dataclasses)
6. [queue_logic.py — Core Queue Business Logic](#6-queue_logicpy--core-queue-business-logic)
7. [notification.py — Firebase Push Notifications](#7-notificationpy--firebase-push-notifications)
8. [seed.py — Test Data Seeder](#8-seedpy--test-data-seeder)
9. [routes/auth_routes.py — Authentication API Endpoints](#9-routesauth_routespy--authentication-api-endpoints)
10. [routes/queue_routes.py — Queue Management API Endpoints](#10-routesqueue_routespy--queue-management-api-endpoints)
11. [routes/complaint_routes.py — Complaint API Endpoints](#11-routescomplaint_routespy--complaint-api-endpoints)
12. [database/schema.sql — Database Schema](#12-dabortschemasql--database-schema)
13. [How Everything Connects — Request Flow](#13-how-everything-connects--request-flow)

---

# 1. PROJECT ARCHITECTURE OVERVIEW

```
AutoMate Backend uses a layered architecture:

┌──────────────────────────────────────────────────┐
│        Android App (Student/Driver/Admin)         │
│       Makes HTTP requests to the backend          │
└─────────────────────┬────────────────────────────┘
                      │  HTTP (REST API)
                      ▼
┌──────────────────────────────────────────────────┐
│              app.py (Flask Server)                │
│     Creates the app, registers all routes         │
│     Handles CORS, JWT setup, health check         │
└─────────────────────┬────────────────────────────┘
                      │
        ┌─────────────┼─────────────┐
        ▼             ▼             ▼
┌──────────┐  ┌──────────────┐  ┌──────────────┐
│auth_routes│  │queue_routes  │  │complaint_    │
│  .py      │  │  .py         │  │  routes.py   │
│(login,    │  │(join queue,  │  │(submit,      │
│ register) │  │ attendance,  │  │ list,        │
│           │  │ start trip)  │  │ resolve)     │
└─────┬─────┘  └──────┬───────┘  └──────┬───────┘
      │               │                 │
      ▼               ▼                 ▼
┌──────────────────────────────────────────────────┐
│          queue_logic.py (Business Logic)          │
│    FCFS queueing, time slots, attendance,         │
│    wait time calculation, trip management          │
└─────────────────────┬────────────────────────────┘
                      │
                      ▼
┌──────────────────────────────────────────────────┐
│            database.py (DB Layer)                 │
│    get_db(), query_db(), execute_db(), init_db()  │
└─────────────────────┬────────────────────────────┘
                      │  SQLite3
                      ▼
┌──────────────────────────────────────────────────┐
│         automate.db (SQLite Database)             │
│    Tables: users, queue, trips,                   │
│            trip_passengers, complaints             │
└──────────────────────────────────────────────────┘

SUPPORTING FILES:
• config.py       → All settings (ports, DB paths, queue rules)
• models.py       → Python dataclasses mirroring DB tables
• notification.py → Firebase Cloud Messaging (push notifications)
• seed.py         → Inserts test users into the database
```

**Technology Stack:**
- **Language:** Python 3.12
- **Web Framework:** Flask (lightweight HTTP server)
- **Database:** SQLite3 (file-based, zero-configuration)
- **Authentication:** JWT (JSON Web Tokens) via flask-jwt-extended
- **Password Security:** bcrypt (one-way hashing)
- **Push Notifications:** Firebase Cloud Messaging (FCM)
- **Cross-Origin Requests:** flask-cors (so the Android app can connect)

---

# 2. config.py — Application Configuration

**Purpose:** This file holds ALL the application settings in one central place. Instead of hard-coding values like the database path or queue size throughout the code, we keep them here so they can be changed easily.

```
class Config
```

### Fields Explained:

#### SECRET_KEY
```python
SECRET_KEY = os.environ.get('SECRET_KEY', 'automate-dev-secret-key-change-in-production')
```
- **What it does:** Flask uses this to sign cookies and session data. It prevents tampering.
- **How it works:** First checks if there's an environment variable called `SECRET_KEY`. If not, uses the default string. In production, you'd set a random secret via environment variable.

#### DEBUG
```python
DEBUG = os.environ.get('FLASK_DEBUG', 'True').lower() == 'true'
```
- **What it does:** When True, Flask shows detailed error messages in the browser/terminal. Useful during development but should be False in production.
- **How it works:** Reads the `FLASK_DEBUG` environment variable. Converts to lowercase and checks if it equals 'true'. Default is True.

#### DATABASE_PATH
```python
DATABASE_PATH = os.environ.get('DATABASE_PATH', os.path.join(BASE_DIR, '..', 'database', 'automate.db'))
```
- **What it does:** Points to the SQLite database file on disk.
- **How it works:** `BASE_DIR` is the folder where config.py lives (`backend/`). `os.path.join(BASE_DIR, '..', 'database', 'automate.db')` goes up one folder and into `database/automate.db`.

#### SCHEMA_PATH
```python
SCHEMA_PATH = os.environ.get('SCHEMA_PATH', os.path.join(BASE_DIR, '..', 'database', 'schema.sql'))
```
- **What it does:** Points to the SQL file that defines all the database tables. Used when the server starts for the first time.

#### JWT_SECRET_KEY
```python
JWT_SECRET_KEY = os.environ.get('JWT_SECRET_KEY', 'automate-jwt-secret-change-in-production')
```
- **What it does:** A separate secret key specifically for signing JWT tokens. When a user logs in, the server creates a JWT token signed with this key. When the user makes subsequent requests, the server verifies the token using this same key.
- **Why separate from SECRET_KEY:** Security best practice — different keys for different purposes.

#### JWT_ACCESS_TOKEN_EXPIRES
```python
JWT_ACCESS_TOKEN_EXPIRES = 86400  # 24 hours in seconds
```
- **What it does:** JWT tokens expire after 24 hours (86400 seconds). After that, the user must log in again. This prevents stolen tokens from being used indefinitely.

#### FIREBASE_CREDENTIALS_PATH
```python
FIREBASE_CREDENTIALS_PATH = os.environ.get('FIREBASE_CREDENTIALS_PATH', os.path.join(BASE_DIR, 'firebase-credentials.json'))
```
- **What it does:** Points to the JSON file containing Firebase service account credentials. This is needed to send push notifications to phones.

#### Queue Settings (THE CORE BUSINESS RULES)
```python
PASSENGERS_PER_TRIP = 4       # Auto carries 4 students at a time
TIME_SLOT_MINUTES = 20        # Each time slot is 20 minutes long
OPERATING_START_HOUR = 6      # Service starts at 6:00 AM
OPERATING_END_HOUR = 23       # Service ends at 11:00 PM
MAX_ABSENCES = 2              # Student removed after 2 absent marks
```
- **PASSENGERS_PER_TRIP = 4:** An auto-rickshaw can carry 4 students. When 4 students are marked present, the trip can start.
- **TIME_SLOT_MINUTES = 20:** The day is divided into 20-minute slots (6:00-6:20, 6:20-6:40, etc.). This is used to estimate wait times.
- **OPERATING_START_HOUR = 6 / OPERATING_END_HOUR = 23:** Students can only join the queue between 6 AM and 11 PM. Outside these hours, attempts to join return "Service is closed."
- **MAX_ABSENCES = 2:** If a driver calls a student twice and they don't show up, they are permanently removed from the queue. First absence just moves them to the back.

---

# 3. database.py — Database Connection Manager

**Purpose:** Provides 4 simple functions that ALL other files use to talk to the SQLite database. No other file touches SQLite directly — they all go through this layer.

### Function: get_db()
```python
def get_db():
    db = sqlite3.connect(Config.DATABASE_PATH, timeout=5)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA foreign_keys = ON")
    return db
```
- **What it does:** Opens a new connection to the SQLite database file.
- **Line by line:**
  - `sqlite3.connect(Config.DATABASE_PATH, timeout=5)` — Opens the database file. The `timeout=5` means "if the database is locked by another process, wait up to 5 seconds before throwing an error." This prevents crashes when multiple requests hit the server at the same time.
  - `db.row_factory = sqlite3.Row` — Makes query results behave like dictionaries. Without this, results would be plain tuples like `(1, 'Rahul', 'rahul@iiitn.ac.in')`. With it, you can write `row['name']` instead of `row[1]`.
  - `db.execute("PRAGMA foreign_keys = ON")` — SQLite doesn't enforce foreign key constraints by default. This command turns them on, so you can't insert a queue entry with a `student_id` that doesn't exist in the `users` table.

### Function: close_db(db)
```python
def close_db(db):
    if db is not None:
        db.close()
```
- **What it does:** Safely closes a database connection. The `if db is not None` check prevents an error if you accidentally pass None.
- **Why it matters:** If you don't close connections, SQLite can lock the database file and other requests will fail.

### Function: init_db()
```python
def init_db():
    db_dir = os.path.dirname(Config.DATABASE_PATH)
    if db_dir and not os.path.exists(db_dir):
        os.makedirs(db_dir)
    db = get_db()
    try:
        with open(Config.SCHEMA_PATH, 'r') as f:
            schema_sql = f.read()
        db.executescript(schema_sql)
        db.commit()
        try:
            db.execute("PRAGMA journal_mode = WAL")
        except sqlite3.OperationalError:
            pass
        print(f"[DB] Database initialized at: {Config.DATABASE_PATH}")
    except Exception as e:
        print(f"[DB] Error initializing database: {e}")
        raise
    finally:
        close_db(db)
```
- **What it does:** Called once when the server starts. Reads the `schema.sql` file and creates all the database tables (if they don't already exist).
- **Line by line:**
  - `os.makedirs(db_dir)` — Creates the `database/` folder if it doesn't exist.
  - `db.executescript(schema_sql)` — Runs ALL the SQL statements from schema.sql (CREATE TABLE IF NOT EXISTS...).
  - `PRAGMA journal_mode = WAL` — Enables Write-Ahead Logging. This is a performance optimization for SQLite. Without WAL, only ONE process can read or write at a time. With WAL, multiple processes can READ simultaneously while one writes. This fixed the "database is locked" bug we had.
  - The `try/except` around WAL handles the edge case where the database is briefly busy.

### Function: query_db(query, args=(), one=False)
```python
def query_db(query, args=(), one=False):
    db = get_db()
    try:
        cursor = db.execute(query, args)
        results = cursor.fetchall()
        if one:
            return dict(results[0]) if results else None
        return [dict(row) for row in results]
    finally:
        close_db(db)
```
- **What it does:** Executes a SELECT query and returns results.
- **Parameters:**
  - `query` — The SQL string, e.g., `"SELECT * FROM users WHERE email = ?"`
  - `args` — Tuple of values to safely substitute into `?` placeholders. Using `?` placeholders prevents SQL injection attacks.
  - `one=False` — If True, returns only the first result (a single dict). If False, returns a list of dicts.
- **Example usage:**
  - `query_db("SELECT * FROM users WHERE email = ?", ("rahul@iiitn.ac.in",), one=True)` → Returns `{'id': 2, 'name': 'Rahul Sharma', 'email': 'rahul@iiitn.ac.in', ...}` or `None`
  - `query_db("SELECT * FROM queue WHERE status = 'waiting'")` → Returns a list like `[{'id': 1, ...}, {'id': 2, ...}]`

### Function: execute_db(query, args=())
```python
def execute_db(query, args=()):
    db = get_db()
    try:
        cursor = db.execute(query, args)
        db.commit()
        return cursor.lastrowid
    finally:
        close_db(db)
```
- **What it does:** Executes an INSERT, UPDATE, or DELETE query and commits the change.
- **Returns:** `cursor.lastrowid` — For INSERT statements, this is the auto-generated ID of the new row. For UPDATE/DELETE, this is 0 (not useful, but we handle that separately where needed).
- **`db.commit()`** — Saves the change to disk. Without this, the change would be lost when the connection closes.

---

# 4. app.py — Main Application Entry Point

**Purpose:** This is the file you run to start the server (`python app.py`). It creates the Flask application, sets up security, and connects all the route files (blueprints).

### Function: create_app()
```python
def create_app():
    app = Flask(__name__)
```
- **What it does:** This is called the "Application Factory Pattern." Instead of creating the Flask app at the top of the file, we create it inside a function. This lets us create multiple instances (useful for testing).

#### Configuration Setup
```python
    app.config['SECRET_KEY'] = Config.SECRET_KEY
    app.config['JWT_SECRET_KEY'] = Config.JWT_SECRET_KEY
    app.config['JWT_ACCESS_TOKEN_EXPIRES'] = timedelta(seconds=Config.JWT_ACCESS_TOKEN_EXPIRES)
```
- Loads all the settings from `config.py` into Flask's internal configuration dictionary.

#### CORS Setup
```python
    CORS(app, resources={r"/api/*": {"origins": "*"}})
```
- **What it does:** Enables Cross-Origin Resource Sharing. Browsers and apps normally block requests to a different domain/IP. CORS tells the browser "it's okay for anyone (`*`) to call my `/api/` endpoints."
- **Why needed:** The Android app running on a phone (IP 192.168.137.x) needs to call the server (IP 192.168.137.1). Without CORS, these requests would be blocked.

#### JWT Initialization
```python
    jwt = JWTManager(app)
```
- **What it does:** Initializes the JWT authentication system. This hooks into Flask to intercept requests and verify tokens automatically when `@jwt_required()` is used on routes.

#### JWT Error Handlers
```python
    @jwt.expired_token_callback
    def expired_token_callback(jwt_header, jwt_payload):
        return jsonify({'error': 'Token has expired', 'code': 'token_expired'}), 401

    @jwt.invalid_token_loader
    def invalid_token_callback(error):
        return jsonify({'error': 'Invalid token', 'code': 'invalid_token'}), 401

    @jwt.unauthorized_loader
    def unauthorized_callback(error):
        return jsonify({'error': 'Authorization required', 'code': 'authorization_required'}), 401
```
- **What these do:** When a user sends a bad, expired, or missing JWT token, instead of a generic error, the server returns clean JSON error messages that the Android app can understand.
- **expired_token_callback** — Token is older than 24 hours.
- **invalid_token_callback** — Token is malformed or signed with a different key.
- **unauthorized_callback** — No token was sent at all.

#### Blueprint Registration
```python
    app.register_blueprint(auth_bp)
    app.register_blueprint(queue_bp)
    app.register_blueprint(complaint_bp)
```
- **What it does:** Connects all the route files to the main app. A "Blueprint" in Flask is a modular group of routes. Instead of putting all 15+ endpoints in one giant file, we split them:
  - `auth_bp` → Login, Register, Profile
  - `queue_bp` → Join queue, Leave queue, Attendance, Start trip, Schedule
  - `complaint_bp` → Submit complaint, List complaints, Resolve complaint

#### Health Check Endpoint
```python
    @app.route('/api/health', methods=['GET'])
    def health_check():
        return jsonify({'status': 'running', 'service': 'AutoMate Backend', 'version': '1.0.0'}), 200
```
- **What it does:** A simple endpoint that returns "I'm alive." Used to quickly check if the server is running without needing authentication.
- **URL:** `GET http://192.168.137.1:5000/api/health`

#### Root Endpoint
```python
    @app.route('/', methods=['GET'])
    def root():
```
- **What it does:** When you visit the server's base URL in a browser, it shows a list of all available API endpoints. This is just for developer convenience.

#### Database Initialization
```python
    with app.app_context():
        init_db()
```
- **What it does:** Before the server starts accepting requests, create all database tables by running `schema.sql`. The `app.app_context()` is needed because Flask requires an "application context" to run database operations.

### Main Execution Block
```python
if __name__ == '__main__':
    app = create_app()
    app.run(host='0.0.0.0', port=5000, debug=Config.DEBUG, use_reloader=False)
```
- `if __name__ == '__main__'` — This block runs only when you execute `python app.py` directly (not when imported).
- `host='0.0.0.0'` — Listen on ALL network interfaces (not just localhost). This is essential so that phones on the WiFi hotspot can reach the server.
- `port=5000` — The server runs on port 5000.
- `debug=Config.DEBUG` — Shows detailed errors during development.
- `use_reloader=False` — **CRITICAL FIX.** Flask's debug mode normally starts TWO processes (one watches for file changes, one serves requests). Both processes open the SQLite database, causing "database is locked" errors. Setting this to False prevents the second process.

---

# 5. models.py — Data Models (Dataclasses)

**Purpose:** Defines Python classes that mirror the database tables. These are "blueprints" for what a User, QueueEntry, Complaint, or Trip looks like in Python.

**Note:** These models are available for use but the current routes use raw dictionaries from `query_db()` for simplicity. They exist as reference documentation and for future refactoring.

### Class: User
```python
@dataclass
class User:
    id: int
    name: str
    email: str
    role: str                          # 'student', 'driver', or 'admin'
    phone: Optional[str] = None        # Only drivers have this
    auto_number: Optional[str] = None  # Only drivers (e.g., "MH-31-AB-1234")
    fcm_token: Optional[str] = None    # For push notifications
    is_active: bool = True             # Can deactivate accounts
    created_at: Optional[str] = None   # Auto-set by database
```
- `@dataclass` — Python decorator that automatically generates `__init__`, `__repr__`, and `__eq__` methods. Saves boilerplate code.
- `Optional[str]` — Means the field can be either a string or None.
- **to_dict()** — Converts the User to a dictionary (for JSON responses). Explicitly removes `password_hash` so it's never exposed to the frontend.
- **from_row(row)** — Creates a User object from a database query result dict.

### Class: QueueEntry
```python
@dataclass
class QueueEntry:
    id: int
    student_id: int
    direction: str        # 'college_to_main' or 'main_to_college'
    time_slot: str        # e.g., "09:00"
    absence_count: int    # 0, 1, or 2 (removed at 2)
    status: str           # 'waiting', 'assigned', 'completed', 'removed'
    joined_at: str        # Timestamp when student joined
```

### Class: Complaint
```python
@dataclass
class Complaint:
    id: int
    student_id: int
    driver_id: Optional[int]
    complaint_type: str   # 'driver_absent', 'auto_not_arrived', 'misbehavior', 'other'
    message: str
    status: str           # 'pending', 'reviewed', 'resolved'
```

### Class: Trip
```python
@dataclass
class Trip:
    id: int
    driver_id: int
    direction: str
    time_slot: str
    status: str           # 'pending', 'in_progress', 'completed'
    started_at: str
    completed_at: str
```

---

# 6. queue_logic.py — Core Queue Business Logic

**Purpose:** This is the BRAIN of the application. It contains all the queueing algorithm logic: how students join, how positions are calculated, how attendance works, and how trips are started. The route files call these functions.

### Function: get_current_time_slot()
```python
def get_current_time_slot():
    now = datetime.now()
    hour = now.hour
    minute = now.minute

    if hour < Config.OPERATING_START_HOUR:
        return None  # Service not started yet
    if hour >= Config.OPERATING_END_HOUR:
        return None  # Service closed

    slot_minute = (minute // Config.TIME_SLOT_MINUTES) * Config.TIME_SLOT_MINUTES
    return f"{hour:02d}:{slot_minute:02d}"
```
- **What it does:** Determines which 20-minute time slot we're currently in.
- **How it works:**
  - If it's before 6 AM or after 11 PM → returns `None` (service closed).
  - Otherwise, "snaps" the current minute down to the nearest 20-minute boundary.
  - **Example:** If it's 9:35 AM → `35 // 20 = 1`, `1 * 20 = 20` → returns `"09:20"` (the 9:20-9:40 slot).
  - **Example:** If it's 9:05 AM → `5 // 20 = 0`, `0 * 20 = 0` → returns `"09:00"` (the 9:00-9:20 slot).
- **Used by:** `join_queue()`, `start_trip()`, `api_queue_status()`

### Function: get_next_time_slot(current_slot)
```python
def get_next_time_slot(current_slot):
    if current_slot is None:
        return None
    hour, minute = map(int, current_slot.split(':'))
    minute += Config.TIME_SLOT_MINUTES
    if minute >= 60:
        hour += 1
        minute -= 60
    if hour >= Config.OPERATING_END_HOUR:
        return None
    return f"{hour:02d}:{minute:02d}"
```
- **What it does:** Given a time slot like `"09:20"`, returns the next one: `"09:40"`.
- **Edge case:** If the current slot is `"09:40"`, adding 20 gives 60 minutes → rolls over to `"10:00"`. If the result is past 11 PM, returns None (no more slots today).

### Function: get_all_time_slots()
```python
def get_all_time_slots():
    slots = []
    hour = Config.OPERATING_START_HOUR  # 6
    minute = 0
    while hour < Config.OPERATING_END_HOUR:  # 23
        slots.append(f"{hour:02d}:{minute:02d}")
        minute += Config.TIME_SLOT_MINUTES  # +20
        if minute >= 60:
            hour += 1
            minute -= 60
    return slots
```
- **What it does:** Generates a list of ALL time slots for the day: `["06:00", "06:20", "06:40", "07:00", ...]`
- **Count:** 6:00 AM to 10:40 PM = 51 total slots per day.

### Function: join_queue(student_id, direction) ⭐ CRITICAL
```python
def join_queue(student_id, direction):
```
- **What it does:** Adds a student to the transport queue. This is called when a student taps "Join Queue" in the app.
- **Step-by-step:**
  1. **Check for duplicates:** Queries the database for any existing entry with this student_id where status='waiting'. If found, returns error "You are already in the queue."
  2. **Check service hours:** Calls `get_current_time_slot()`. If it returns None, the service is closed — returns error.
  3. **Insert into database:** Adds a new row to the `queue` table with the student_id, direction (college_to_main or main_to_college), and the current time slot.
  4. **Calculate position:** Calls `get_queue_position()` to find where this student is in line (e.g., position 3 out of 7).
  5. **Calculate wait time:** Calls `calculate_wait_time()` to estimate how long they'll wait.
  6. **Return result:** Sends back the queue_id, position, estimated time, and direction.
- **Returns:** A tuple of `(result_dict, error_string)`. On success: `({...dict...}, None)`. On failure: `(None, "error message")`.

### Function: get_queue_position(student_id, direction=None)
```python
def get_queue_position(student_id, direction=None):
```
- **What it does:** Finds a student's position in line (1st, 2nd, 3rd, etc.)
- **How it works:**
  1. If direction is provided, queries all waiting students for that direction, ordered by `joined_at ASC` (first come first). 
  2. If direction is not provided, first looks up the student's direction from their queue entry.
  3. Loops through the results, counting: when it finds the matching student_id, returns the count.
- **Why ordered by `joined_at ASC`:** This enforces FCFS (First Come First Served). The student who joined earliest has position 1.

### Function: calculate_wait_time(position)
```python
def calculate_wait_time(position):
    if position <= 0:
        return "N/A"
    trips_ahead = (position - 1) // Config.PASSENGERS_PER_TRIP
    wait_minutes = trips_ahead * Config.TIME_SLOT_MINUTES
    if wait_minutes == 0:
        return "Next trip"
    elif wait_minutes < 60:
        return f"{wait_minutes} minutes"
    else:
        hours = wait_minutes // 60
        mins = wait_minutes % 60
        return f"{hours}h {mins}m"
```
- **What it does:** Estimates how long a student will wait based on their position.
- **Logic:**
  - Each auto takes 4 passengers per trip.
  - `trips_ahead = (position - 1) // 4` — How many full trips must depart before yours.
  - Each trip takes one 20-minute time slot.
  - **Example:** Position 1-4 → 0 trips ahead → "Next trip"
  - **Example:** Position 5-8 → 1 trip ahead → "20 minutes"
  - **Example:** Position 9-12 → 2 trips ahead → "40 minutes"
  - **Example:** Position 13 → 3 trips ahead → "1 hour"

### Function: get_waiting_queue(direction)
```python
def get_waiting_queue(direction):
    return query_db(
        """SELECT q.*, u.name as student_name, u.email as student_email
           FROM queue q
           JOIN users u ON q.student_id = u.id
           WHERE q.direction = ? AND q.status = 'waiting'
           ORDER BY q.joined_at ASC""",
        (direction,)
    )
```
- **What it does:** Returns ALL students currently waiting in a specific direction, ordered by who joined first.
- **JOIN:** Links the `queue` table with the `users` table so we get the student's name and email alongside their queue data.
- **Used by:** The admin/driver queue view.

### Function: get_next_passengers(direction, count=None)
```python
def get_next_passengers(direction, count=None):
    if count is None:
        count = Config.PASSENGERS_PER_TRIP  # 4
    return query_db(
        """...WHERE q.direction = ? AND q.status = 'waiting'
           ORDER BY q.joined_at ASC
           LIMIT ?""",
        (direction, count)
    )
```
- **What it does:** Returns the first 4 (or specified count) students in the queue for a direction. These are the students the driver will check attendance for.
- **LIMIT clause:** Only returns the top `count` results, not the entire queue.

### Function: mark_attendance(queue_id, student_id, status) ⭐ CRITICAL
```python
def mark_attendance(queue_id, student_id, status):
```
- **What it does:** Called when the driver taps "Present ✓" or "Absent ✗" on a student.
- **Status = 'present':**
  - Updates the queue entry status from `'waiting'` to `'assigned'`.
  - `'assigned'` means "this student is physically present and will be on the next trip."
- **Status = 'absent' (1st time, absence_count becomes 1):**
  - Updates `absence_count` to 1.
  - Updates `joined_at` to `CURRENT_TIMESTAMP` — this effectively **moves the student to the end of the queue** because the queue is ordered by `joined_at ASC`. By changing their timestamp to "now", they become the newest entry.
  - Returns "Moved to end of queue (absence 1/2)"
- **Status = 'absent' (2nd time, absence_count becomes 2):**
  - Changes status to `'removed'` — the student is permanently out.
  - Returns "Removed from queue after 2 absences"
- **Why this design:** This is fair. If a student doesn't show up once, they get a second chance at the back of the line. If they miss again, they're out so they don't block others.

### Function: leave_queue(student_id) ⭐
```python
def leave_queue(student_id):
    entry = query_db(
        "SELECT id FROM queue WHERE student_id = ? AND status IN ('waiting', 'assigned')",
        (student_id,), one=True
    )
    if not entry:
        return False
    execute_db(
        "UPDATE queue SET status = 'removed' WHERE student_id = ? AND status IN ('waiting', 'assigned')",
        (student_id,)
    )
    return True
```
- **What it does:** Called when a student taps "Leave Queue" in the app.
- **How it works:**
  1. First checks if the student actually has an active queue entry (either 'waiting' or 'assigned'). If not, returns False.
  2. Sets their status to `'removed'`.
- **Why we check both 'waiting' and 'assigned':** A student might have already been marked "present" by the driver (status='assigned') but still wants to leave. We allow leaving from both states.
- **Why query first instead of just UPDATE:** The old code used `execute_db()` which returns `lastrowid` (always 0 for UPDATE statements). Checking `0 > 0` always returned False, making leave_queue ALWAYS fail. By querying first, we reliably check if the student exists.

### Function: start_trip(driver_id, direction) ⭐ CRITICAL
```python
def start_trip(driver_id, direction):
```
- **What it does:** Called when the driver taps "Start Trip." Creates a trip record and assigns passengers.
- **Step-by-step:**
  1. **Check service hours:** If `get_current_time_slot()` returns None, service is closed.
  2. **Get assigned passengers:** Finds students in this direction with status `'assigned'` (marked present by driver), ordered by who joined first, limited to 4.
  3. **No passengers check:** If nobody has been marked present, returns error.
  4. **Create trip record:** Inserts a new row in the `trips` table with the driver_id, direction, time_slot, and status 'in_progress'.
  5. **Link passengers:** For each assigned student:
     - Inserts a row in `trip_passengers` linking this trip to this student.
     - Updates the student's queue entry status from 'assigned' to 'completed'.
  6. **Return trip info:** Returns the trip_id, passenger count, and passenger names.

### Function: get_queue_stats()
```python
def get_queue_stats():
```
- **What it does:** Returns dashboard statistics for the admin panel.
- **Returns:**
  - `college_to_main_waiting` — Count of students waiting to go to Main Road
  - `main_to_college_waiting` — Count of students waiting to go to College
  - `total_waiting` — Sum of both
  - `trips_today` — How many trips have been completed today
  - `passengers_today` — How many students were transported today
  - `current_time_slot` — What time slot we're currently in
  - `operating_hours` — String showing service hours

---

# 7. notification.py — Firebase Push Notifications

**Purpose:** Sends push notifications to the Android app via Firebase Cloud Messaging (FCM). If Firebase isn't configured, notifications are logged to the console but not sent (graceful degradation).

### Module-Level Initialization (Lines 12-31)
```python
_firebase_available = False
_firebase_app = None

try:
    import firebase_admin
    from firebase_admin import credentials, messaging
    if os.path.exists(Config.FIREBASE_CREDENTIALS_PATH):
        cred = credentials.Certificate(Config.FIREBASE_CREDENTIALS_PATH)
        _firebase_app = firebase_admin.initialize_app(cred)
        _firebase_available = True
        print("[FCM] Firebase initialized successfully")
    ...
except ImportError:
    print("[FCM] firebase-admin not installed. Notifications disabled.")
```
- **What it does:** When the server starts, tries to initialize Firebase. If the credentials file exists and the library is installed, sets `_firebase_available = True`. If not, the server still works — it just can't send push notifications.
- **Why try/except:** Makes the notification system optional. The app works perfectly without Firebase.

### Function: send_notification(fcm_token, title, body, data=None)
```python
def send_notification(fcm_token, title, body, data=None):
```
- **What it does:** Sends a push notification to ONE specific device.
- **Parameters:**
  - `fcm_token` — Each phone has a unique Firebase token. This identifies which phone to send to.
  - `title` — The notification title (e.g., "Your Ride is Ready!")
  - `body` — The notification body text
  - `data` — Optional extra data the app can use programmatically

### Function: send_notification_to_topic(topic, title, body, data=None)
```python
def send_notification_to_topic(topic, title, body, data=None):
```
- **What it does:** Sends a notification to ALL devices subscribed to a topic. Topics are like channels.
- **Topics used:** `'drivers'` — all driver phones, `'students'` — all student phones.

### Function: notify_driver_queue_update(direction, waiting_count)
```python
def notify_driver_queue_update(direction, waiting_count):
```
- **What it does:** When 4+ students are waiting, notifies all drivers that passengers are ready.
- **Example notification:** Title: "Passengers Waiting", Body: "4 students waiting at College Gate"

### Function: notify_student_position_update(fcm_token, position, estimated_time)
- **What it does:** Notifies a specific student that their queue position has changed.

### Function: notify_student_trip_ready(fcm_token, direction)
- **What it does:** Notifies a student that their trip is about to leave.
- **Example notification:** Title: "Your Ride is Ready! 🚗", Body: "Please proceed to the pickup point. Heading to Main Road."

---

# 8. seed.py — Test Data Seeder

**Purpose:** Populates the database with test users so you can immediately log in and test without registering accounts manually. Run once with `python seed.py`.

### Function: hash_password(password)
```python
def hash_password(password):
    return bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')
```
- **What it does:** Takes a plain-text password like `"admin123"` and converts it to a secure bcrypt hash like `"$2b$12$LJ3m4..."`. This hash is stored in the database instead of the actual password.
- `bcrypt.gensalt()` — Generates a random salt (random bytes added to the password before hashing). Even if two users have the same password, their hashes will be different.
- `.encode('utf-8')` — bcrypt works with bytes, not strings, so we convert.
- `.decode('utf-8')` — Convert the result back to a string for storage.

### Function: seed()
```python
def seed():
```
- **What it does:**
  1. Calls `init_db()` to create tables.
  2. Checks if users already exist (to avoid duplicates).
  3. Creates test accounts:
     - **1 Admin:** `admin@iiitn.ac.in` / `admin123`
     - **5 Students:** `rahul@iiitn.ac.in`, `priya@iiitn.ac.in`, etc. / all `student123`
     - **2 Drivers:** `raju@automate.com`, `suresh@automate.com` / all `driver123`

---

# 9. routes/auth_routes.py — Authentication API Endpoints

**Purpose:** Handles user registration, login, profile retrieval, and FCM token updates.

### Blueprint Setup
```python
auth_bp = Blueprint('auth', __name__)
```
- Creates a Blueprint named 'auth'. All routes defined here will be grouped under this blueprint and registered with the main app.

### Route: POST /api/register
```python
@auth_bp.route('/api/register', methods=['POST'])
def register():
```
- **What it does:** Creates a new user account.
- **Flow:**
  1. **Parse JSON body:** Gets name, email, password, role from the request.
  2. **Validate required fields:** All 4 fields must be present.
  3. **Validate role:** Must be 'student', 'driver', or 'admin'.
  4. **Check duplicate email:** Queries database to see if email already exists. Returns 409 (Conflict) if it does.
  5. **Hash password:** Uses bcrypt to securely hash the password. The plain-text password is NEVER stored.
  6. **Driver validation:** If role is 'driver', phone number is required.
  7. **Insert into database:** Creates the user row.
  8. **Generate JWT token:** Immediately creates a login token so the user doesn't need to log in separately after registering.
  9. **Return response:** Sends back the user_id, token, role, and name.
- **HTTP Status Codes:**
  - `201 Created` — Success
  - `400 Bad Request` — Missing fields or invalid role
  - `409 Conflict` — Email already taken

### Route: POST /api/login ⭐ CRITICAL
```python
@auth_bp.route('/api/login', methods=['POST'])
def login():
```
- **What it does:** Authenticates a user and returns a JWT token.
- **Flow:**
  1. **Parse JSON:** Gets email, password, and optional role from request.
  2. **Find user:** Queries database by email (and optionally role). If the role is specified, it only matches users with that role — so a student can't log in as a driver.
  3. **Verify password:** Uses `bcrypt.checkpw()` to compare the submitted password against the stored hash. bcrypt internally handles the salt extraction and comparison.
  4. **Check active status:** If the account has been deactivated (`is_active = 0`), returns 403 (Forbidden).
  5. **Update FCM token:** If the app sent a Firebase token, saves it so the server can send push notifications to this device later.
  6. **Generate JWT:** Creates a token containing:
     - `identity` — The user's ID (string)
     - `role` — 'student', 'driver', or 'admin'
     - `name` — The user's display name
     - This token is signed with `JWT_SECRET_KEY` and expires in 24 hours.
  7. **Return response:** Token, user_id, role, name, email.
- **HTTP Status Codes:**
  - `200 OK` — Successful login
  - `400 Bad Request` — Missing email or password
  - `401 Unauthorized` — Wrong email or password
  - `403 Forbidden` — Account deactivated

### Route: GET /api/profile
```python
@auth_bp.route('/api/profile', methods=['GET'])
@jwt_required()
def get_profile():
```
- **`@jwt_required()`** — This decorator means the request MUST include a valid JWT token in the `Authorization: Bearer <token>` header. If not, one of the JWT error handlers from app.py kicks in.
- **What it does:** Returns the logged-in user's profile info (without the password hash).

### Route: POST /api/update_fcm_token
```python
@auth_bp.route('/api/update_fcm_token', methods=['POST'])
@jwt_required()
def update_fcm_token():
```
- **What it does:** Updates the user's Firebase Cloud Messaging token. Called when the app starts or when the device gets a new FCM token.

---

# 10. routes/queue_routes.py — Queue Management API Endpoints

**Purpose:** The most complex route file. Handles ALL queue operations: joining, leaving, checking status, driver-side queue view, attendance marking, trip starting, statistics, and the schedule.

### Route: POST /api/join_queue ⭐
```python
@queue_bp.route('/api/join_queue', methods=['POST'])
@jwt_required()
def api_join_queue():
```
- **Access:** Students only (checked via JWT role claim).
- **What it does:** Adds the student to the queue.
- **Flow:**
  1. Extract user_id from JWT token.
  2. Verify role is 'student' (drivers/admins can't join).
  3. Get direction from request body ('college_to_main' or 'main_to_college').
  4. Call `join_queue()` from queue_logic.py.
  5. If 4+ students are now waiting, send a push notification to all drivers.
  6. Return position and estimated wait time.

### Route: GET /api/queue_status
```python
@queue_bp.route('/api/queue_status', methods=['GET'])
@jwt_required()
def api_queue_status():
```
- **What it does:** Returns the current student's queue position, wait time, and direction. The Android app calls this repeatedly to show live status.
- **If student is NOT in queue:** Returns `{'in_queue': false}`.
- **If student IS in queue:** Returns position, estimated_time, direction, time_slot.

### Route: DELETE /api/leave_queue
```python
@queue_bp.route('/api/leave_queue', methods=['DELETE'])
@jwt_required()
def api_leave_queue():
```
- **HTTP Method:** DELETE (because we're removing the student from the queue).
- **What it does:** Calls `leave_queue()` to set the student's queue status to 'removed'.

### Route: GET /api/driver_queue
```python
@queue_bp.route('/api/driver_queue', methods=['GET'])
@jwt_required()
def api_driver_queue():
```
- **Access:** Drivers only.
- **What it does:** Returns the next 4 students in line for a specific direction, plus total waiting count. The driver's app shows this as a list of students with "Present ✓" and "Absent ✗" buttons.
- **Query parameter:** `?direction=college_to_main` or `?direction=main_to_college`

### Route: POST /api/attendance ⭐
```python
@queue_bp.route('/api/attendance', methods=['POST'])
@jwt_required()
def api_mark_attendance():
```
- **Access:** Drivers only.
- **What it does:** Driver marks a student as present or absent.
- **Request body:** `{"student_id": 5, "status": "present"}` or `{"student_id": 5, "status": "absent"}`
- **Flow:**
  1. Verify the caller is a driver.
  2. Find the student's active queue entry.
  3. Call `mark_attendance()` from queue_logic.py.
  4. Return the result (present, moved_to_end, or removed).

### Route: POST /api/start_trip ⭐ CRITICAL
```python
@queue_bp.route('/api/start_trip', methods=['POST'])
@jwt_required()
def api_start_trip():
```
- **Access:** Drivers only.
- **What it does:** Creates a trip and departs with all assigned passengers.
- **15-Minute Cooldown Logic:**
  ```python
  last_trip = query_db("SELECT started_at FROM trips WHERE driver_id = ? ORDER BY started_at DESC LIMIT 1", ...)
  if last_trip:
      last_time = datetime.strptime(last_trip['started_at'], '%Y-%m-%d %H:%M:%S')
      cooldown_end = last_time + timedelta(minutes=15)
      if now < cooldown_end:
          remaining = int((cooldown_end - now).total_seconds())
          return jsonify({'error': f'Cooldown active. Wait {mins}m {secs}s...'}), 429
  ```
  - Looks up the driver's most recent trip.
  - If it was less than 15 minutes ago, blocks starting a new trip and tells the driver how long to wait.
  - HTTP 429 = "Too Many Requests" — rate limiting.
  - **Why:** Prevents a driver from spamming trips. A round trip takes time, so 15 minutes is realistic.
- After creating the trip, sends push notifications to all assigned passengers.

### Route: GET /api/queue_stats
```python
@queue_bp.route('/api/queue_stats', methods=['GET'])
@jwt_required()
def api_queue_stats():
```
- **Access:** Admin and drivers only.
- **What it does:** Returns dashboard statistics (total waiting, trips today, passengers today).

### Route: GET /api/full_queue
```python
@queue_bp.route('/api/full_queue', methods=['GET'])
@jwt_required()
def api_full_queue():
```
- **Access:** Admin and drivers only.
- **What it does:** Returns the ENTIRE queue for a direction (not just top 4). Used for admin monitoring.

### Route: GET /api/schedule ⭐
```python
@queue_bp.route('/api/schedule', methods=['GET'])
@jwt_required()
def api_schedule():
```
- **What it does:** Returns today's complete schedule with slot statuses (passed/active/upcoming).
- **Algorithm:**
  1. Get current time, convert to total minutes: `current_total_minutes = hour * 60 + minute`
  2. Loop through every 20-minute slot from 6:00 AM to 11:00 PM.
  3. For each slot, convert its start and end times to total minutes.
  4. Compare:
     - `current_time >= slot_end` → **"passed"** (slot is over, RED)
     - `current_time >= slot_start AND current_time < slot_end` → **"active"** (happening now, GREEN)
     - Otherwise → **"upcoming"** (hasn't started yet, GRAY)
  5. Format times in 12-hour AM/PM (e.g., "6:00 A.M - 6:20 A.M").
- **Example at 6:25 AM:**
  - Slot 1 (6:00-6:20): 385 >= 380 → passed (RED)
  - Slot 2 (6:20-6:40): 385 >= 380 AND 385 < 400 → active (GREEN)
  - Slot 3+ (6:40+): upcoming (GRAY)

### Helper: format_time_12h(hour, minute)
```python
def format_time_12h(hour, minute):
    period = "A.M" if hour < 12 else "P.M"
    display_hour = hour % 12
    if display_hour == 0:
        display_hour = 12
    return f"{display_hour}:{minute:02d} {period}"
```
- Converts 24-hour time to 12-hour format. E.g., `(14, 20)` → `"2:20 P.M"`, `(0, 0)` → `"12:00 A.M"`.

---

# 11. routes/complaint_routes.py — Complaint API Endpoints

### Route: POST /api/complaint
```python
@complaint_bp.route('/api/complaint', methods=['POST'])
@jwt_required()
def submit_complaint():
```
- **Access:** Students only.
- **What it does:** Submits a complaint about a driver or service issue.
- **Validation:**
  - `complaint_type` must be one of: `driver_absent`, `auto_not_arrived`, `misbehavior`, `other`.
  - `message` must be at least 10 characters long.
- **Inserts into:** `complaints` table with status defaulting to 'pending'.

### Route: GET /api/complaints
```python
@complaint_bp.route('/api/complaints', methods=['GET'])
@jwt_required()
def list_complaints():
```
- **Access:** Role-based:
  - **Students** see only THEIR OWN complaints.
  - **Admin/Drivers** see ALL complaints (optionally filtered by status).
- **SQL JOIN:** Joins complaints with users table to get student_name and driver_name.
- **Optional filter:** `?status=pending` to see only unresolved complaints.

### Route: PUT /api/complaint/<id>/resolve
```python
@complaint_bp.route('/api/complaint/<int:complaint_id>/resolve', methods=['PUT'])
@jwt_required()
def resolve_complaint(complaint_id):
```
- **Access:** Admin only.
- **What it does:** Changes a complaint's status to 'reviewed' or 'resolved' and records the timestamp.
- **URL parameter:** The complaint ID is embedded in the URL (e.g., `/api/complaint/5/resolve`).

---

# 12. database/schema.sql — Database Schema

This SQL file defines 5 tables:

### Table: users
| Column        | Type    | Description                           |
|--------------|---------|---------------------------------------|
| id           | INTEGER | Auto-generated unique ID              |
| name         | TEXT    | Full name                             |
| email        | TEXT    | Unique email address (login)          |
| password_hash| TEXT    | bcrypt hashed password                |
| role         | TEXT    | 'student', 'driver', or 'admin'       |
| phone        | TEXT    | Driver's phone number (nullable)      |
| auto_number  | TEXT    | Driver's auto plate number (nullable) |
| fcm_token    | TEXT    | Firebase push notification token      |
| is_active    | INTEGER | 1 = active, 0 = deactivated           |
| created_at   | TIMESTAMP| Auto-generated creation time          |

### Table: queue
| Column        | Type    | Description                           |
|--------------|---------|---------------------------------------|
| id           | INTEGER | Auto-generated unique ID              |
| student_id   | INTEGER | Foreign key → users.id                |
| direction    | TEXT    | 'college_to_main' or 'main_to_college'|
| time_slot    | TEXT    | e.g., "09:00"                         |
| absence_count| INTEGER | How many times marked absent (0/1/2)  |
| status       | TEXT    | 'waiting'/'assigned'/'completed'/'removed' |
| joined_at    | TIMESTAMP| When the student joined the queue     |

### Table: trips
| Column        | Type    | Description                           |
|--------------|---------|---------------------------------------|
| id           | INTEGER | Auto-generated unique ID              |
| driver_id    | INTEGER | Foreign key → users.id                |
| direction    | TEXT    | 'college_to_main' or 'main_to_college'|
| time_slot    | TEXT    | e.g., "09:00"                         |
| status       | TEXT    | 'pending'/'in_progress'/'completed'   |
| started_at   | TIMESTAMP| When the trip started                 |
| completed_at | TIMESTAMP| When the trip ended (if applicable)   |

### Table: trip_passengers
| Column        | Type    | Description                           |
|--------------|---------|---------------------------------------|
| id           | INTEGER | Auto-generated unique ID              |
| trip_id      | INTEGER | Foreign key → trips.id                |
| student_id   | INTEGER | Foreign key → users.id                |
| queue_id     | INTEGER | Foreign key → queue.id                |
| attendance   | TEXT    | 'pending'/'present'/'absent'          |

### Table: complaints
| Column         | Type    | Description                          |
|---------------|---------|--------------------------------------|
| id            | INTEGER | Auto-generated unique ID             |
| student_id    | INTEGER | Who filed it (FK → users.id)        |
| driver_id     | INTEGER | Against whom (nullable, FK → users) |
| complaint_type| TEXT    | Category of complaint                |
| message       | TEXT    | Detailed description                 |
| status        | TEXT    | 'pending'/'reviewed'/'resolved'      |
| resolved_at   | TIMESTAMP| When an admin resolved it           |

### Performance Indexes
```sql
CREATE INDEX IF NOT EXISTS idx_queue_student ON queue(student_id);
CREATE INDEX IF NOT EXISTS idx_queue_status ON queue(status);
CREATE INDEX IF NOT EXISTS idx_queue_direction ON queue(direction);
CREATE INDEX IF NOT EXISTS idx_queue_joined ON queue(joined_at);
```
- **What indexes do:** Make database lookups FASTER. Without an index on `status`, SQLite would scan every single row in the queue table to find 'waiting' entries. With an index, it can jump directly to matching rows. Think of it like a book's index — you don't read the whole book to find a topic.

---

# 13. HOW EVERYTHING CONNECTS — Request Flow

### Example: Student Joins Queue

```
1. Student opens app → taps "Join Queue" → selects "College to Main Road"

2. Android app sends HTTP request:
   POST http://192.168.137.1:5000/api/join_queue
   Headers: Authorization: Bearer eyJhbGciOiJ...  (JWT token from login)
   Body: {"direction": "college_to_main"}

3. Flask receives the request → matches to api_join_queue() in queue_routes.py

4. @jwt_required() decorator intercepts:
   - Extracts the JWT token from the Authorization header
   - Verifies the signature using JWT_SECRET_KEY
   - Checks it hasn't expired (24-hour limit)
   - Makes user_id and claims available via get_jwt_identity() and get_jwt()

5. api_join_queue() checks role == 'student' from JWT claims

6. Calls join_queue(student_id=9, direction='college_to_main') in queue_logic.py

7. join_queue():
   a. Calls query_db() → checks if student 9 is already in queue
   b. Calls get_current_time_slot() → e.g., returns "09:00"
   c. Calls execute_db() → INSERT INTO queue VALUES (9, 'college_to_main', '09:00')
   d. Calls get_queue_position() → student is 3rd in line
   e. Calls calculate_wait_time(3) → 0 trips ahead → "Next trip"
   f. Returns {'queue_id': 58, 'position': 3, 'estimated_time': 'Next trip', ...}

8. Back in api_join_queue():
   - Checks if 4+ students are waiting → if yes, calls notify_driver_queue_update()
   - Returns JSON response with position info

9. Android app receives the response → updates UI to show position #3

10. Total time: ~50-100 milliseconds
```

### Queue Status Lifecycle:
```
Student joins  →  status = 'waiting'       (in the queue, waiting for turn)
                     │
                     ▼
Driver marks   →  status = 'assigned'      (present, ready for trip)
  OR
Driver marks   →  absence_count += 1       (absent, moved to end)
absent 2nd     →  status = 'removed'       (kicked out)
  OR
Student leaves →  status = 'removed'       (voluntarily left)
                     │
                     ▼
Trip starts    →  status = 'completed'     (departed on a trip)
```

---

**Test Credentials:**
- Admin:   `admin@iiitn.ac.in`   / `admin123`
- Student: `rahul@iiitn.ac.in`   / `student123`
- Driver:  `raju@automate.com`   / `driver123`
