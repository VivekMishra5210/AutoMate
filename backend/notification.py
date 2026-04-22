"""
AutoMate Firebase Notification Service

This module handles sending push notifications via Firebase Cloud Messaging (FCM).
If Firebase credentials are not configured, notifications are logged but not sent.
"""
import os
import json
from config import Config

# Firebase availability flag
_firebase_available = False
_firebase_app = None

try:
    import firebase_admin
    from firebase_admin import credentials, messaging

    if os.path.exists(Config.FIREBASE_CREDENTIALS_PATH):
        cred = credentials.Certificate(Config.FIREBASE_CREDENTIALS_PATH)
        _firebase_app = firebase_admin.initialize_app(cred)
        _firebase_available = True
        print("[FCM] Firebase initialized successfully")
    else:
        print(f"[FCM] Firebase credentials not found at: {Config.FIREBASE_CREDENTIALS_PATH}")
        print("[FCM] Notifications will be logged but not sent. See docs/firebase_setup_guide.md")
except ImportError:
    print("[FCM] firebase-admin not installed. Notifications disabled.")
except Exception as e:
    print(f"[FCM] Firebase initialization error: {e}")
    print("[FCM] Notifications will be logged but not sent.")


def send_notification(fcm_token, title, body, data=None):
    """
    Send a push notification to a specific device.

    Args:
        fcm_token: The device's FCM registration token
        title: Notification title
        body: Notification body text
        data: Optional dict of key-value data payload
    """
    print(f"[FCM] Notification -> {title}: {body}")

    if not _firebase_available or not fcm_token:
        print("[FCM] Skipping send (Firebase not configured or no token)")
        return False

    try:
        message = messaging.Message(
            notification=messaging.Notification(
                title=title,
                body=body
            ),
            data=data or {},
            token=fcm_token
        )
        response = messaging.send(message)
        print(f"[FCM] Sent successfully: {response}")
        return True
    except Exception as e:
        print(f"[FCM] Send failed: {e}")
        return False


def send_notification_to_topic(topic, title, body, data=None):
    """
    Send a notification to all devices subscribed to a topic.
    Topics: 'drivers', 'students', 'admin'
    """
    print(f"[FCM] Topic notification [{topic}] -> {title}: {body}")

    if not _firebase_available:
        print("[FCM] Skipping send (Firebase not configured)")
        return False

    try:
        message = messaging.Message(
            notification=messaging.Notification(
                title=title,
                body=body
            ),
            data=data or {},
            topic=topic
        )
        response = messaging.send(message)
        print(f"[FCM] Topic send successful: {response}")
        return True
    except Exception as e:
        print(f"[FCM] Topic send failed: {e}")
        return False

def notify_driver_queue_update(direction, waiting_count):
    """Notify drivers that students are waiting."""
    location = "College Gate" if direction == "college_to_main" else "Main Road"
    send_notification_to_topic(
        topic='drivers',
        title='Passengers Waiting',
        body=f'{waiting_count} student{"s" if waiting_count != 1 else ""} waiting at {location}',
        data={'direction': direction, 'count': str(waiting_count)}
    )

def notify_student_position_update(fcm_token, position, estimated_time):
    """Notify a student about their updated queue position."""
    send_notification(
        fcm_token=fcm_token,
        title='Queue Update',
        body=f'Your position: #{position}. Estimated wait: {estimated_time}',
        data={'position': str(position), 'estimated_time': estimated_time}
    )


def notify_student_trip_ready(fcm_token, direction):
    """Notify a student that their trip is about to depart."""
    destination = "Main Road" if direction == "college_to_main" else "College"
    send_notification(
        fcm_token=fcm_token,
        title='Your Ride is Ready! 🛺',
        body=f'Please proceed to the pickup point. Heading to {destination}.',
        data={'direction': direction, 'action': 'trip_ready'}
    )


def notify_student_joined_queue(fcm_token, position, direction, time_slot):
    """Notify a student that they have successfully joined the queue."""
    if direction == 'college_to_main':
        route = 'College → Main Road'
    else:
        route = 'Main Road → College'

    send_notification(
        fcm_token=fcm_token,
        title='Queue Joined! 🛺',
        body=f'Position #{position} | Slot: {time_slot} | {route}',
        data={
            'position': str(position),
            'direction': direction,
            'time_slot': time_slot,
            'action': 'queue_joined'
        }
    )
