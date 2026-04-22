-- ============================================
-- AutoMate Database Schema
-- IIIT Nagpur Campus Transport Coordination
-- ============================================

-- Enable foreign keys (SQLite requires this per-connection)
PRAGMA foreign_keys = ON;

-- ============================================
-- USERS TABLE (unified for students, drivers, admin)
-- ============================================
CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name TEXT NOT NULL,
    email TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role TEXT NOT NULL CHECK (role IN ('student', 'driver', 'admin')),
    phone TEXT,
    auto_number TEXT,           -- only for drivers
    fcm_token TEXT,             -- Firebase Cloud Messaging token
    is_active INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- QUEUE TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS queue (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id INTEGER NOT NULL,
    direction TEXT NOT NULL CHECK (direction IN ('college_to_main', 'main_to_college')),
    time_slot TEXT,              -- e.g., "09:00", "09:20", "09:40"
    absence_count INTEGER DEFAULT 0,
    status TEXT DEFAULT 'waiting' CHECK (status IN ('waiting', 'assigned', 'completed', 'removed')),
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES users(id)
);

-- ============================================
-- TRIPS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS trips (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    driver_id INTEGER NOT NULL,
    direction TEXT NOT NULL CHECK (direction IN ('college_to_main', 'main_to_college')),
    time_slot TEXT NOT NULL,
    status TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'in_progress', 'completed')),
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (driver_id) REFERENCES users(id)
);

-- ============================================
-- TRIP PASSENGERS (links trips to queue entries)
-- ============================================
CREATE TABLE IF NOT EXISTS trip_passengers (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    trip_id INTEGER NOT NULL,
    student_id INTEGER NOT NULL,
    queue_id INTEGER NOT NULL,
    attendance TEXT DEFAULT 'pending' CHECK (attendance IN ('pending', 'present', 'absent')),
    marked_at TIMESTAMP,
    FOREIGN KEY (trip_id) REFERENCES trips(id),
    FOREIGN KEY (student_id) REFERENCES users(id),
    FOREIGN KEY (queue_id) REFERENCES queue(id)
);

-- ============================================
-- COMPLAINTS TABLE
-- ============================================
CREATE TABLE IF NOT EXISTS complaints (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id INTEGER NOT NULL,
    driver_id INTEGER,
    complaint_type TEXT NOT NULL CHECK (complaint_type IN ('driver_absent', 'auto_not_arrived', 'misbehavior', 'other')),
    message TEXT NOT NULL,
    status TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'reviewed', 'resolved')),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP,
    FOREIGN KEY (student_id) REFERENCES users(id),
    FOREIGN KEY (driver_id) REFERENCES users(id)
);

-- ============================================
-- INDEXES for performance
-- ============================================
CREATE INDEX IF NOT EXISTS idx_queue_student ON queue(student_id);
CREATE INDEX IF NOT EXISTS idx_queue_status ON queue(status);
CREATE INDEX IF NOT EXISTS idx_queue_direction ON queue(direction);
CREATE INDEX IF NOT EXISTS idx_queue_joined ON queue(joined_at);
CREATE INDEX IF NOT EXISTS idx_trips_driver ON trips(driver_id);
CREATE INDEX IF NOT EXISTS idx_trips_status ON trips(status);
CREATE INDEX IF NOT EXISTS idx_complaints_student ON complaints(student_id);
CREATE INDEX IF NOT EXISTS idx_complaints_status ON complaints(status);

-- ============================================
-- SEED DATA
-- ============================================
-- Run backend/seed.py to create test users with proper bcrypt hashes.
-- Test accounts created by seed.py:
--   Admin:   admin@iiitn.ac.in / admin123
--   Student: rahul@iiitn.ac.in / student123 (+ 4 more)
--   Driver:  raju@automate.com / driver123 (+ 1 more)
