# 🛺 AutoMate — Campus Transport Coordination System

**AutoMate** is an end-to-end transport coordination system designed to solve the chaos of unorganized auto-rickshaw queues at **IIIT Nagpur**. It streamlines the process of traveling between the college campus and the main road by providing a structured, fair, and real-time digital queue system for students and a smart dispatch dashboard for auto drivers.

---

## ✨ Key Features

### For Students
* **Real-time Digital Queue:** Join the queue virtually (College → Main Road or Main Road → College) without standing in the sun.
* **Wait Time Estimation:** See your exact position in the queue and estimated wait time dynamically.
* **Push Notifications:** Get instant alerts when you successfully join a queue and when your ride is ready to board.
* **Multi-Language Support:** Fully localized in **English, Hindi, and Marathi**.
* **Dark / Light Mode:** Adaptive UI that respects system preferences with an elegant custom gradient design.
* **Complaint System:** Report missing drivers, misbehavior, or autos not arriving directly to administration.

### For Auto Drivers
* **Driver Dashboard:** See exactly how many students are waiting at the respective gates.
* **Smart Dispatch:** The system auto-assigns 4 students per ride based on the First-In-First-Out (FIFO) queue.
* **Attendance System:** Drivers can mark physically present students. If a student misses their turn 2 times, they are removed from the queue.
* **Trip Cooldown Enforcement:** To ensure fairness, drivers have a 15-minute cooldown between accepting new trips.

### For Administrators
* **Admin Analytics:** View live statistics of active queue sizes, daily completed trips, and total passengers moved.
* **Complaint Tracking:** Monitor student complaints in real-time to manage transport contractor performance.

---

## 🛠️ Technology Stack

**Frontend (Mobile App)**
* **Language:** Java
* **Framework:** Android SDK (Minimum API 24)
* **Design:** Material Design 3, custom XML layouts, DayNight Themes
* **Networking:** Retrofit2 + OkHttp3
* **JSON Parsing:** Gson

**Backend (REST API)**
* **Language:** Python 3
* **Framework:** Flask
* **Security:** Flask-JWT-Extended (Token-based Auth), Bcrypt (Password Hashing)
* **Database:** SQLite (Relational DB with parameterized SQL injection protection)
* **Deployment:** Gunicorn / WSGI
* **Notifications:** Firebase Admin SDK (FCM)

---


