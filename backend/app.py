import os
import sys
from datetime import datetime, timedelta
from decimal import Decimal
import psycopg2
from psycopg2.extras import RealDictCursor
from flask import Flask, request, jsonify, send_from_directory
from flask_jwt_extended import JWTManager, create_access_token, jwt_required, get_jwt_identity
from werkzeug.security import generate_password_hash, check_password_hash
from flask_cors import CORS
from flask_socketio import SocketIO, emit, join_room, leave_room
from dotenv import load_dotenv
from contextlib import contextmanager
from models import db, User, Alert, Location, DeviceActivityLog, PoliceResponseLog
# --- SHESHIELD AI INTEGRATION ---
# Add sheshield_deploy to path for importing its modules
DEPLOY_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), '../sheshield_deploy'))
if DEPLOY_PATH not in sys.path:
    sys.path.append(DEPLOY_PATH)

try:
    from risk_engine_v2 import SheShieldRiskEngine, log_assessment
    risk_engine = SheShieldRiskEngine()
    print("--- SheShield AI Risk Engine: LOADED & READY ---")
except Exception as e:
    print(f"--- SheShield AI Risk Engine Error: {e} ---")
    risk_engine = None

# Force unbuffered output
sys.stdout.reconfigure(line_buffering=True)
print("--- SHESHIELD BACKEND: REAL-TIME COMMAND CENTER (SocketIO) ---")

load_dotenv(os.path.join(os.path.dirname(__file__), '../.env'))

# Get absolute path to dashboard folder
DASHBOARD_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), '../web_dashboard'))
print(f"--- Dashboard Path: {DASHBOARD_PATH}")

app = Flask(__name__, static_folder=DASHBOARD_PATH, static_url_path='')
CORS(app)
socketio = SocketIO(app, cors_allowed_origins="*")
print(f"--- SocketIO initialized (async_mode: {socketio.async_mode}) ---")

app.config['JWT_SECRET_KEY'] = os.getenv('SECRET_KEY', 'sheshield_secret_key')
app.config['JWT_ACCESS_TOKEN_EXPIRES'] = timedelta(days=30)
jwt = JWTManager(app)

DATABASE_URL = os.getenv('DATABASE_URL')
app.config['SQLALCHEMY_DATABASE_URI'] = DATABASE_URL
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False
db.init_app(app)

def get_db_connection():
    return psycopg2.connect(DATABASE_URL, cursor_factory=RealDictCursor)

@contextmanager
def db_conn():
    conn = get_db_connection()
    try:
        yield conn
    finally:
        conn.close()

def format_json(data):
    if isinstance(data, list):
        return [format_json(i) for i in data]
    if isinstance(data, dict):
        return {k: format_json(v) for k, v in data.items()}
    if isinstance(data, Decimal):
        return float(data)
    if isinstance(data, datetime):
        return data.isoformat()
    return data

@app.route('/')
def dashboard():
    # Check if file exists first
    index_path = os.path.join(DASHBOARD_PATH, 'index.html')
    if os.path.exists(index_path):
        return send_from_directory(DASHBOARD_PATH, 'index.html')
    else:
        return f"<h1>Backend Live</h1><p>Dashboard not found at: {DASHBOARD_PATH}</p>", 200

@app.route('/api-status')
def status():
    return jsonify({"message": "SheShield AI Backend is live (Bulletproof Mode)."}), 200

@app.route('/web_dashboard/<path:filename>')
def serve_dashboard(filename):
    return send_from_directory(DASHBOARD_PATH, filename)

@app.route('/register', methods=['POST'])
def register():
    try:
        data = request.get_json()
        if not data:
            return jsonify({"message": "No data provided"}), 400
            
        required = ['email', 'password', 'full_name', 'phone_number']
        for field in required:
            if field not in data:
                return jsonify({"message": f"Missing required field: {field}"}), 400

        # Check if email or phone already exists
        if User.query.filter((User.email == data['email']) | (User.phone_number == data['phone_number'])).first():
            return jsonify({"message": "Email or Phone already exists"}), 400
            
        hashed_pw = generate_password_hash(data['password'], method='pbkdf2:sha256')
        
        new_user = User(
            full_name=data['full_name'],
            phone_number=data['phone_number'],
            email=data['email'],
            password_hash=hashed_pw,
            home_address=data.get('home_address', ''),
            guardian_details=data.get('guardian_details', ''),
            emergency_contacts=data.get('emergency_contacts', '')
        )
        
        db.session.add(new_user)
        db.session.commit()
        
        access_token = create_access_token(identity=str(new_user.id))
        return jsonify({
            "message": "User registered successfully", 
            "user_id": str(new_user.id),
            "name": new_user.full_name,
            "user_name": new_user.full_name,
            "access_token": access_token
        }), 201
    except Exception as e:
        db.session.rollback()
        print(f"Error during registration: {e}")
        return jsonify({"message": "Registration failed", "error": str(e)}), 500

