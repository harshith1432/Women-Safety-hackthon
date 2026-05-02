"""
SheShield AI — Hybrid Risk Engine (Rules + ML)
================================================
Combines:
  1. Rule-based real-time triggers (immediate, deterministic)
  2. ML model (Random Forest/XGBoost) historical pattern validation

Flow:
  phone_data → rule_score → ML validation → final_score → classification → action
"""

import os
import pandas as pd
from datetime import datetime

# Import the trained ML model
from inference import predict_from_coords, nearest_ward


# ============================================================
# RULE-BASED ENGINE (your original, slightly cleaned)
# ============================================================
class RuleEngine:
    """Deterministic rule scoring — handles immediate user triggers."""

    # Rule weights (matches your system design)
    WEIGHTS = {
        "night_travel":  40,
        "small_road":    25,
        "unsafe_zone":   30,
        "battery_low":   10,
        "shake":         80,
        "voice_trigger": 25,  # Reduced from 95 to allow for increments
        "sudden_stop":   15,
        "manual_sos":    100,
    }

    def calculate(self, data: dict) -> tuple[int, dict]:
        """Returns (score, breakdown) so we can explain decisions."""
        score = 0
        breakdown = {}

        if data.get("night_travel"):
            score += self.WEIGHTS["night_travel"]
            breakdown["night_travel"] = self.WEIGHTS["night_travel"]

        if data.get("road_type", "").lower() == "small":
            score += self.WEIGHTS["small_road"]
            breakdown["small_road"] = self.WEIGHTS["small_road"]

        if data.get("unsafe_zone"):
            score += self.WEIGHTS["unsafe_zone"]
            breakdown["unsafe_zone"] = self.WEIGHTS["unsafe_zone"]

        if data.get("battery", 100) < 20:
            score += self.WEIGHTS["battery_low"]
            breakdown["battery_low"] = self.WEIGHTS["battery_low"]

        if data.get("shake"):
            score += self.WEIGHTS["shake"]
            breakdown["shake"] = self.WEIGHTS["shake"]

        if data.get("voice_trigger"):
            score += self.WEIGHTS["voice_trigger"]
            breakdown["voice_trigger"] = self.WEIGHTS["voice_trigger"]

        if data.get("sudden_stop"):
            score += self.WEIGHTS["sudden_stop"]
            breakdown["sudden_stop"] = self.WEIGHTS["sudden_stop"]

        if data.get("manual_sos"):
            score += self.WEIGHTS["manual_sos"]
            breakdown["manual_sos"] = self.WEIGHTS["manual_sos"]

        return min(score, 100), breakdown

    def has_explicit_trigger(self, data: dict) -> bool:
        """User intentionally triggered alarm — ML cannot override these."""
        return any([
            data.get("manual_sos"),
            data.get("shake"),
            data.get("voice_trigger")
        ])


# ============================================================
# ML VALIDATOR (uses the trained Random Forest/XGBoost)
# ============================================================
class MLValidator:
    """Wraps the trained model to validate rule-based score."""

    def get_ml_score(self, lat: float, lon: float,
                     hour: int = None, day_of_week: int = None,
                     month: int = None,
                     crime_category: str = "ASSAULT_ON_WOMEN",
                     location_type: str = "ROAD",
                     sensor_data: dict = None) -> dict:
        """Get historical-pattern-based risk from ML model."""
        now = datetime.now()
        hour = hour if hour is not None else now.hour
        dow = day_of_week if day_of_week is not None else now.weekday()
        month = month if month is not None else now.month

        return predict_from_coords(
            lat=lat, lon=lon,
            hour=hour, day_of_week=dow, month=month,
            crime_category=crime_category,
            location_type=location_type,
            sensor_data=sensor_data
        )


