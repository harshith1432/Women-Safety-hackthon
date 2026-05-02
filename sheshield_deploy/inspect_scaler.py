import joblib
import numpy as np
import os

model_dir = r"d:\projects softwares\100 DAYS PROGRAM\EXTRA\sheshield ai\sheshield_deploy\models"
scaler_path = os.path.join(model_dir, "scaler.pkl")

try:
    scaler = joblib.load(scaler_path)
    print(f"Mean: {scaler.mean_[:30]}")
    print(f"Scale: {scaler.scale_[:30]}")
except Exception as e:
    print(f"Error: {e}")