@app.route('/login', methods=['POST'])
def login():
    try:
        data = request.get_json()
        if not data or 'email' not in data or 'password' not in data:
            return jsonify({"message": "Missing email or password"}), 400
            
        user = User.query.filter_by(email=data['email']).first()
        
        if user and check_password_hash(user.password_hash, data['password']):
            user.is_online = True
            
            # Log login activity via SQLAlchemy instead of raw SQL to avoid leaks
            log = DeviceActivityLog(user_id=user.id, action='USER_LOGIN')
            db.session.add(log)
            db.session.commit()
            
            access_token = create_access_token(identity=str(user.id))
            # Send refresh token as well to satisfy mobile app expectations
            refresh_token = create_access_token(identity=str(user.id)) 
            
            return jsonify({
                "access_token": access_token,
                "refresh_token": refresh_token,
                "user_id": str(user.id),
                "name": user.full_name,
                "user_name": user.full_name
            }), 200
            
        return jsonify({"message": "Invalid email or password"}), 401
    except Exception as e:
        print(f"Error during login: {e}")
        return jsonify({"message": "Login failed", "error": str(e)}), 500

@app.route('/device-online', methods=['POST'])
@jwt_required()
def device_online():
    user_id = get_jwt_identity()
    data = request.get_json()
    status = data.get('status', 'ONLINE')
    
    with db_conn() as conn:
        cur = conn.cursor()
        cur.execute("""
            UPDATE users SET 
                is_online = TRUE, 
                device_status = %s, 
                last_seen = NOW() 
            WHERE id = %s
        """, (status, user_id))
        
        cur.execute("INSERT INTO device_activity_logs (user_id, action) VALUES (%s, %s)", (user_id, f"DEVICE_{status}"))
        conn.commit()
        
        cur.execute("""
            SELECT u.id, u.full_name, u.phone_number, u.risk_score, u.speed, u.movement_status,
                   l.latitude, l.longitude
            FROM users u
            LEFT JOIN (
                SELECT DISTINCT ON (user_id) * FROM locations ORDER BY user_id, created_at DESC
            ) l ON u.id = l.user_id
            WHERE u.id = %s
        """, (user_id,))
        user = cur.fetchone()
    
    socketio.emit('user_active', format_json({
        "id": int(user['id']),
        "name": user['full_name'],
        "phone": user['phone_number'],
        "status": status,
        "risk_score": float(user['risk_score'] or 0),
        "movement_status": user['movement_status'],
        "latitude": user['latitude'],
        "longitude": user['longitude'],
        "timestamp": datetime.now()
    }))
    return jsonify({"message": "Device marked as active"}), 200

@app.route('/device-offline', methods=['POST'])
@jwt_required()
def device_offline():
    user_id = get_jwt_identity()
    with db_conn() as conn:
        cur = conn.cursor()
        cur.execute("UPDATE users SET is_online = FALSE, device_status = 'OFFLINE', last_seen = NOW() WHERE id = %s", (user_id,))
        cur.execute("INSERT INTO device_activity_logs (user_id, action) VALUES (%s, 'DEVICE_OFFLINE')", (user_id,))
        conn.commit()
        
        cur.execute("SELECT full_name FROM users WHERE id = %s", (user_id,))
        user = cur.fetchone()
    
    name = user['full_name'] if user and user.get('full_name') else "Unknown User"
    socketio.emit('user_offline', {"id": user_id, "name": name, "status": "OFFLINE"})
    return jsonify({"message": "Device marked as offline"}), 200
