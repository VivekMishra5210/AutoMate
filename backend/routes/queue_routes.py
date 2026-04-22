"""
AutoMate Queue Routes
"""
from flask import Blueprint, request, jsonify
from flask_jwt_extended import jwt_required, get_jwt_identity, get_jwt

from database import query_db, execute_db
from queue_logic import (
    join_queue, get_queue_position, calculate_wait_time,
    get_waiting_queue, get_next_passengers, mark_attendance,
    leave_queue, start_trip, get_queue_stats, get_current_time_slot
)
from notification import notify_driver_queue_update, notify_student_trip_ready, notify_student_joined_queue

queue_bp = Blueprint('queue', __name__)


@queue_bp.route('/api/join_queue', methods=['POST'])
@jwt_required()
def api_join_queue():
    """Student joins the transport queue."""
    user_id = get_jwt_identity()
    claims = get_jwt()

    # Only students can join queue
    if claims.get('role') != 'student':
        return jsonify({'error': 'Only students can join the queue'}), 403

    data = request.get_json()
    if not data or 'direction' not in data:
        return jsonify({'error': 'Direction is required (college_to_main or main_to_college)'}), 400

    direction = data['direction']
    if direction not in ('college_to_main', 'main_to_college'):
        return jsonify({'error': 'Invalid direction'}), 400

    result, error = join_queue(int(user_id), direction)

    if error:
        return jsonify({'error': error}), 400

    # Notify drivers about queue update
    waiting = query_db(
        "SELECT COUNT(*) as count FROM queue WHERE direction = ? AND status = 'waiting'",
        (direction,),
        one=True
    )
    if waiting and waiting['count'] >= 4:
        notify_driver_queue_update(direction, waiting['count'])

    # Notify the student that they joined the queue
    student = query_db("SELECT fcm_token FROM users WHERE id = ?", (user_id,), one=True)
    if student and student['fcm_token']:
        time_slot = get_current_time_slot() or '--:--'
        notify_student_joined_queue(
            fcm_token=student['fcm_token'],
            position=result['position'],
            direction=direction,
            time_slot=time_slot
        )

    return jsonify({
        'message': 'Successfully joined queue',
        **result
    }), 200


@queue_bp.route('/api/queue_status', methods=['GET'])
@jwt_required()
def api_queue_status():
    """Get the current user's queue status."""
    user_id = get_jwt_identity()

    # Find the student's active queue entry
    entry = query_db(
        """SELECT q.*, u.name as student_name
           FROM queue q
           JOIN users u ON q.student_id = u.id
           WHERE q.student_id = ? AND q.status = 'waiting'""",
        (user_id,),
        one=True
    )

    if not entry:
        return jsonify({
            'in_queue': False,
            'message': 'You are not currently in any queue'
        }), 200

    position = get_queue_position(int(user_id), entry['direction'])
    estimated_time = calculate_wait_time(position)

    return jsonify({
        'in_queue': True,
        'queue_id': entry['id'],
        'position': position,
        'estimated_time': estimated_time,
        'direction': entry['direction'],
        'time_slot': entry['time_slot'],
        'joined_at': entry['joined_at'],
        'current_time_slot': get_current_time_slot()
    }), 200


@queue_bp.route('/api/leave_queue', methods=['DELETE'])
@jwt_required()
def api_leave_queue():
    """Student voluntarily leaves the queue."""
    user_id = get_jwt_identity()

    success = leave_queue(int(user_id))
    if success:
        return jsonify({'message': 'Successfully left the queue'}), 200
    else:
        return jsonify({'error': 'You are not in any queue'}), 400


@queue_bp.route('/api/driver_queue', methods=['GET'])
@jwt_required()
def api_driver_queue():
    """Get the first 4 students in queue for the driver."""
    claims = get_jwt()

    if claims.get('role') != 'driver':
        return jsonify({'error': 'Only drivers can access this endpoint'}), 403

    direction = request.args.get('direction', 'college_to_main')
    if direction not in ('college_to_main', 'main_to_college'):
        return jsonify({'error': 'Invalid direction'}), 400

    passengers = get_next_passengers(direction)

    # Get total waiting count
    total = query_db(
        "SELECT COUNT(*) as count FROM queue WHERE direction = ? AND status = 'waiting'",
        (direction,),
        one=True
    )

    return jsonify({
        'passengers': [dict(p) for p in passengers],
        'passenger_count': len(passengers),
        'total_waiting': total['count'] if total else 0,
        'direction': direction,
        'current_time_slot': get_current_time_slot()
    }), 200


@queue_bp.route('/api/attendance', methods=['POST'])
@jwt_required()
def api_mark_attendance():
    """Driver marks a student as present or absent."""
    claims = get_jwt()

    if claims.get('role') != 'driver':
        return jsonify({'error': 'Only drivers can mark attendance'}), 403

    data = request.get_json()
    if not data or 'student_id' not in data or 'status' not in data:
        return jsonify({'error': 'student_id and status are required'}), 400

    student_id = data['student_id']
    status = data['status']

    if status not in ('present', 'absent'):
        return jsonify({'error': 'Status must be present or absent'}), 400

    # Find the student's active queue entry
    entry = query_db(
        "SELECT id FROM queue WHERE student_id = ? AND status = 'waiting'",
        (student_id,),
        one=True
    )
    if not entry:
        return jsonify({'error': 'Student is not in the queue'}), 404

    result, error = mark_attendance(entry['id'], student_id, status)

    if error:
        return jsonify({'error': error}), 400

    return jsonify(result), 200


