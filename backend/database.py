"""
AutoMate Database Connection Manager
"""
import sqlite3
import os
from config import Config


def get_db():
    """Get a database connection with row factory enabled."""
    db = sqlite3.connect(Config.DATABASE_PATH, timeout=5)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA foreign_keys = ON")
    return db


def close_db(db):
    """Close a database connection."""
    if db is not None:
        db.close()


def init_db():
    """Initialize the database from schema.sql."""
    # Ensure the database directory exists
    db_dir = os.path.dirname(Config.DATABASE_PATH)
    if db_dir and not os.path.exists(db_dir):
        os.makedirs(db_dir)

    db = get_db()
    try:
        with open(Config.SCHEMA_PATH, 'r') as f:
            schema_sql = f.read()
        db.executescript(schema_sql)
        db.commit()
        # WAL mode allows concurrent reads+writes (set once, persists on the file)
        try:
            db.execute("PRAGMA journal_mode = WAL")
        except sqlite3.OperationalError:
            pass  # WAL already set or db briefly busy — safe to skip
        print(f"[DB] Database initialized at: {Config.DATABASE_PATH}")
    except Exception as e:
        print(f"[DB] Error initializing database: {e}")
        raise
    finally:
        close_db(db)


def query_db(query, args=(), one=False):
    """Execute a query and return results as list of dicts."""
    db = get_db()
    try:
        cursor = db.execute(query, args)
        results = cursor.fetchall()
        if one:
            return dict(results[0]) if results else None
        return [dict(row) for row in results]
    finally:
        close_db(db)


def execute_db(query, args=()):
    """Execute an INSERT/UPDATE/DELETE and return lastrowid."""
    db = get_db()
    try:
        cursor = db.execute(query, args)
        db.commit()
        return cursor.lastrowid
    finally:
        close_db(db)


if __name__ == '__main__':
    init_db()
    print("[DB] Database setup complete.")