def extract_sensor_data(data):
    """
    Standardizes sensor data mapping from different mobile request formats 
    to the Risk Engine format.
    """
    # Debug raw data keys for troubleshooting
    print(f"DEBUG: Incoming sensor data: {data}")

    # Mapping for PredictRiskRequest (Android) vs legacy/direct formats
    battery = data.get("battery_level")
    if battery is None:
        battery = data.get("battery", 100)
    
    # Helper to handle truthy values from various clients (strings, ints, booleans)
    def is_truthy(val):
        if val is None: return False
        if isinstance(val, bool): return val
        if isinstance(val, (int, float)): return val != 0
        return str(val).lower() in ("true", "yes", "1", "t", "y")

    # Check for night mode in various possible keys
    is_night_system = is_truthy(data.get('is_night')) or is_truthy(data.get('night_travel'))
    
    # Manual toggle check
    is_night_manual = any([
        is_truthy(data.get('night_travel_mode')),
        is_truthy(data.get('night_mode')),
        is_truthy(data.get('nightTravelMode'))
    ])
    
    # Manual toggle always wins
    night_travel = is_night_manual or is_night_system

    return {
        "night_travel": night_travel,
        "road_type": data.get('road_type', "Unknown"),
        "unsafe_zone": is_truthy(data.get('unsafe_zone')) or is_truthy(data.get('route_deviation')),
        "battery": int(battery),
        "shake": is_truthy(data.get('shake_detected')) or is_truthy(data.get('shake')),
        "voice_trigger": is_truthy(data.get('voice_trigger')),
        "sudden_stop": is_truthy(data.get('sudden_stop')),
        "manual_sos": is_truthy(data.get('manual_sos')),
        "route_deviation": is_truthy(data.get('route_deviation')),
        "movement_status": data.get('movement_status', 'STATIONARY')
    }

@app.route('/predict-risk', methods=['POST'])
@jwt_required()
def predict_risk_route():
    user_id = get_jwt_identity()
    data = request.get_json()
    
    lat = data.get('latitude')
    lng = data.get('longitude')
    speed = data.get('speed', 0.0)
    
    if lat is None or lng is None:
        return jsonify({"message": "Location required"}), 400

    sensor_data = extract_sensor_data(data)
    
    risk_score = 0
    risk_level = "Safe"
    assessment = {}
    auto_alert = False

    if risk_engine:
        try:
            location = {"lat": float(lat), "lon": float(lng)}
            assessment = risk_engine.assess(sensor_data, location)
            risk_score = assessment["final_score"]
            risk_level = assessment["risk_level"]
            auto_alert = assessment["auto_alert"]
            
            # Extract component scores correctly
            rule_score = assessment["components"]["rule_score"]
            ml_score = assessment["components"]["ml_score"]
            
            # Log for training
            log_assessment(sensor_data, location, assessment)
        except Exception as e:
            print(f"Risk assessment error in /predict-risk: {e}")
            risk_score = data.get('risk_score', 0)
            rule_score = 0
            ml_score = 0
    else:
        risk_score = data.get('risk_score', 0)
        rule_score = 0
        ml_score = 0

    # Fetch user info for socket emission
    with db_conn() as conn:
        cur = conn.cursor()
        cur.execute("SELECT full_name, phone_number FROM users WHERE id = %s", (user_id,))
        user_info = cur.fetchone()
        
        # Check if user has an active alert
        cur.execute("SELECT EXISTS(SELECT 1 FROM alerts WHERE user_id = %s AND status != 'RESOLVED') as alert_exists", (user_id,))
        alert_row = cur.fetchone()
        has_alert = alert_row['alert_exists'] if alert_row else False

    # Standardize data for emission
    user_name = user_info['full_name'] if user_info and user_info.get('full_name') else "Active Device"
    user_phone = user_info['phone_number'] if user_info and user_info.get('phone_number') else ""
    movement_status = data.get('movement_status', 'MOVING' if speed > 0.5 else 'STATIONARY')
    battery = sensor_data.get("battery", 80)

    # Emit normal location update to sync map
    socketio.emit('location_update', format_json({
        "id": int(user_id),
        "name": user_name,
        "phone": user_phone,
        "latitude": lat,
        "longitude": lng,
        "speed": speed,
        "movement_status": movement_status,
        "risk_score": float(risk_score),
        "risk_level": risk_level,
        "battery": battery,
        "assessment": assessment,
        "has_alert": has_alert,
        "is_online": True,
        "timestamp": datetime.now()
    }))

    return jsonify({
        "risk_score": int(risk_score),
        "risk_level": risk_level,
        "rule_score": float(rule_score),
        "ml_score": float(ml_score),
        "message": f"Assessment complete: {risk_level}",
        "action": "TRIGGER_ALARM" if auto_alert else "MONITOR",
        "auto_alert": auto_alert
    }), 200

