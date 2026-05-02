import webbrowser
import os
import time

def launch():
    # Path to the web dashboard
    dashboard_path = os.path.abspath("../web_dashboard/index.html")
    
    print("--- SheShield AI Launcher ---")
    print("1. Backend API is running on http://127.0.0.1:5001")
    print("2. Opening Web Dashboard...")
    
    # Open the dashboard via HTTP instead of file:// to avoid WebSocket origin issues
    webbrowser.open("http://127.0.0.1:5001")
    
    print("\n[SUCCESS] Dashboard opened via HTTP. Stay safe!")

if __name__ == "__main__":
    launch()
