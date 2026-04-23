"""
AutoMate Backend Configuration
"""
import os

BASE_DIR = os.path.abspath(os.path.dirname(__file__))


class Config:
    """Application configuration."""

    # Flask
    SECRET_KEY = os.environ.get('SECRET_KEY', 'automate-dev-secret-key-change-in-production')
    DEBUG = os.environ.get('FLASK_DEBUG', 'False').lower() == 'true'

    # Database
    DATABASE_PATH = os.environ.get('DATABASE_PATH', os.path.join(BASE_DIR, '..', 'database', 'automate.db'))
    SCHEMA_PATH = os.environ.get('SCHEMA_PATH', os.path.join(BASE_DIR, '..', 'database', 'schema.sql'))

    # JWT
    JWT_SECRET_KEY = os.environ.get('JWT_SECRET_KEY', 'automate-jwt-secret-change-in-production')
    JWT_ACCESS_TOKEN_EXPIRES = 86400  

    # Firebase
    FIREBASE_CREDENTIALS_PATH = os.environ.get(
        'FIREBASE_CREDENTIALS_PATH',
        os.path.join(BASE_DIR, 'firebase-credentials.json')
    )

    # Queue Settings
    PASSENGERS_PER_TRIP = 4
    TIME_SLOT_MINUTES = 20
    OPERATING_START_HOUR = 6    # 6 AM
    OPERATING_END_HOUR = 23     # 11 PM
    MAX_ABSENCES = 2            # removed after 2 absences
