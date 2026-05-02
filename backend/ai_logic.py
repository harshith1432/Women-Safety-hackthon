import math

class SheShieldAI:
    def __init__(self):
        # Sample unsafe zones: (lat, lon, risk_weight)
        self.unsafe_zones = [
            (28.6139, 77.2090, 80),  # Example zone
        ]

    def rule_based_risk_score(self, data):
        """
        Calculates risk score (0-100) based on multiple factors.
        """
        score = 0
        
        # 1. Time factor (Night travel increases risk)
        hour = data.get('hour', 12)
        if hour < 6 or hour > 20:
            score += 20
            
        # 2. Battery factor
        battery = data.get('battery', 100)
        if battery < 20:
            score += 15
        elif battery < 40:
            score += 10
            
        # 3. Sudden Motion / Shake
        if data.get('shake_detected'):
            score += 50
            
        # 4. Voice Trigger
        if data.get('voice_trigger'):
            score += 60
            
        # 5. Route Deviation
        if data.get('route_deviation'):
            score += 25
            
        # 6. Manual SOS
        if data.get('manual_sos'):
            score = 100
            
        return min(score, 100)

    def predict_danger_escalation(self, sequence_data):
        """
        Random Forest Classifier placeholder.
        Predicts if a situation is likely to escalate based on movement patterns.
        """
        # In a real app, you'd load a joblib model here
        # For now, return a mock probability
        return 0.25  # 25% chance of escalation

    def get_safest_route(self, start, end):
        """
        Dijkstra's Algorithm placeholder for Safest Route.
        Instead of shortest distance, we weight edges by risk score.
        """
        # This would integrate with a Graph API like OSMnx or Google Maps
        return {
            "path": [start, (start[0]+0.01, start[1]+0.01), end],
            "risk_level": "Low"
        }

    def classify_risk(self, score):
        if score < 25: return "SAFE SECURE"
        if score < 50: return "WARNING"
        if score < 80: return "HIGH RISK"
        return "EMERGENCY"

ai_engine = SheShieldAI()
