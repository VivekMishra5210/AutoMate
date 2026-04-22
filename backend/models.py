"""
AutoMate Data Models
"""
from dataclasses import dataclass, asdict
from datetime import datetime
from typing import Optional


@dataclass
class User:
    """Represents a user (student, driver, or admin)."""
    id: int
    name: str
    email: str
    role: str
    phone: Optional[str] = None
    auto_number: Optional[str] = None
    fcm_token: Optional[str] = None
    is_active: bool = True
    created_at: Optional[str] = None

    def to_dict(self):
        """Convert to dictionary, excluding sensitive fields."""
        data = asdict(self)
        # Never expose password hash
        data.pop('password_hash', None)
        return data

    @staticmethod
    def from_row(row):
        """Create User from database row dict."""
        if row is None:
            return None
        return User(
            id=row['id'],
            name=row['name'],
            email=row['email'],
            role=row['role'],
            phone=row.get('phone'),
            auto_number=row.get('auto_number'),
            fcm_token=row.get('fcm_token'),
            is_active=bool(row.get('is_active', 1)),
            created_at=row.get('created_at')
        )


@dataclass
class QueueEntry:
    """Represents a student in the transport queue."""
    id: int
    student_id: int
    direction: str
    time_slot: Optional[str] = None
    absence_count: int = 0
    status: str = 'waiting'
    joined_at: Optional[str] = None
    student_name: Optional[str] = None
    student_email: Optional[str] = None

    def to_dict(self):
        return asdict(self)

    @staticmethod
    def from_row(row):
        if row is None:
            return None
        return QueueEntry(
            id=row['id'],
            student_id=row['student_id'],
            direction=row['direction'],
            time_slot=row.get('time_slot'),
            absence_count=row.get('absence_count', 0),
            status=row.get('status', 'waiting'),
            joined_at=row.get('joined_at'),
            student_name=row.get('student_name'),
            student_email=row.get('student_email')
        )


@dataclass
class Complaint:
    """Represents a complaint filed by a student."""
    id: int
    student_id: int
    driver_id: Optional[int]
    complaint_type: str
    message: str
    status: str = 'pending'
    created_at: Optional[str] = None
    resolved_at: Optional[str] = None
    student_name: Optional[str] = None
    driver_name: Optional[str] = None

    def to_dict(self):
        return asdict(self)

    @staticmethod
    def from_row(row):
        if row is None:
            return None
        return Complaint(
            id=row['id'],
            student_id=row['student_id'],
            driver_id=row.get('driver_id'),
            complaint_type=row['complaint_type'],
            message=row['message'],
            status=row.get('status', 'pending'),
            created_at=row.get('created_at'),
            resolved_at=row.get('resolved_at'),
            student_name=row.get('student_name'),
            driver_name=row.get('driver_name')
        )


@dataclass
class Trip:
    """Represents a transport trip."""
    id: int
    driver_id: int
    direction: str
    time_slot: str
    status: str = 'pending'
    started_at: Optional[str] = None
    completed_at: Optional[str] = None
    created_at: Optional[str] = None
    driver_name: Optional[str] = None
    passenger_count: int = 0

    def to_dict(self):
        return asdict(self)

    @staticmethod
    def from_row(row):
        if row is None:
            return None
        return Trip(
            id=row['id'],
            driver_id=row['driver_id'],
            direction=row['direction'],
            time_slot=row['time_slot'],
            status=row.get('status', 'pending'),    
            started_at=row.get('started_at'),
            completed_at=row.get('completed_at'),
            created_at=row.get('created_at'),
            driver_name=row.get('driver_name'),
            passenger_count=row.get('passenger_count', 0)
        )
