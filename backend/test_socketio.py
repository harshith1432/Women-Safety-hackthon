import sys
sys.stdout.reconfigure(line_buffering=True)
print("Testing SocketIO imports...")

try:
    import engineio
    print("engineio imported")
except Exception as e: print(f"engineio error: {e}")

try:
    import socketio
    print("socketio (python-socketio) imported")
except Exception as e: print(f"socketio error: {e}")

try:
    from flask_socketio import SocketIO
    print("flask_socketio imported")
except Exception as e: print(f"flask_socketio error: {e}")
