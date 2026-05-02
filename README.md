# SheShield AI – Intelligent Women Safety Ecosystem

![SheShield AI Logo](https://img.shields.io/badge/Status-Production%20Ready-success) ![Kotlin](https://img.shields.io/badge/Mobile-Kotlin-purple) ![Python](https://img.shields.io/badge/Backend-Python%20Flask-blue) ![XGBoost](https://img.shields.io/badge/AI-XGBoost-orange)

SheShield AI is a comprehensive, production-ready, AI-driven women's safety ecosystem. It goes beyond simple SOS apps by combining real-time machine learning risk assessment, offline-first capabilities, and an integrated Police Command Dashboard to ensure an instant, reliable, and bulletproof emergency response.

## 🚀 Features Highlights

### 1. Advanced Mobile Application (Android / Kotlin)
- **Offline-First Architecture**: Functions without an internet connection using local SQLite storage. Synchronizes automatically when the connection is restored.
- **Instant Emergency Response**: Removes arbitrary delays. An SOS instantly forces the risk score to 100% and triggers RED alerts.
- **Triple-Threat Triggers**:
  - **Manual SOS**: Dedicated emergency button.
  - **Shake Detection**: Hardware accelerometer-based activation (Shake 3 times).
  - **Voice Recognition**: AI voice trigger listening for keywords (e.g., "Help me").
- **Dynamic Battery Logic**: Modifies alert transmission methods based on battery levels (e.g., fallback to native SMS when battery is critical).
- **Universal Emergency Fallback**: Automatically dials `112` or priority emergency contacts on the native dialer during SOS events.
- **Safe Routing System**: Evaluates route deviations locally and warns the user if they stray from a safe path.

### 2. Live Police Command Center (Web Dashboard)
- **Real-Time Monitoring**: Powered by WebSockets (`Socket.IO`), providing instant map updates the second an emergency is triggered.
- **Bulletproof Mapping**: Dual-engine rendering using Google Maps with automatic Leaflet.js fallback to ensure uninterrupted tracking.
- **Risk Assessment Visualization**: Color-coded risk indicators (Green/Yellow/Orange/Red) updating live based on AI predictions.
- **Incident Dispatch**: Allows operators to track case history and assign nearby response units.

### 3. SheShield AI Risk Engine (Python / XGBoost)
- **Hybrid Risk Analysis**: Combines deterministic rule-based algorithms with an XGBoost machine learning model to calculate real-time danger probabilities.
- **Feature Processing**: Evaluates location density, time of day, movement speed, sudden stops, battery status, and sensor anomalies.
- **Continuous Learning**: Logs every assessment to `live_assessments.csv` for continuous model retraining and drift monitoring.

---

## 🔄 System Flow Architecture

### 1. Normal Operation Flow
1. **App Background Service (`SafetyService.kt`)**: Silently monitors location, battery, and accelerometer in the background.
2. **Periodic AI Assessment**: Sends telematics to the Python backend.
3. **Hybrid Engine Evaluation**: The `SheShieldRiskEngine` assesses the data, assigning a `risk_score` from 0-100.
4. **UI Update**: Risk scores are broadcasted back to the user's dashboard (e.g., "Safe - 15%").

### 2. Emergency Trigger Flow
1. **Trigger Activated**: User clicks SOS, shouts a keyword, or shakes the phone.
2. **Instant Local Escalation**: `SafetyService` immediately bypasses cooldowns, forces the risk score to **100% (CRITICAL EMERGENCY)**, and turns the mobile UI RED.
3. **Dispatch Communication (`CommunicationManager.kt`)**:
   - **Local Dialer**: Instantly dials the priority emergency contact or `112`.
   - **SMS Fallback**: Fires off native SMS or Twilio SMS based on battery levels.
   - **Backend SOS Payload**: Dispatches `EmergencyAlertRequest` with the exact live location and a 100% risk score.
4. **Command Center Real-time Update**: 
   - The Flask backend saves the incident to PostgreSQL.
   - Emits a WebSocket `risk_update` event.
   - The Police Dashboard instantly updates the marker to blinking RED, sounding alarms, and initiating dispatch protocols.

---

## 🗄️ Database Structure

The project utilizes **PostgreSQL** (hosted on Neon) managed via SQLAlchemy. 

### Core Schemas

#### 1. `users` (Users Table)
Stores user credentials, emergency profiles, and live telematics.
- `id` (PK), `full_name`, `phone_number`, `email`, `password_hash`
- `home_address`, `guardian_details`, `emergency_contacts`
- **Live Status:** `battery_status`, `is_online`, `device_status`, `risk_score`, `speed`, `movement_status`, `last_seen`

#### 2. `alerts` (Emergency Alerts Table)
Logs all SOS events and tracks dispatch status.
- `id` (PK), `user_id` (FK)
- `risk_score`, `status` (SAFE/ACTIVE/RESOLVED)
- `latitude`, `longitude`, `created_at`
- `officer_assigned`

#### 3. `locations` (Location History Table)
Maintains breadcrumb trails for predictive analytics.
- `id` (PK), `user_id` (FK)
- `latitude`, `longitude`, `speed`, `movement_status`, `risk_score`, `created_at`

#### 4. `trusted_contacts` (Emergency Contacts)
- `id` (PK), `user_id` (FK)
- `name`, `phone`, `relation`

#### 5. `unsafe_zones` (Community Threat Mapping)
Crowdsourced or AI-generated high-risk areas.
- `id` (PK), `latitude`, `longitude`
- `reason`, `risk_score`, `is_police_verified`, `reported_by` (FK)

#### 6. `device_activity_logs` & `police_response_logs`
Audit tables ensuring all hardware triggers and police dispatch actions are fully logged for accountability.

---

## 🛠 Tech Stack

- **Mobile Application**: Kotlin, Coroutines, Google Maps SDK, Room Database (SQLite), Fused Location Provider.
- **Backend Services**: Python, Flask, Flask-SQLAlchemy, Flask-SocketIO, JWT Authentication.
- **AI & ML Engine**: Scikit-Learn, XGBoost, Pandas.
- **Database Layer**: PostgreSQL (Cloud/Neon), SQLAlchemy ORM.
- **Web Frontend**: HTML5, Vanilla JS, Socket.IO Client, Bootstrap, Leaflet.js.
- **Third-Party Integrations**: Twilio (SMS), Google Maps Directions/Places APIs.

---

## ⚙️ Setup & Installation Instructions

### 1. Prerequisites
- Python 3.9+
- Android Studio (Iguana or newer)
- Node/NPM (Optional for dashboard dependencies, otherwise served statically)
- PostgreSQL Database URL (e.g., Neon.tech)
- Google Maps API Key & Twilio Credentials

### 2. Backend Initialization
1. Navigate to the backend directory:
   ```bash
   cd backend
   ```
2. Install Python dependencies:
   ```bash
   pip install -r requirements.txt
   # OR
   pip install flask flask-sqlalchemy flask-jwt-extended psycopg2-binary python-dotenv flask-socketio flask-cors xgboost pandas scikit-learn
   ```
3. Create a `.env` file in the root directory:
   ```env
   DATABASE_URL=postgresql://user:pass@host/db
   SECRET_KEY=your_super_secret_jwt_key
   TWILIO_ACCOUNT_SID=your_sid
   TWILIO_AUTH_TOKEN=your_token
   TWILIO_FROM_NUMBER=your_number
   ```
4. Start the server:
   ```bash
   python app.py
   ```
   *The server will run on `http://localhost:5001`. Socket.IO will attach automatically.*

### 3. Web Dashboard (Police View)
- The Web Dashboard is statically served by the Flask application.
- Simply navigate to `http://localhost:5001/` in any modern web browser to access the real-time command center.

### 4. Android App Setup
1. Open the `mobile_app` folder in Android Studio.
2. In `app/src/main/res/values/strings.xml`, update the Google Maps API Key:
   ```xml
   <string name="google_maps_key">YOUR_API_KEY_HERE</string>
   ```
3. Update `Config.kt` in the `com.sheshield.ai` package to point to your backend IP:
   ```kotlin
   const val BASE_URL = "http://YOUR_LOCAL_IP:5001/"
   ```
4. Build and deploy to an Android physical device (Sensors/Shake detection will not work properly on emulators).

---

## 🛡️ Developed for Women Safety
Built with resilience in mind. The offline caching, dual-map rendering, battery optimization, and intelligent risk algorithms ensure that SheShield AI acts as a reliable guardian, even in the worst-case scenarios.
