"""
AutoMate Queue Logic — FCFS with 4 passengers per trip
"""
from datetime import datetime, timedelta
from config import Config
from database import query_db, execute_db


def get_current_time_slot():
    """Get the current or next available 20-minute time slot."""
    now = datetime.now()
    hour = now.hour
    minute = now.minute

    # Check if within operating hours (9 AM - 6 PM)
    if hour < Config.OPERATING_START_HOUR:
        return None  # Service not started yet
    if hour >= Config.OPERATING_END_HOUR:
        return None  # Service closed

    # Snap to current 20-minute slot
    slot_minute = (minute // Config.TIME_SLOT_MINUTES) * Config.TIME_SLOT_MINUTES
    return f"{hour:02d}:{slot_minute:02d}"


def get_next_time_slot(current_slot):
    """Get the next time slot after the given one."""
    if current_slot is None:
        return None

    hour, minute = map(int, current_slot.split(':'))
    minute += Config.TIME_SLOT_MINUTES

    if minute >= 60:
        hour += 1
        minute -= 60

    if hour >= Config.OPERATING_END_HOUR:
        return None  # No more slots today

    return f"{hour:02d}:{minute:02d}"


def get_all_time_slots():
    """Generate all time slots for the day."""
    slots = []
    hour = Config.OPERATING_START_HOUR
    minute = 0

    while hour < Config.OPERATING_END_HOUR:
        slots.append(f"{hour:02d}:{minute:02d}")
        minute += Config.TIME_SLOT_MINUTES
        if minute >= 60:
            hour += 1
            minute -= 60

    return slots


def join_queue(student_id, direction):
    """
    Add a student to the transport queue.
    Returns queue position and estimated wait time.
    """
    # Check if student is already in queue
    existing = query_db(
        "SELECT id FROM queue WHERE student_id = ? AND status = 'waiting'",
        (student_id,),
        one=True
    )
    if existing:
        return None, "You are already in the queue"

    # Get current time slot
    time_slot = get_current_time_slot()
    if time_slot is None:
        return None, "Service is closed. Operating hours: 6 AM - 11 PM"

    # Add to queue
    queue_id = execute_db(
        "INSERT INTO queue (student_id, direction, time_slot) VALUES (?, ?, ?)",
        (student_id, direction, time_slot)
    )

    # Calculate position
    position = get_queue_position(student_id, direction)
    estimated_time = calculate_wait_time(position)

    return {
        'queue_id': queue_id,
        'position': position,
        'estimated_time': estimated_time,
        'time_slot': time_slot,
        'direction': direction
    }, None


def get_queue_position(student_id, direction=None):
    """Get a student's position in the queue."""
    if direction:
        entries = query_db(
            "SELECT student_id FROM queue WHERE direction = ? AND status = 'waiting' ORDER BY joined_at ASC",
            (direction,)
        )
    else:
        # Get the student's direction first
        entry = query_db(
            "SELECT direction FROM queue WHERE student_id = ? AND status = 'waiting'",
            (student_id,),
            one=True
        )
        if not entry:
            return 0
        entries = query_db(
            "SELECT student_id FROM queue WHERE direction = ? AND status = 'waiting' ORDER BY joined_at ASC",
            (entry['direction'],)
        )

    for i, entry in enumerate(entries, 1):
        if entry['student_id'] == student_id:
            return i
    return 0


def calculate_wait_time(position):
    """Calculate estimated wait time based on queue position."""
    if position <= 0:
        return "N/A"

    # Every 4 passengers = 1 trip = 1 time slot (20 minutes)
    trips_ahead = (position - 1) // Config.PASSENGERS_PER_TRIP
    wait_minutes = trips_ahead * Config.TIME_SLOT_MINUTES

    if wait_minutes == 0:
        return "Next trip"
    elif wait_minutes < 60:
        return f"{wait_minutes} minutes"
    else:
        hours = wait_minutes // 60
        mins = wait_minutes % 60
        if mins == 0:
            return f"{hours} hour{'s' if hours > 1 else ''}"
        return f"{hours}h {mins}m"


def get_waiting_queue(direction):
    """Get all waiting students for a direction, ordered by FCFS."""
    return query_db(
        """SELECT q.*, u.name as student_name, u.email as student_email
           FROM queue q
           JOIN users u ON q.student_id = u.id
           WHERE q.direction = ? AND q.status = 'waiting'
           ORDER BY q.joined_at ASC""",
        (direction,)
    )


def get_next_passengers(direction, count=None):
    """Get the next batch of passengers (first 4 by default) for a trip."""
    if count is None:
        count = Config.PASSENGERS_PER_TRIP

    return query_db(
        """SELECT q.*, u.name as student_name, u.email as student_email
           FROM queue q
           JOIN users u ON q.student_id = u.id
           WHERE q.direction = ? AND q.status = 'waiting'
           ORDER BY q.joined_at ASC
           LIMIT ?""",
        (direction, count)
    )


def mark_attendance(queue_id, student_id, status):
    """
    Mark a student as present or absent.
    - Present: mark as 'assigned' (ready for trip)
    - Absent (1st time): move to end of queue
    - Absent (2nd time): remove from queue
    """
    entry = query_db(
        "SELECT * FROM queue WHERE id = ? AND student_id = ?",
        (queue_id, student_id),
        one=True
    )
    if not entry:
        return None, "Queue entry not found"

    if status == 'present':
        execute_db(
            "UPDATE queue SET status = 'assigned' WHERE id = ?",
            (queue_id,)
        )
        return {'status': 'present', 'message': 'Marked as present'}, None

    elif status == 'absent':
        new_absence_count = entry['absence_count'] + 1

        if new_absence_count >= Config.MAX_ABSENCES:
            # Remove from queue after 2 absences
            execute_db(
                "UPDATE queue SET status = 'removed', absence_count = ? WHERE id = ?",
                (new_absence_count, queue_id)
            )
            return {
                'status': 'removed',
                'message': f'Removed from queue after {new_absence_count} absences'
            }, None
        else:
            # Move to end of queue (update joined_at to now)
            execute_db(
                """UPDATE queue SET absence_count = ?, joined_at = CURRENT_TIMESTAMP
                   WHERE id = ?""",
                (new_absence_count, queue_id)
            )
            return {
                'status': 'moved_to_end',
                'message': f'Moved to end of queue (absence {new_absence_count}/{Config.MAX_ABSENCES})'
            }, None

    return None, "Invalid status. Use 'present' or 'absent'"


def leave_queue(student_id):
    """Student voluntarily leaves the queue."""
    # Check if the student is in the queue (waiting or assigned)
    entry = query_db(
        "SELECT id FROM queue WHERE student_id = ? AND status IN ('waiting', 'assigned')",
        (student_id,),
        one=True
    )
    if not entry:
        return False

    execute_db(
        "UPDATE queue SET status = 'removed' WHERE student_id = ? AND status IN ('waiting', 'assigned')",
        (student_id,)
    )
    return True


def start_trip(driver_id, direction):
    """
    Start a trip: take the first 4 assigned/waiting passengers.
    Returns trip info.
    """
    time_slot = get_current_time_slot()
    if time_slot is None:
        return None, "Service is closed"

    # Only take passengers that the driver explicitly marked present (assigned)
    passengers = query_db(
        """SELECT q.*, u.name as student_name
           FROM queue q
           JOIN users u ON q.student_id = u.id
           WHERE q.direction = ? AND q.status = 'assigned'
           ORDER BY q.joined_at ASC
           LIMIT ?""",
        (direction, Config.PASSENGERS_PER_TRIP)
    )

    if not passengers:
        return None, "No passengers in queue"

    # Create trip
    trip_id = execute_db(
        """INSERT INTO trips (driver_id, direction, time_slot, status, started_at)
           VALUES (?, ?, ?, 'in_progress', CURRENT_TIMESTAMP)""",
        (driver_id, direction, time_slot)
    )

    # Link passengers to trip and mark queue entries as completed
    for p in passengers:
        execute_db(
            """INSERT INTO trip_passengers (trip_id, student_id, queue_id, attendance)
               VALUES (?, ?, ?, 'present')""",
            (trip_id, p['student_id'], p['id'])
        )
        execute_db(
            "UPDATE queue SET status = 'completed' WHERE id = ?",
            (p['id'],)
        )

    return {
        'trip_id': trip_id,
        'driver_id': driver_id,
        'direction': direction,
        'time_slot': time_slot,
        'passenger_count': len(passengers),
        'passengers': [{'id': p['student_id'], 'name': p['student_name']} for p in passengers]
    }, None


def get_queue_stats():
    """Get overall queue statistics (for admin dashboard)."""
    college_to_main = query_db(
        "SELECT COUNT(*) as count FROM queue WHERE direction = 'college_to_main' AND status = 'waiting'",
        one=True
    )
    main_to_college = query_db(
        "SELECT COUNT(*) as count FROM queue WHERE direction = 'main_to_college' AND status = 'waiting'",
        one=True
    )
    total_trips = query_db(
        "SELECT COUNT(*) as count FROM trips WHERE DATE(created_at) = DATE('now')",
        one=True
    )
    total_passengers = query_db(
        """SELECT COUNT(*) as count FROM trip_passengers tp
           JOIN trips t ON tp.trip_id = t.id
           WHERE DATE(t.created_at) = DATE('now')""",
        one=True
    )

    return {
        'college_to_main_waiting': college_to_main['count'] if college_to_main else 0,
        'main_to_college_waiting': main_to_college['count'] if main_to_college else 0,
        'total_waiting': (college_to_main['count'] if college_to_main else 0) +
                         (main_to_college['count'] if main_to_college else 0),
        'trips_today': total_trips['count'] if total_trips else 0,
        'passengers_today': total_passengers['count'] if total_passengers else 0,
        'current_time_slot': get_current_time_slot(),
        'operating_hours': f"{Config.OPERATING_START_HOUR}:00 AM - {Config.OPERATING_END_HOUR - 12}:00 PM"
    }
