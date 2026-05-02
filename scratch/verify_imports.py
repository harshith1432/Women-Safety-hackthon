import os
import sys

# Add sheshield_deploy to path
DEPLOY_PATH = os.path.abspath(os.path.join(os.getcwd(), 'sheshield_deploy'))
if DEPLOY_PATH not in sys.path:
    sys.path.append(DEPLOY_PATH)

print(f"Checking {DEPLOY_PATH}...")

try:
    from inference import predict_from_coords, nearest_ward
    print("Inference imports: SUCCESS")
except ImportError as e:
    print(f"Inference imports: FAILED - {e}")

try:
    from risk_engine_v2 import SheShieldRiskEngine
    print("Risk Engine imports: SUCCESS")
except ImportError as e:
    print(f"Risk Engine imports: FAILED - {e}")
except Exception as e:
    print(f"Risk Engine error: {e}")