@queue_bp.route('/api/start_trip', methods=['POST'])
@jwt_required()
def api_start_trip():
    """Driver starts a trip with current passengers."""
    user_id = get_jwt_identity()
    claims = get_jwt()

    if claims.get('role') != 'driver':
        return jsonify({'error': 'Only drivers can start trips'}), 403

    data = request.get_json()
    direction = data.get('direction', 'college_to_main') if data else 'college_to_main'

    if direction not in ('college_to_main', 'main_to_college'):
        return jsonify({'error': 'Invalid direction'}), 400

    # 15-minute cooldown check — applies to BOTH directions
    last_trip = query_db(
        """SELECT started_at FROM trips
           WHERE driver_id = ?
           ORDER BY started_at DESC LIMIT 1""",
        (user_id,),
        one=True
    )
    if last_trip and last_trip['started_at']:
        from datetime import datetime, timedelta
        try:
            last_time = datetime.strptime(last_trip['started_at'], '%Y-%m-%d %H:%M:%S')
            cooldown_end = last_time + timedelta(minutes=15)
            now = datetime.utcnow() + timedelta(hours=5, minutes=30)
            if now < cooldown_end:
                remaining = int((cooldown_end - now).total_seconds())
                mins = remaining // 60
                secs = remaining % 60
                return jsonify({
                    'error': f'Cooldown active. Wait {mins}m {secs}s before next trip.',
                    'cooldown_remaining': remaining
                }), 429
        except (ValueError, TypeError):
            pass  # Skip if timestamp parsing fails

    result, error = start_trip(int(user_id), direction)

    if error:
        return jsonify({'error': error}), 400

    # Notify passengers that their trip is ready
    for passenger in result.get('passengers', []):
        student = query_db(
            "SELECT fcm_token FROM users WHERE id = ?",
            (passenger['id'],),
            one=True
        )
        if student and student.get('fcm_token'):
            notify_student_trip_ready(student['fcm_token'], direction)

    return jsonify({
        'message': 'Trip started successfully',
        **result
    }), 200


@queue_bp.route('/api/queue_stats', methods=['GET'])
@jwt_required()
def api_queue_stats():
    """Get queue statistics (for admin dashboard)."""
    claims = get_jwt()

    if claims.get('role') not in ('admin', 'driver'):
        return jsonify({'error': 'Access denied'}), 403

    stats = get_queue_stats()
    return jsonify(stats), 200


@queue_bp.route('/api/full_queue', methods=['GET'])
@jwt_required()
def api_full_queue():
    """Get the full queue for a direction (for admin/driver)."""
    claims = get_jwt()

    if claims.get('role') not in ('admin', 'driver'):
        return jsonify({'error': 'Access denied'}), 403

    direction = request.args.get('direction', 'college_to_main')
    queue = get_waiting_queue(direction)

    return jsonify({
        'queue': [dict(q) for q in queue],
        'total': len(queue),
        'direction': direction
    }), 200


@queue_bp.route('/api/schedule', methods=['GET'])
@jwt_required()
def api_schedule():
    """Get today's transport schedule with slot statuses."""
    from datetime import datetime, timedelta
    from config import Config

    now = datetime.utcnow() + timedelta(hours=5, minutes=30)
    current_total_minutes = now.hour * 60 + now.minute

    slots = []
    slot_num = 1
    hour = Config.OPERATING_START_HOUR
    minute = 0

    while hour < Config.OPERATING_END_HOUR:
        # Calculate end of this slot
        end_minute = minute + Config.TIME_SLOT_MINUTES
        end_hour = hour
        if end_minute >= 60:
            end_hour += 1
            end_minute -= 60

        slot_start_total = hour * 60 + minute
        slot_end_total = end_hour * 60 + end_minute

        # Format times in 12-hour AM/PM
        start_str = format_time_12h(hour, minute)
        end_str = format_time_12h(end_hour, end_minute)

        # Determine status based on current time vs slot range
        if current_total_minutes >= slot_end_total:
            status = "passed"
        elif current_total_minutes >= slot_start_total and current_total_minutes < slot_end_total:
            status = "active"
        else:
            status = "upcoming"

        slots.append({
            'slot_number': slot_num,
            'start_time': start_str,
            'end_time': end_str,
            'status': status
        })

        slot_num += 1
        minute = end_minute
        hour = end_hour

    return jsonify({
        'slots': slots,
        'total_slots': len(slots)
    }), 200


def format_time_12h(hour, minute):
    """Format hour and minute into 12-hour AM/PM string."""
    period = "A.M" if hour < 12 else "P.M"
    display_hour = hour % 12
    if display_hour == 0:
        display_hour = 12
    return f"{display_hour}:{minute:02d} {period}"
