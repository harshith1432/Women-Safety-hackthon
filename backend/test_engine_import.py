import os
import sys

# Add sheshield_deploy to path
deploy_path = os.path.abspath(os.path.join(os.path.dirname(__file__), '../sheshield_deploy'))
sys.path.append(deploy_path)

try:
    from risk_engine_v2 import SheShieldRiskEngine
    print("Success: SheShieldRiskEngine imported")
    engine = SheShieldRiskEngine()
    print("Success: SheShieldRiskEngine initialized")
    
    # Test assessment
    sensor_data = {
        "night_travel": True,
        "road_type": "small",
        "unsafe_zone": False,
        "battery": 45,
        "shake": False,
        "voice_trigger": False,
        "sudden_stop": False,
        "manual_sos": False
    }
    location = {"lat": 12.9774, "lon": 77.5722}
    result = engine.assess(sensor_data, location)
    print(f"Test Result: {result['final_score']} ({result['risk_level']})")
    
except Exception as e:
    print(f"Error: {e}")
    import traceback
    traceback.print_exc()
