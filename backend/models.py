from flask_sqlalchemy import SQLAlchemy
from datetime import datetime

db = SQLAlchemy()

class User(db.Model):
    __tablename__ = 'users'
    id = db.Column(db.Integer, primary_key=True)
    full_name = db.Column(db.String(100), nullable=False)
    phone_number = db.Column(db.String(20), unique=True, nullable=False)
    email = db.Column(db.String(100), unique=True, nullable=False)
    password_hash = db.Column(db.Text, nullable=False)
    home_address = db.Column(db.Text)
    guardian_details = db.Column(db.Text)
    emergency_contacts = db.Column(db.Text)
    
    # Internal tracking fields
    battery_status = db.Column(db.Integer, default=100)
    last_seen = db.Column(db.DateTime, default=datetime.utcnow)
    is_online = db.Column(db.Boolean, default=False)
    device_status = db.Column(db.String(20), default='OFFLINE')
    risk_score = db.Column(db.Integer, default=0)
    speed = db.Column(db.Float, default=0.0)
    movement_status = db.Column(db.String(20), default='STATIONARY')
    created_at = db.Column(db.DateTime, default=datetime.utcnow)

class TrustedContact(db.Model):
    __tablename__ = 'trusted_contacts'
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('users.id'))
    name = db.Column(db.String(100), nullable=False)
    phone = db.Column(db.String(20), nullable=False)
    relation = db.Column(db.String(50))

class Alert(db.Model):
    __tablename__ = 'alerts'
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('users.id'))
    risk_score = db.Column(db.Integer, default=0)
    status = db.Column(db.String(20), default='SAFE')
    latitude = db.Column(db.Numeric(9, 6))
    longitude = db.Column(db.Numeric(9, 6))
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
    officer_assigned = db.Column(db.String(100))

class Location(db.Model):
    __tablename__ = 'locations'
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('users.id'))
    latitude = db.Column(db.Numeric(9, 6))
    longitude = db.Column(db.Numeric(9, 6))
    speed = db.Column(db.Float, default=0.0)
    movement_status = db.Column(db.String(20), default='STATIONARY')
    risk_score = db.Column(db.Integer, default=0)
    created_at = db.Column(db.DateTime, default=datetime.utcnow)

class UnsafeZone(db.Model):
    __tablename__ = 'unsafe_zones'
    id = db.Column(db.Integer, primary_key=True)
    latitude = db.Column(db.Numeric(9, 6))
    longitude = db.Column(db.Numeric(9, 6))
    reason = db.Column(db.Text)
    risk_score = db.Column(db.Integer, default=50)
    is_police_verified = db.Column(db.Boolean, default=False)
    reported_by = db.Column(db.Integer, db.ForeignKey('users.id'))

class DeviceActivityLog(db.Model):
    __tablename__ = 'device_activity_logs'
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('users.id'))
    action = db.Column(db.String(50))
    timestamp = db.Column(db.DateTime, default=datetime.utcnow)

class PoliceResponseLog(db.Model):
    __tablename__ = 'police_response_logs'
    id = db.Column(db.Integer, primary_key=True)
    alert_id = db.Column(db.Integer, db.ForeignKey('alerts.id'))
    officer_name = db.Column(db.String(100))
    action_taken = db.Column(db.String(200))
    status = db.Column(db.String(50))
    timestamp = db.Column(db.DateTime, default=datetime.utcnow)

class IncidentHistory(db.Model):
    __tablename__ = 'incident_history'
    id = db.Column(db.Integer, primary_key=True)
    user_id = db.Column(db.Integer, db.ForeignKey('users.id'))
    title = db.Column(db.String(200))
    description = db.Column(db.Text)
    latitude = db.Column(db.Numeric(9, 6))
    longitude = db.Column(db.Numeric(9, 6))
    created_at = db.Column(db.DateTime, default=datetime.utcnow)
