"""
SheShield AI - Inference Module (cross-platform).
"""
import os
import json
import joblib
import numpy as np
import pandas as pd

# Auto-detect models folder location (works on Linux, Mac, Windows)
MODEL_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "models")

_model = joblib.load(os.path.join(MODEL_DIR, "best_model.pkl"))
_scaler = joblib.load(os.path.join(MODEL_DIR, "scaler.pkl"))
_feature_cols = json.load(open(os.path.join(MODEL_DIR, "feature_columns.json")))
_threshold = json.load(open(os.path.join(MODEL_DIR, "emergency_threshold.json")))["emergency_threshold"]

LEVEL_NAMES = {0: "Safe", 1: "Warning", 2: "High", 3: "Emergency"}
LEVEL_COLORS = {0: "GREEN", 1: "YELLOW", 2: "ORANGE", 3: "RED"}

WARD_CENTROIDS = {
    "MG_Road":         (12.9750, 77.6050),
    "Majestic":        (12.9774, 77.5722),
    "Koramangala":     (12.9352, 77.6245),
    "Indiranagar":     (12.9716, 77.6412),
    "Whitefield":      (12.9698, 77.7500),
    "Electronic_City": (12.8452, 77.6602),
    "Jayanagar":       (12.9250, 77.5938),
    "BTM_Layout":      (12.9166, 77.6101),
    "Marathahalli":    (12.9591, 77.6974),
    "Hebbal":          (13.0359, 77.5970),
    "Yeshwanthpur":    (13.0280, 77.5400),
    "Banashankari":    (12.9250, 77.5667),
}


LOCATION_DEFAULTS = {
    "DARK_ALLEY": {"lighting": 0.1, "visibility": 0.2, "security": 0.1, "people_density": 0.1, "women_presence": 0.05, "feeling_of_safety": 0.2},
    "ABANDONED_BUILDING": {"lighting": 0.05, "visibility": 0.1, "security": 0.0, "people_density": 0.05, "women_presence": 0.0, "feeling_of_safety": 0.1},
    "BUS_STOP": {"lighting": 0.7, "visibility": 0.8, "security": 0.4, "people_density": 0.6, "women_presence": 0.4, "feeling_of_safety": 0.7},
    "METRO_STATION": {"lighting": 0.9, "visibility": 0.9, "security": 0.8, "people_density": 0.8, "women_presence": 0.5, "feeling_of_safety": 0.9},
    "RESIDENTIAL_AREA": {"lighting": 0.6, "visibility": 0.7, "security": 0.5, "people_density": 0.4, "women_presence": 0.6, "feeling_of_safety": 0.8},
    "ROAD": {"lighting": 0.5, "visibility": 0.8, "security": 0.3, "people_density": 0.3, "women_presence": 0.3, "feeling_of_safety": 0.6},
    "PARK": {"lighting": 0.3, "visibility": 0.4, "security": 0.2, "people_density": 0.2, "women_presence": 0.2, "feeling_of_safety": 0.4},
}