# ============================================================
# HYBRID RISK ENGINE — THE COMBINER
# ============================================================
class SheShieldRiskEngine:
    """
    Final risk engine — combines rules + ML.

    Logic:
      - Explicit triggers (SOS/shake/voice) → rule dominates 90%
      - Otherwise → 60% rule + 40% ML (ML validates pattern)
      - ML can BOOST score if location is historically dangerous
      - ML can NEVER suppress an explicit user trigger
    """

    def __init__(self):
        self.rule_engine = RuleEngine()
        self.ml_validator = MLValidator()

    def classify(self, score: float) -> dict:
        if score < 25:
            return {"level": "SAFE SECURE", "color": "GREEN", "emoji": "🟢", "class": 0}
        elif score < 50:
            return {"level": "WARNING", "color": "YELLOW", "emoji": "🟡", "class": 1}
        elif score < 80:
            return {"level": "HIGH RISK", "color": "ORANGE", "emoji": "🟠", "class": 2}
        else:
            return {"level": "EMERGENCY", "color": "RED", "emoji": "🔴", "class": 3}

    def assess(self, sensor_data: dict, location: dict,
               crime_category: str = "ASSAULT_ON_WOMEN",
               location_type: str = "ROAD") -> dict:
        """
        Full pipeline: rules → ML validation → final score → action.

        Args:
          sensor_data: dict with night_travel, road_type, unsafe_zone, 
                       battery, shake, voice_trigger, sudden_stop, manual_sos
          location: dict with lat, lon
        """
        # ===== STAGE 1: Rule-based score =====
        rule_score, rule_breakdown = self.rule_engine.calculate(sensor_data)
        explicit = self.rule_engine.has_explicit_trigger(sensor_data)

        # ===== STAGE 2: ML validation =====
        ml_result = self.ml_validator.get_ml_score(
            lat=location["lat"], lon=location["lon"],
            crime_category=crime_category,
            location_type=location_type,
            sensor_data=sensor_data
        )
        ml_score = ml_result["risk_score"]

        # ===== STAGE 3: Combine intelligently =====
        if explicit:
            # User explicitly triggered — rules dominate, ML can only BOOST
            final_score = rule_score
            if ml_score > rule_score:
                # Historical pattern says even worse — boost slightly
                final_score = min(100, rule_score + 0.2 * (ml_score - rule_score))
            blend_strategy = "explicit_trigger_rule_dominant"
        else:
            # No explicit trigger — blend 50% rule + 50% ML (Balance pattern with real-time settings)
            final_score = 0.5 * rule_score + 0.5 * ml_score

            # If ML strongly disagrees (high score but no rule triggers),
            # boost moderately because location-time pattern is dangerous
            if ml_score > 60 and rule_score < 20:
                final_score = max(final_score, 60)  # significantly boost to HIGH RISK level
            blend_strategy = "weighted_blend_50_50"

        final_score = round(min(100, max(0, final_score)), 2)
        classification = self.classify(final_score)

        # ===== STAGE 4: Action decision =====
        auto_alert = (final_score >= 80 or 
                      sensor_data.get("manual_sos") or 
                      sensor_data.get("shake") or
                      sensor_data.get("voice_trigger"))
        
        # If manual SOS or shake is active, ensure it hits at least Emergency level
        if sensor_data.get("manual_sos") or sensor_data.get("shake"):
            final_score = max(85, final_score)
            classification = self.classify(final_score)

        return {
            "final_score": final_score,
            "risk_level": classification["level"],
            "color": classification["color"],
            "risk_class": classification["class"],
            "auto_alert": auto_alert,
            "alert_needed": final_score >= 60,
            "components": {
                "rule_score": rule_score,
                "ml_score": ml_score,
                "ml_risk_level": ml_result["risk_level"]
            },
            "rule_breakdown": rule_breakdown,
            "explicit_trigger": explicit,
            "blend_strategy": blend_strategy,
            "location": {
                "lat": location["lat"],
                "lon": location["lon"],
                "ward": ml_result.get("ward")
            },
            "ml_probabilities": ml_result.get("probabilities"),
            "timestamp": datetime.now().isoformat(),
            "source": "SheShield AI v2.0 (Hybrid)"
        }

    def take_action(self, assessment: dict):
        """Trigger downstream actions based on assessment."""
        score = assessment["final_score"]
        loc = assessment["location"]

        print(f"\n📊 Final Score: {score}")
        print(f"🚦 Level: {assessment['color']} {assessment['risk_level']}")
        print(f"🧮 Rule: {assessment['components']['rule_score']} | "
              f"ML: {assessment['components']['ml_score']}")
        print(f"📍 Ward: {loc['ward']} ({loc['lat']:.4f}, {loc['lon']:.4f})")

        if assessment["auto_alert"]:
            print("\n🚨 EMERGENCY ALERT TRIGGERED!")
            print("📩 SMS via Android SmsManager...")
            print("📞 Auto-calling primary contact...")
            print("👮 Police dashboard notified...")
            print("📍 Live location streaming...")
        elif assessment["alert_needed"]:
            print("\n⚠️  Warning sent to guardian (no auto-alert yet).")
        else:
            print("\n✅ Monitoring — situation normal.")


# ============================================================
# DATA LOGGING (for continuous learning later)
# ============================================================
def log_assessment(sensor_data: dict, location: dict,
                   assessment: dict,
                   filename: str = "live_assessments.csv"):
    """Save every assessment for retraining + audit trail."""
    row = {
        **sensor_data,
        "lat": location["lat"],
        "lon": location["lon"],
        "rule_score": assessment["components"]["rule_score"],
        "ml_score": assessment["components"]["ml_score"],
        "final_score": assessment["final_score"],
        "risk_level": assessment["risk_level"],
        "ward": assessment["location"]["ward"],
        "timestamp": assessment["timestamp"]
    }
    df = pd.DataFrame([row])
    if os.path.exists(filename):
        df.to_csv(filename, mode='a', header=False, index=False)
    else:
        df.to_csv(filename, index=False)


# ============================================================
# CLI INTERFACE (for testing without the Android app)
# ============================================================
def get_boolean(prompt: str) -> bool:
    return input(prompt + " (yes/no): ").strip().lower() in ("yes", "y", "1")


def cli_input():
    print("\n📥 Enter Live Phone Data:\n")
    data = {
        "night_travel": get_boolean("Is it night travel?"),
        "road_type": input("Road type (highway/main/small): ").strip().lower() or "main",
        "unsafe_zone": get_boolean("Is it a flagged unsafe zone?"),
        "battery": int(input("Battery level (0-100): ") or "80"),
        "shake": get_boolean("Shake detected?"),
        "voice_trigger": get_boolean("Voice trigger activated?"),
        "sudden_stop": get_boolean("Sudden stop detected?"),
        "manual_sos": get_boolean("Manual SOS pressed?")
    }
    location = {
        "lat": float(input("Latitude (e.g. 12.9774): ") or "12.9774"),
        "lon": float(input("Longitude (e.g. 77.5722): ") or "77.5722")
    }
    return data, location


# ============================================================
# MAIN
# ============================================================
if __name__ == "__main__":
    print("=" * 60)
    print("🛡️  SHESHIELD AI — Hybrid Risk Engine v2.0")
    print("=" * 60)

    engine = SheShieldRiskEngine()
    sensor_data, location = cli_input()

    print("\n🔄 Running hybrid assessment (rules + ML)...\n")
    assessment = engine.assess(sensor_data, location)

    log_assessment(sensor_data, location, assessment)
    engine.take_action(assessment)

    print("\n📂 Saved to live_assessments.csv")
    print("=" * 60)