@app.route('/save-location', methods=['POST'])
@jwt_required()
def save_location():
    user_id = get_jwt_identity()
    data = request.get_json()
    
    lat = data.get('latitude')
    lng = data.get('longitude')
    speed = data.get('speed', 0.0)
    
    # Extract standardized sensor data
    sensor_data = extract_sensor_data(data)
    battery = sensor_data["battery"]

    # Perform AI Risk Assessment
    risk_score = 0
    risk_level = "Safe"
    auto_alert = False
    assessment = {}

    if risk_engine:
        try:
            location = {"lat": float(lat), "lon": float(lng)}
            assessment = risk_engine.assess(sensor_data, location)
            risk_score = assessment["final_score"]
            risk_level = assessment["risk_level"]
            auto_alert = assessment["auto_alert"]
            
            rule_score = assessment["components"]["rule_score"]
            ml_score = assessment["components"]["ml_score"]
            
            # Fetch user info for logging
            with db_conn() as conn:
                cur = conn.cursor()
                cur.execute("SELECT full_name FROM users WHERE id = %s", (user_id,))
                user_info = cur.fetchone()
                user_name = user_info['full_name'] if user_info else f"User {user_id}"

            # Detailed console logging for the user to see "AI working"
            print(f"\n--- [AI ASSESSMENT] {user_name} ---")
            print(f"Location: {lat}, {lng} | Speed: {speed}")
            print(f"Battery: {sensor_data['battery']}% | Night: {sensor_data['night_travel']}")
            print(f"Triggers: SOS={sensor_data['manual_sos']}, Shake={sensor_data['shake']}, Voice={sensor_data['voice_trigger']}")
            print(f"Result: {risk_score}% ({risk_level})")
            print(f"Rule-Based Score: {rule_score}% | ML Prediction: {ml_score}%")
            print(f"Strategy: {assessment.get('blend_strategy', 'N/A')}")
            if auto_alert: print("ALERT: AUTO-ALERT TRIGGERED!")
            print("----------------------------------\n")

            # Log for training
            log_assessment(sensor_data, location, assessment)
        except Exception as e:
            print(f"Risk assessment error in /save-location: {e}")
            risk_score = data.get('risk_score', 0)
    else:
        risk_score = data.get('risk_score', 0)

    movement_status = data.get('movement_status', 'MOVING' if speed > 1 else 'STATIONARY')

    with db_conn() as conn:
        cur = conn.cursor()
        # Save to history
        cur.execute("""
            INSERT INTO locations (user_id, latitude, longitude, speed, movement_status, risk_score) 
            VALUES (%s, %s, %s, %s, %s, %s)
        """, (user_id, lat, lng, speed, movement_status, risk_score))
        
        # Update current state
        cur.execute("""
            UPDATE users SET 
                last_seen = NOW(), 
                speed = %s, 
                movement_status = %s, 
                risk_score = %s,
                battery_status = %s,
                is_online = TRUE 
            WHERE id = %s
        """, (speed, movement_status, risk_score, battery, user_id))
        
        # Fetch user info for socket emission
        cur.execute("SELECT full_name, phone_number FROM users WHERE id = %s", (user_id,))
        user_info = cur.fetchone()
        conn.commit()

    # Handle Auto Emergency Alert
    if auto_alert or sensor_data["manual_sos"]:
        
        # Check for existing active alert
        existing_alert = Alert.query.filter_by(user_id=user_id, status='EMERGENCY').first()
        if not existing_alert:
            new_alert = Alert(
                user_id=user_id,
                risk_score=int(risk_score),
                status='EMERGENCY',
                latitude=lat,
                longitude=lng
            )
            db.session.add(new_alert)
            db.session.commit()
            
            # Battery-based Alert Logic
            alert_channels = []
            if battery > 80:
                alert_channels = ["WhatsApp", "Live Location", "Emergency Call"]
            elif 40 <= battery <= 60:
                alert_channels = ["Live Location", "Emergency Call"]
            else:
                alert_channels = ["Last Known Location", "Emergency Call Only"]

            print(f"🚨 AUTO ALERT: {user_info['full_name']} | Channels: {', '.join(alert_channels)}")

            # Emit to all connected dashboards
            socketio.emit('emergency_alert', format_json({
                "id": new_alert.id,
                "user_id": user_id,
                "name": user_info['full_name'],
                "phone": user_info['phone_number'],
                "latitude": lat,
                "longitude": lng,
                "trigger": "AI_HYBRID_ENGINE",
                "risk_score": risk_score,
                "risk_level": risk_level,
                "battery": battery,
                "assessment": assessment,
                "alert_channels": alert_channels,
                "timestamp": datetime.now()
            }))

    # Check if user has an active alert for final status
    with db_conn() as conn:
        cur = conn.cursor()
        cur.execute("SELECT EXISTS(SELECT 1 FROM alerts WHERE user_id = %s AND status != 'RESOLVED') as alert_exists", (user_id,))
        has_alert = cur.fetchone()['alert_exists']

    # Emit normal location update
    socketio.emit('location_update', format_json({
        "id": int(user_id),
        "name": user_info['full_name'],
        "phone": user_info['phone_number'],
        "latitude": lat,
        "longitude": lng,
        "speed": speed,
        "movement_status": movement_status,
        "risk_score": risk_score,
        "risk_level": risk_level,
        "battery": battery,
        "assessment": assessment,
        "has_alert": has_alert,
        "is_online": True,
        "timestamp": datetime.now()
    }))

    return jsonify({
        "message": "Location and Risk assessed", 
        "risk_score": int(risk_score), 
        "risk_level": risk_level,
        "auto_alert": auto_alert,
        "has_alert": has_alert
    }), 200



