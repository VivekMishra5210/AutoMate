"""
WSGI entry point for production deployments.
Services like Render, Railway, and Heroku use this file to start the server
using Gunicorn instead of the built-in Flask development server.
"""
from app import create_app

# Create the application instance
app = create_app()

if __name__ == "__main__":
    # This shouldn't run in production (gunicorn calls `app` directly)
    # but serves as a fallback.
    app.run(host="0.0.0.0", port=5000)
