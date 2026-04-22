"""
AutoMate Complaint Routes
"""
from flask import Blueprint, request, jsonify
from flask_jwt_extended import jwt_required, get_jwt_identity, get_jwt

from database import query_db, execute_db

complaint_bp = Blueprint('complaint', __name__)


@complaint_bp.route('/api/complaint', methods=['POST'])
@jwt_required()
def submit_complaint():
    """Student submits a complaint."""
    user_id = get_jwt_identity()
    claims = get_jwt()

    if claims.get('role') != 'student':
        return jsonify({'error': 'Only students can submit complaints'}), 403

    data = request.get_json()
    if not data or 'complaint_type' not in data or 'message' not in data:
        return jsonify({'error': 'complaint_type and message are required'}), 400

    complaint_type = data['complaint_type']
    valid_types = ['driver_absent', 'auto_not_arrived', 'misbehavior', 'other']
    if complaint_type not in valid_types:
        return jsonify({'error': f'Invalid complaint type. Valid types: {", ".join(valid_types)}'}), 400

    message = data['message'].strip()
    if len(message) < 10:
        return jsonify({'error': 'Complaint message must be at least 10 characters'}), 400

    driver_id = data.get('driver_id') 

    complaint_id = execute_db(
        """INSERT INTO complaints (student_id, driver_id, complaint_type, message)
           VALUES (?, ?, ?, ?)""",
        (user_id, driver_id, complaint_type, message)
    )

    return jsonify({
        'message': 'Complaint submitted successfully',
        'complaint_id': complaint_id
    }), 201


@complaint_bp.route('/api/complaints', methods=['GET'])
@jwt_required()
def list_complaints():
    """
    List complaints.
    - Admin/drivers see all complaints
    """
    user_id = get_jwt_identity()
    claims = get_jwt()
    role = claims.get('role')

    if role == 'student':
        complaints = query_db(
            """SELECT c.*, u.name as student_name, d.name as driver_name
               FROM complaints c
               JOIN users u ON c.student_id = u.id
               LEFT JOIN users d ON c.driver_id = d.id
               WHERE c.student_id = ?
               ORDER BY c.created_at DESC""",
            (user_id,)
        )
    elif role in ('admin', 'driver'):
        # Admin and drivers can see all complaints
        status_filter = request.args.get('status')
        if status_filter and status_filter in ('pending', 'reviewed', 'resolved'):
            complaints = query_db(
                """SELECT c.*, u.name as student_name, d.name as driver_name
                   FROM complaints c
                   JOIN users u ON c.student_id = u.id
                   LEFT JOIN users d ON c.driver_id = d.id
                   WHERE c.status = ?
                   ORDER BY c.created_at DESC""",
                (status_filter,)
            )
        else:
            complaints = query_db(
                """SELECT c.*, u.name as student_name, d.name as driver_name
                   FROM complaints c
                   JOIN users u ON c.student_id = u.id
                   LEFT JOIN users d ON c.driver_id = d.id
                   ORDER BY c.created_at DESC"""
            )
    else:
        return jsonify({'error': 'Access denied'}), 403

    return jsonify({
        'complaints': [dict(c) for c in complaints],
        'total': len(complaints)
    }), 200


@complaint_bp.route('/api/complaint/<int:complaint_id>/resolve', methods=['PUT'])
@jwt_required()
def resolve_complaint(complaint_id):
    """Admin resolves a complaint."""
    claims = get_jwt()

    if claims.get('role') != 'admin':
        return jsonify({'error': 'Only admin can resolve complaints'}), 403

    complaint = query_db(
        "SELECT * FROM complaints WHERE id = ?",
        (complaint_id,),
        one=True
    )
    if not complaint:
        return jsonify({'error': 'Complaint not found'}), 404

    data = request.get_json()
    new_status = data.get('status', 'resolved') if data else 'resolved'

    if new_status not in ('reviewed', 'resolved'):
        return jsonify({'error': 'Status must be reviewed or resolved'}), 400

    execute_db(
        "UPDATE complaints SET status = ?, resolved_at = CURRENT_TIMESTAMP WHERE id = ?",
        (new_status, complaint_id)
    )

    return jsonify({'message': f'Complaint {new_status}'}), 200
