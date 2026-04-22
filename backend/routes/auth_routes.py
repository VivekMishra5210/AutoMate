"""
AutoMate Authentication Routes
"""
from flask import Blueprint, request, jsonify
from flask_jwt_extended import create_access_token, jwt_required, get_jwt_identity
import bcrypt

from database import query_db, execute_db

auth_bp = Blueprint('auth', __name__)


@auth_bp.route('/api/register', methods=['POST'])
def register():
    """Register a new user (student, driver, or admin)."""
    data = request.get_json()

    required = ['name', 'email', 'password', 'role']
    for field in required:
        if field not in data or not data[field]:
            return jsonify({'error': f'Missing required field: {field}'}), 400

    # ── Name Validation ──
    name = data['name'].strip()
    if len(name) < 2 or len(name) > 50:
        return jsonify({'error': 'Name must be between 2 and 50 characters'}), 400
    if not all(c.isalpha() or c.isspace() for c in name):
        return jsonify({'error': 'Name can only contain letters and spaces'}), 400

    # ── Email Validation ──
    email = data['email'].strip().lower()
    if '@' not in email or '.' not in email:
        return jsonify({'error': 'Enter a valid email address'}), 400
    if data['role'] == 'student' and not email.endswith('@iiitn.ac.in'):
        return jsonify({'error': 'Students must use an @iiitn.ac.in email'}), 400

    # ── Password Strength Validation ──
    password = data['password']
    if len(password) < 8:
        return jsonify({'error': 'Password must be at least 8 characters long'}), 400
    if not any(c.isupper() for c in password):
        return jsonify({'error': 'Password must contain at least one uppercase letter'}), 400
    if not any(c.islower() for c in password):
        return jsonify({'error': 'Password must contain at least one lowercase letter'}), 400
    if not any(c.isdigit() for c in password):
        return jsonify({'error': 'Password must contain at least one number'}), 400
    if not any(c in '!@#$%^&*()_+-=[]{}|;:,.<>?' for c in password):
        return jsonify({'error': 'Password must contain at least one special character (!@#$%^&* etc.)'}), 400

    # ── Phone Validation ──
    phone = data.get('phone', '').strip()
    if phone:
        if not phone.isdigit():
            return jsonify({'error': 'Phone number must contain only digits'}), 400
        if len(phone) != 10:
            return jsonify({'error': 'Phone number must be exactly 10 digits'}), 400

    # Validate role
    if data['role'] not in ('student', 'driver', 'admin'):
        return jsonify({'error': 'Invalid role. Must be student, driver, or admin'}), 400

    # Check if email already exists
    existing = query_db(
        "SELECT id FROM users WHERE email = ?",
        (email,),
        one=True
    )
    if existing:
        return jsonify({'error': 'Email already registered'}), 409

    # Hash password with bcrypt
    password_hash = bcrypt.hashpw(
        password.encode('utf-8'),
        bcrypt.gensalt()
    ).decode('utf-8')

    # Driver-specific fields
    auto_number = data.get('auto_number', '')

    if data['role'] == 'driver' and not phone:
        return jsonify({'error': 'Phone number is required for drivers'}), 400

    # Insert user
    user_id = execute_db(
        """INSERT INTO users (name, email, password_hash, role, phone, auto_number)
           VALUES (?, ?, ?, ?, ?, ?)""",
        (name, email, password_hash, data['role'], phone, auto_number)
    )

    # Generate JWT token
    access_token = create_access_token(
        identity=str(user_id),
        additional_claims={'role': data['role'], 'name': name}
    )

    return jsonify({
        'message': 'Registration successful',
        'user_id': user_id,
        'token': access_token,
        'role': data['role'],
        'name': name
    }), 201


@auth_bp.route('/api/login', methods=['POST'])
def login():
    """Authenticate a user and return JWT token."""
    data = request.get_json()

    # Validate required fields
    if not data or 'email' not in data or 'password' not in data:
        return jsonify({'error': 'Email and password are required'}), 400

    # ── Login Email Validation ──
    email = data['email'].strip().lower()
    if '@' not in email or '.' not in email:
        return jsonify({'error': 'Enter a valid email address'}), 400

    # ── Login Password Validation ──
    password = data['password']
    if len(password) < 1:
        return jsonify({'error': 'Password cannot be empty'}), 400

    role = data.get('role')  # Optional role filter

    # Find user by email
    if role:
        user = query_db(
            "SELECT * FROM users WHERE email = ? AND role = ?",
            (email, role),
            one=True
        )
    else:
        user = query_db(
            "SELECT * FROM users WHERE email = ?",
            (email,),
            one=True
        )

    if not user:
        return jsonify({'error': 'Invalid email or password'}), 401

    # Verify password with bcrypt
    if not bcrypt.checkpw(data['password'].encode('utf-8'), user['password_hash'].encode('utf-8')):
        return jsonify({'error': 'Invalid email or password'}), 401

    # Check if user is active
    if not user['is_active']:
        return jsonify({'error': 'Account is deactivated'}), 403

    # Update FCM token if provided
    fcm_token = data.get('fcm_token')
    if fcm_token:
        execute_db(
            "UPDATE users SET fcm_token = ? WHERE id = ?",
            (fcm_token, user['id'])
        )

    # Generate JWT token
    access_token = create_access_token(
        identity=str(user['id']),
        additional_claims={'role': user['role'], 'name': user['name']}
    )

    return jsonify({
        'message': 'Login successful',
        'token': access_token,
        'user_id': user['id'],
        'role': user['role'],
        'name': user['name'],
        'email': user['email']
    }), 200


@auth_bp.route('/api/profile', methods=['GET'])
@jwt_required()
def get_profile():
    """Get the current user's profile."""
    user_id = get_jwt_identity()
    user = query_db(
        "SELECT id, name, email, role, phone, auto_number, created_at FROM users WHERE id = ?",
        (user_id,),
        one=True
    )
    if not user:
        return jsonify({'error': 'User not found'}), 404

    return jsonify({'user': user}), 200


@auth_bp.route('/api/update_fcm_token', methods=['POST'])
@jwt_required()
def update_fcm_token():
    """Update the user's FCM token for push notifications."""
    user_id = get_jwt_identity()
    data = request.get_json()

    if not data or 'fcm_token' not in data:
        return jsonify({'error': 'FCM token is required'}), 400

    execute_db(
        "UPDATE users SET fcm_token = ? WHERE id = ?",
        (data['fcm_token'], user_id)
    )

    return jsonify({'message': 'FCM token updated'}), 200
