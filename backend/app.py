"""
AutoMate — Campus Transport Coordination Backend
IIIT Nagpur
"""
from datetime import timedelta
from flask import Flask, jsonify
from flask_cors import CORS
from flask_jwt_extended import JWTManager
from dotenv import load_dotenv

# Load .env file automatically
load_dotenv()

from config import Config
from database import init_db
from routes.auth_routes import auth_bp
from routes.queue_routes import queue_bp
from routes.complaint_routes import complaint_bp


def create_app():
    """Application factory."""
    app = Flask(__name__)

    # Configuration
    app.config['SECRET_KEY'] = Config.SECRET_KEY
    app.config['JWT_SECRET_KEY'] = Config.JWT_SECRET_KEY
    app.config['JWT_ACCESS_TOKEN_EXPIRES'] = timedelta(seconds=Config.JWT_ACCESS_TOKEN_EXPIRES)

    # Enable CORS (allow Android app to connect)
    CORS(app, resources={r"/api/*": {"origins": "*"}})

    # Initialize JWT
    jwt = JWTManager(app)

    # JWT error handlers
    @jwt.expired_token_loader
    def expired_token_callback(jwt_header, jwt_payload):
        return jsonify({'error': 'Token has expired', 'code': 'token_expired'}), 401

    @jwt.invalid_token_loader
    def invalid_token_callback(error):
        return jsonify({'error': 'Invalid token', 'code': 'invalid_token'}), 401

    @jwt.unauthorized_loader
    def unauthorized_callback(error):
        return jsonify({'error': 'Authorization required', 'code': 'authorization_required'}), 401

    # Register blueprints
    app.register_blueprint(auth_bp)
    app.register_blueprint(queue_bp)
    app.register_blueprint(complaint_bp)

    # Health check endpoint
    @app.route('/api/health', methods=['GET'])
    def health_check():
        return jsonify({
            'status': 'running',
            'service': 'AutoMate Backend',
            'version': '1.0.0'
        }), 200

    # Root endpoint
    @app.route('/', methods=['GET'])
    def root():
        return jsonify({
            'message': 'AutoMate Backend API',
            'version': '1.0.0',
            'endpoints': {
                'health': '/api/health',
                'login': 'POST /api/login',
                'register': 'POST /api/register',
                'join_queue': 'POST /api/join_queue',
                'queue_status': 'GET /api/queue_status',
                'driver_queue': 'GET /api/driver_queue',
                'attendance': 'POST /api/attendance',
                'start_trip': 'POST /api/start_trip',
                'complaint': 'POST /api/complaint',
                'complaints': 'GET /api/complaints',
                'queue_stats': 'GET /api/queue_stats'
            }
        }), 200

    # Initialize database
    from seed import seed
    with app.app_context():
        seed()

    return app


if __name__ == '__main__':
    app = create_app()
    print("\n" + "=" * 50)
    print("  AutoMate Backend Server")
    print("  IIIT Nagpur Campus Transport")
    print("=" * 50)
    print(f"  Running on: http://127.0.0.1:5000")
    print(f"  Health check: http://127.0.0.1:5000/api/health")
    print("=" * 50 + "\n")
    app.run(host='0.0.0.0', port=5000, debug=Config.DEBUG, use_reloader=False)