def predict_risk(hour, day_of_week, month,
                 india_location_type, ncrb_crime_category, ward,
                 sensor_data=None):
    """
    Predict risk based on time, location, and mobile sensor data.
    Uses scaler-aware baselines to ensure model sensitivity.
    """
    # 1. Initialize with Scaler Means (The "Neutral" point for the model)
    row = {col: float(_scaler.mean_[i]) for i, col in enumerate(_feature_cols)}

    # 2. Map Location Defaults (Scale relative to means)
    loc_key = india_location_type.upper()
    if loc_key in LOCATION_DEFAULTS:
        for feat, val in LOCATION_DEFAULTS[loc_key].items():
            if feat in row:
                row[feat] = row[feat] * (val / 0.5)
    
    # 3. Map Time Features
    row['hour_sin']  = np.sin(2 * np.pi * hour / 24)
    row['hour_cos']  = np.cos(2 * np.pi * hour / 24)
    row['dow_sin']   = np.sin(2 * np.pi * day_of_week / 7)
    row['dow_cos']   = np.cos(2 * np.pi * day_of_week / 7)
    row['month_sin'] = np.sin(2 * np.pi * month / 12)
    row['month_cos'] = np.cos(2 * np.pi * month / 12)
    row['is_night']      = 1.0 if (hour >= 20 or hour <= 5) else 0.0
    row['is_late_night'] = 1.0 if (hour >= 23 or hour <= 4) else 0.0
    row['is_weekend']    = 1.0 if day_of_week >= 5 else 0.0

    # 4. Map Sensor Data to ML Features
    if sensor_data:
        # Emergency Triggers
        if sensor_data.get("manual_sos") or sensor_data.get("shake") or sensor_data.get("voice_trigger"):
            for feat in ['emergency_cases', 'severe_crime_ratio', 'total_reports', 'police_incident_count']:
                if feat in _feature_cols:
                    idx = _feature_cols.index(feat)
                    row[feat] = _scaler.mean_[idx] + 5 * _scaler.scale_[idx]
            
            if 'feeling_of_safety' in _feature_cols:
                row['feeling_of_safety'] = _scaler.mean_[_feature_cols.index('feeling_of_safety')] * 0.1
            if 'security' in _feature_cols:
                row['security'] = _scaler.mean_[_feature_cols.index('security')] * 0.1

        # Unsafe Zone (Hotspots)
        if sensor_data.get("unsafe_zone") or sensor_data.get("in_unsafe_zone"):
            if 'in_hotspot_zone' in _feature_cols:
                row['in_hotspot_zone'] = 1.0
            if 'unsafe_zone_score' in _feature_cols:
                idx = _feature_cols.index('unsafe_zone_score')
                row['unsafe_zone_score'] = _scaler.mean_[idx] + 3 * _scaler.scale_[idx]
            if 'nearest_hotspot_dist_m' in _feature_cols:
                row['nearest_hotspot_dist_m'] = 5.0

        battery = sensor_data.get("battery", 100)
        if battery < 20:
            if 'feeling_of_safety' in _feature_cols: row['feeling_of_safety'] *= 0.7
            if 'security' in _feature_cols: row['security'] *= 0.7

    for key in [f"loc_{india_location_type.upper()}",
                f"crime_{ncrb_crime_category.upper()}",
                f"ward_{ward}"]:
        if key in row:
            row[key] = 1.0

    X = pd.DataFrame([row])[_feature_cols].values.astype(np.float64)
    X_scaled = _scaler.transform(X)
    
    proba = _model.predict_proba(X_scaled)[0]
    
    if len(proba) < 4:
        full = np.zeros(4)
        for i, c in enumerate(_model.classes_):
            if int(c) < 4:
                full[int(c)] = proba[i]
        proba = full
        
    pred = int(np.argmax(proba))
    if proba[3] > _threshold:
        pred = 3
    elif proba[2] > 0.4:
        pred = 2
        
    base_ml_score = float(proba[0]*15 + proba[1]*40 + proba[2]*75 + proba[3]*98)
    
    sensor_boost = 0
    if sensor_data:
        if sensor_data.get("shake"): sensor_boost += 15
        if sensor_data.get("voice_trigger"): sensor_boost += 20
        if sensor_data.get("manual_sos"): sensor_boost += 30
    
    risk_score = min(100, base_ml_score + sensor_boost)
    
    return {
        "risk_class": pred,
        "risk_level": LEVEL_NAMES.get(pred, "High"),
        "color": LEVEL_COLORS.get(pred, "RED"),
        "risk_score": round(risk_score, 2),
        "probabilities": {
            "safe":      round(float(proba[0]), 4),
            "warning":   round(float(proba[1]), 4),
            "high":      round(float(proba[2]), 4),
            "emergency": round(float(proba[3]), 4)
        },
        "alert_needed": pred >= 2 or risk_score > 70,
        "auto_sos":     pred == 3 or risk_score > 90,
        "source": "SheShield AI v1.1 (Scaler-Aware)",
        "features_mapped": len([v for k, v in row.items() if v != _scaler.mean_[_feature_cols.index(k)]])
    }


def nearest_ward(lat, lon):
    """Find the nearest ward based on Euclidean distance to centroids."""
    min_dist = float('inf')
    best_ward = "MG_Road"
    for ward, (wlat, wlon) in WARD_CENTROIDS.items():
        dist = np.sqrt((lat - wlat)**2 + (lon - wlon)**2)
        if dist < min_dist:
            min_dist = dist
            best_ward = ward
    return best_ward


def predict_from_coords(lat, lon, hour, day_of_week, month,
                        crime_category="ASSAULT_ON_WOMEN",
                        location_type="ROAD",
                        sensor_data=None):
    ward = nearest_ward(lat, lon)
    result = predict_risk(hour, day_of_week, month,
                          location_type, crime_category, ward,
                          sensor_data=sensor_data)
    result["lat"] = lat
    result["lon"] = lon
    result["ward"] = ward
    return result