@app.route('/update-risk', methods=['POST'])
@jwt_required()
def update_risk():
    user_id = get_jwt_identity()
    data = request.get_json()
    score = data.get('risk_score', 0)
    
    with db_conn() as conn:
        cur = conn.cursor()
        cur.execute("UPDATE users SET risk_score = %s WHERE id = %s", (score, user_id))
        
        if score >= 80:
            cur.execute("""
                INSERT INTO alerts (user_id, risk_score, status) 
                VALUES (%s, %s, 'EMERGENCY')
            """, (user_id, score))
        
        conn.commit()
        
        cur.execute("SELECT full_name FROM users WHERE id = %s", (user_id,))
        user = cur.fetchone()
    
    socketio.emit('risk_update', {"id": user_id, "name": user['full_name'], "risk_score": score})
    return jsonify({"message": "Risk score updated"}), 200

@app.route('/send-alert', methods=['POST'])
@jwt_required()
def send_alert():
    user_id = get_jwt_identity()
    data = request.get_json()
    lat = data.get('latitude')
    lng = data.get('longitude')
    trigger = data.get('trigger_type', 'MANUAL_SOS')
    risk = data.get('risk_score', 100)

    with db_conn() as conn:
        cur = conn.cursor()
        cur.execute("""
            INSERT INTO alerts (user_id, risk_score, status, latitude, longitude) 
            VALUES (%s, %s, %s, %s, %s) RETURNING id
        """, (user_id, risk, 'EMERGENCY', lat, lng))
        alert_id = cur.fetchone()['id']
        conn.commit()
        
        cur.execute("SELECT full_name, phone_number FROM users WHERE id = %s", (user_id,))
        user = cur.fetchone()

    # Instant Socket Alert
    socketio.emit('emergency_alert', format_json({
        "id": alert_id,
        "user_id": user_id,
        "name": user['full_name'],
        "phone": user['phone_number'],
        "latitude": lat,
        "longitude": lng,
        "trigger": trigger,
        "risk_score": risk,
        "assessment": {
            "final_score": risk,
            "components": {"rule_score": 0, "ml_score": 0}
        },
        "timestamp": datetime.now()
    }))

    return jsonify({"message": "Emergency alert triggered", "alert_id": alert_id}), 200

@app.route('/live-users', methods=['GET'])
@app.route('/active-locations', methods=['GET'])
def live_users():
    with db_conn() as conn:
        cur = conn.cursor()
        # Get last known location and status for all users
        cur.execute("""
            SELECT DISTINCT ON (u.id) 
                u.id, 
                COALESCE(u.full_name, 'Active User') as name, 
                u.phone_number as phone, 
                COALESCE(u.risk_score, 0) as risk_score, 
                u.device_status, 
                u.is_online,
                u.movement_status, 
                u.speed, 
                COALESCE(u.battery_status, 80) as battery,
                COALESCE(l.latitude, 12.9716) as latitude, 
                COALESCE(l.longitude, 77.5946) as longitude, 
                l.created_at as last_seen,
                EXISTS(SELECT 1 FROM alerts WHERE user_id = u.id AND status != 'RESOLVED') as has_alert
            FROM users u
            LEFT JOIN locations l ON u.id = l.user_id
            ORDER BY u.id, l.created_at DESC
        """)
        users = cur.fetchall()
    return jsonify(format_json(users)), 200

