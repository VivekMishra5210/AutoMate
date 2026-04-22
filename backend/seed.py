"""
Seed the database with test data using proper bcrypt hashes.
Run this once after init_db to create test users.
"""
import bcrypt
import sys
import os

sys.path.insert(0, os.path.dirname(__file__))

from database import init_db, execute_db, query_db


def hash_password(password):
    """Hash a password with bcrypt."""
    return bcrypt.hashpw(password.encode('utf-8'), bcrypt.gensalt()).decode('utf-8')


def seed():
    """Seed the database with test users."""
    # First, initialize the schema (creates tables)
    init_db()

    # Check if already seeded
    existing = query_db("SELECT COUNT(*) as count FROM users", one=True)
    if existing and existing['count'] > 0:
        print("[SEED] Database already has users. Skipping seed.")
        return

    # Admin (password: admin123)
    execute_db(
        "INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, ?)",
        ('Admin', 'admin@iiitn.ac.in', hash_password('admin123'), 'admin')
    )

    # Students (password: student123)
    students = [
        ('Rahul Sharma', 'rahul@iiitn.ac.in'),
        ('Priya Patel', 'priya@iiitn.ac.in'),
        ('Amit Kumar', 'amit@iiitn.ac.in'),
        ('Sneha Reddy', 'sneha@iiitn.ac.in'),
        ('Vikram Singh', 'vikram@iiitn.ac.in'),
    ]
    for name, email in students:
        execute_db(
            "INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, ?, ?)",
            (name, email, hash_password('student123'), 'student')
        )

    # Drivers (password: driver123)
    drivers = [
        ('Raju Driver', 'raju@automate.com', '9876543210', 'MH-31-AB-1234'),
        ('Suresh Driver', 'suresh@automate.com', '9876543211', 'MH-31-CD-5678'),
    ]
    for name, email, phone, auto_num in drivers:
        execute_db(
            "INSERT INTO users (name, email, password_hash, role, phone, auto_number) VALUES (?, ?, ?, ?, ?, ?)",
            (name, email, hash_password('driver123'), 'driver', phone, auto_num)
        )

    print("[SEED] Database seeded successfully!")
    print("  Test accounts:")
    print("    Admin:   admin@iiitn.ac.in / admin123")
    print("    Student: rahul@iiitn.ac.in / student123")
    print("    Driver:  raju@automate.com / driver123")


if __name__ == '__main__':
    seed()
