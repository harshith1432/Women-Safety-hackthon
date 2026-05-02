print("Testing basic imports...")
try:
    import sqlalchemy
    print(f"SQLAlchemy {sqlalchemy.__version__} imported")
except Exception as e:
    print(f"SQLAlchemy import failed: {e}")

try:
    from flask_sqlalchemy import SQLAlchemy
    print("Flask-SQLAlchemy imported")
except Exception as e:
    print(f"Flask-SQLAlchemy import failed: {e}")

try:
    import eventlet
    print("Eventlet imported")
except ImportError:
    print("Eventlet not found")

try:
    import gevent
    print("Gevent imported")
except ImportError:
    print("Gevent not found")