@app.route('/movement-history/<int:user_id>', methods=['GET'])
def movement_history(user_id):
    with db_conn() as conn:
        cur = conn.cursor()
        cur.execute("""
            SELECT latitude, longitude, speed, movement_status, risk_score, created_at 
            FROM locations 
            WHERE user_id = %s 
            ORDER BY created_at ASC 
        """, (user_id,))
        history = cur.fetchall()
    return jsonify(format_json(history)), 200

@app.route('/emergency-alerts', methods=['GET'])
def emergency_alerts():
    with db_conn() as conn:
        cur = conn.cursor()
        cur.execute("""
            SELECT a.*, u.full_name as name, u.phone_number as phone 
            FROM alerts a 
            LEFT JOIN users u ON a.user_id = u.id 
            WHERE a.status != 'RESOLVED'
            ORDER BY a.created_at DESC
        """)
        alerts = cur.fetchall()
    return jsonify(format_json(alerts)), 200

@app.route('/police-dashboard', methods=['GET'])
def police_dashboard():
    with db_conn() as conn:
        cur = conn.cursor()
        cur.execute("SELECT COUNT(*) FROM users")
        total = cur.fetchone()['count']
        cur.execute("SELECT COUNT(*) FROM users WHERE is_online = TRUE")
        online = cur.fetchone()['count']
        cur.execute("SELECT COUNT(*) FROM alerts WHERE status != 'RESOLVED'")
        active = cur.fetchone()['count']
    return jsonify({
        "total_users": total, 
        "online_users": online, 
        "active_alerts": active,
        "system_status": "OPERATIONAL",
        "timestamp": datetime.now().isoformat()
    }), 200

@app.route('/police/assign', methods=['POST'])
def assign_police():
    data = request.get_json()
    alert_id = data.get('alert_id')
    officer_name = data.get('officer_name', 'Unknown Officer')
    
    with db_conn() as conn:
        cur = conn.cursor()
        cur.execute("""
            INSERT INTO police_response_logs (alert_id, officer_name, action_taken, status) 
            VALUES (%s, %s, 'DISPATCHED', 'IN_PROGRESS')
        """, (alert_id, officer_name))
        
        cur.execute("UPDATE alerts SET status = 'RESPONDING', officer_assigned = %s WHERE id = %s", (officer_name, alert_id))
        conn.commit()
    
    socketio.emit('police_dispatched', {"alert_id": alert_id, "officer": officer_name})
    return jsonify({"message": f"Officer {officer_name} assigned."}), 200

@app.route('/resolve-alert', methods=['POST'])
def resolve_alert():
    data = request.get_json()
    alert_id = data.get('alert_id')
    
    with db_conn() as conn:
        cur = conn.cursor()
        cur.execute("UPDATE alerts SET status = 'RESOLVED' WHERE id = %s", (alert_id,))
        cur.execute("UPDATE police_response_logs SET status = 'RESOLVED', action_taken = 'HELP_PROVIDED' WHERE alert_id = %s", (alert_id,))
        conn.commit()
    
    socketio.emit('alert_resolved', {"alert_id": alert_id})
    return jsonify({"message": "Alert marked as resolved."}), 200

@app.route('/user-profile/<int:id>', methods=['GET'])
@jwt_required()
def profile(id):
    with db_conn() as conn:
        cur = conn.cursor()
        cur.execute("SELECT id, full_name, phone_number, email, risk_score, device_status, last_seen, home_address, guardian_details, emergency_contacts FROM users WHERE id = %s", (id,))
        user = cur.fetchone()
    return jsonify(format_json(user)) if user else (jsonify({"error": "Not found"}), 404)

if __name__ == '__main__':
    # Using 5001 to avoid port conflicts
    # Binding to 0.0.0.0 to ensure cloudflared can connect (fixes [::1] vs 127.0.0.1 issues)
    socketio.run(app, host='0.0.0.0', port=5001, debug=True, allow_unsafe_werkzeug=True)
