"""
SheShield AI - FastAPI Server (no DB version, demo-ready).
Run with: uvicorn api:app --host 0.0.0.0 --port 8000
"""
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import Optional
from datetime import datetime
import uuid

from inference import predict_risk, predict_from_coords, WARD_CENTROIDS

app = FastAPI(title="SheShield AI Risk API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"]
)

# In-memory alert log (good enough for hackathon demo)
ALERT_LOG = []
RISK_LOG = []


class CoordRiskRequest(BaseModel):
    user_id: Optional[str] = "demo_user"
    lat: float = Field(..., ge=-90, le=90)
    lon: float = Field(..., ge=-180, le=180)
    hour: Optional[int] = None
    day_of_week: Optional[int] = None
    month: Optional[int] = None
    location_type: str = "ROAD"
    crime_category: str = "ASSAULT_ON_WOMEN"


class WardRiskRequest(BaseModel):
    hour: int = Field(..., ge=0, le=23)
    day_of_week: int = Field(..., ge=0, le=6)
    month: int = Field(..., ge=1, le=12)
    india_location_type: str
    ncrb_crime_category: str
    ward: str


class EmergencyAlert(BaseModel):
    user_id: str = "demo_user"
    lat: float
    lon: float
    risk_score: float
    risk_level: str
    triggered_by: str = "auto_ai"


@app.get("/")
def root():
    return {
        "app": "SheShield AI",
        "status": "online",
        "endpoints": ["/predict_risk", "/predict_by_coords",
                      "/emergency_alert", "/wards", "/active_alerts", "/health"]
    }


@app.get("/health")
def health():
    return {"status": "healthy", "model_loaded": True,
            "alerts_count": len(ALERT_LOG)}


@app.post("/predict_risk")
def predict_risk_endpoint(req: WardRiskRequest):
    try:
        return predict_risk(**req.dict())
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/predict_by_coords")
def predict_by_coords_endpoint(req: CoordRiskRequest):
    try:
        now = datetime.now()
        hour  = req.hour if req.hour is not None else now.hour
        dow   = req.day_of_week if req.day_of_week is not None else now.weekday()
        month = req.month if req.month is not None else now.month

        result = predict_from_coords(
            lat=req.lat, lon=req.lon,
            hour=hour, day_of_week=dow, month=month,
            crime_category=req.crime_category,
            location_type=req.location_type
        )

        RISK_LOG.append({
            "id": str(uuid.uuid4()),
            "user_id": req.user_id,
            "lat": req.lat, "lon": req.lon,
            "ward": result['ward'],
            "risk_class": result['risk_class'],
            "risk_score": result['risk_score'],
            "created_at": datetime.now().isoformat()
        })
        if len(RISK_LOG) > 1000:
            RISK_LOG.pop(0)

        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.post("/emergency_alert")
def emergency_alert_endpoint(alert: EmergencyAlert):
    alert_id = str(uuid.uuid4())
    ALERT_LOG.append({
        "id": alert_id,
        "user_id": alert.user_id,
        "lat": alert.lat, "lon": alert.lon,
        "risk_score": alert.risk_score,
        "risk_level": alert.risk_level,
        "triggered_by": alert.triggered_by,
        "status": "ACTIVE",
        "created_at": datetime.now().isoformat()
    })
    return {"alert_id": alert_id, "status": "ACTIVE",
            "message": "Police + emergency contacts notified"}


@app.get("/active_alerts")
def get_active_alerts():
    active = [a for a in ALERT_LOG if a['status'] == 'ACTIVE']
    return {"count": len(active), "alerts": active}


@app.get("/risk_history")
def get_risk_history(user_id: str = "demo_user", limit: int = 50):
    history = [r for r in RISK_LOG if r['user_id'] == user_id]
    return {"count": len(history), "history": history[-limit:]}


@app.get("/wards")
def list_wards():
    return {"wards": list(WARD_CENTROIDS.keys()), "centroids": WARD_CENTROIDS}

# ============================================================
# HYBRID ENDPOINT — uses BOTH rules + ML
# ============================================================
from risk_engine_v2 import SheShieldRiskEngine, log_assessment

hybrid_engine = SheShieldRiskEngine()


class HybridRiskRequest(BaseModel):
    user_id: Optional[str] = "demo_user"
    # Location
    lat: float = Field(..., ge=-90, le=90)
    lon: float = Field(..., ge=-180, le=180)
    # Rule-based phone signals
    night_travel:   bool = False
    road_type:      str = "main"      # highway / main / small
    unsafe_zone:    bool = False
    battery:        int = 80           # 0-100
    shake:          bool = False
    voice_trigger:  bool = False
    sudden_stop:    bool = False
    manual_sos:     bool = False
    # Optional ML context
    crime_category: str = "ASSAULT_ON_WOMEN"
    location_type:  str = "ROAD"


@app.post("/assess")
def hybrid_assess(req: HybridRiskRequest):
    """
    MAIN ENDPOINT for the Android app.
    Combines rule-based real-time signals with ML historical patterns.
    """
    try:
        sensor_data = {
            "night_travel": req.night_travel,
            "road_type":    req.road_type,
            "unsafe_zone":  req.unsafe_zone,
            "battery":      req.battery,
            "shake":        req.shake,
            "voice_trigger": req.voice_trigger,
            "sudden_stop":  req.sudden_stop,
            "manual_sos":   req.manual_sos,
        }
        location = {"lat": req.lat, "lon": req.lon}

        result = hybrid_engine.assess(
            sensor_data, location,
            crime_category=req.crime_category,
            location_type=req.location_type
        )
        log_assessment(sensor_data, location, result)

        # Auto-trigger emergency alert if needed
        if result["auto_alert"]:
            alert_id = str(uuid.uuid4())
            ALERT_LOG.append({
                "id": alert_id,
                "user_id": req.user_id,
                "lat": req.lat, "lon": req.lon,
                "risk_score": result["final_score"],
                "risk_level": result["risk_level"],
                "triggered_by": ("manual_sos" if req.manual_sos else
                                  "voice" if req.voice_trigger else
                                  "shake" if req.shake else "auto_ai"),
                "status": "ACTIVE",
                "created_at": datetime.now().isoformat()
            })
            result["alert_id"] = alert_id

        return result
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
