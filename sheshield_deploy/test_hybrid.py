"""Tests the hybrid engine with realistic scenarios."""
from risk_engine_v2 import SheShieldRiskEngine

engine = SheShieldRiskEngine()

scenarios = [
    {
        "name": "1. Worst case — SOS + night + small road in Majestic",
        "data": {"night_travel": True, "road_type": "small", "unsafe_zone": True,
                 "battery": 15, "shake": False, "voice_trigger": False,
                 "sudden_stop": False, "manual_sos": True},
        "loc":  {"lat": 12.9774, "lon": 77.5722}
    },
    {
        "name": "2. Silent danger — night, small road, NO triggers (ML must catch)",
        "data": {"night_travel": True, "road_type": "small", "unsafe_zone": True,
                 "battery": 80, "shake": False, "voice_trigger": False,
                 "sudden_stop": False, "manual_sos": False},
        "loc":  {"lat": 12.9774, "lon": 77.5722}
    },
    {
        "name": "3. Safe daytime in Jayanagar (family area)",
        "data": {"night_travel": False, "road_type": "main", "unsafe_zone": False,
                 "battery": 90, "shake": False, "voice_trigger": False,
                 "sudden_stop": False, "manual_sos": False},
        "loc":  {"lat": 12.9250, "lon": 77.5938}
    },
    {
        "name": "4. Voice trigger — should always escalate",
        "data": {"night_travel": False, "road_type": "main", "unsafe_zone": False,
                 "battery": 80, "shake": False, "voice_trigger": True,
                 "sudden_stop": False, "manual_sos": False},
        "loc":  {"lat": 12.9716, "lon": 77.6412}
    },
    {
        "name": "5. Sensor anomaly — sudden stop + shake at night",
        "data": {"night_travel": True, "road_type": "main", "unsafe_zone": False,
                 "battery": 50, "shake": True, "voice_trigger": False,
                 "sudden_stop": True, "manual_sos": False},
        "loc":  {"lat": 12.9698, "lon": 77.7500}
    },
]

for s in scenarios:
    print("\n" + "=" * 70)
    print(f"  {s['name']}")
    print("=" * 70)
    result = engine.assess(s['data'], s['loc'])
    print(f"  Rule:  {result['components']['rule_score']}")
    print(f"  ML:    {result['components']['ml_score']}")
    print(f"  Final: {result['final_score']}  ->  {result['color']} {result['risk_level']}")
    print(f"  ML Probs: {result.get('ml_probabilities') or result.get('probabilities')}")
    print(f"  Auto-alert: {'ALERT YES' if result['auto_alert'] else 'no'}")
    print(f"  Strategy: {result['blend_strategy']}")
