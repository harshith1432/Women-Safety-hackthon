import os
from flask import Flask
from dotenv import load_dotenv
from models import db, User, TrustedContact, Alert, Location, UnsafeZone, DeviceActivityLog, PoliceResponseLog, IncidentHistory

# Load environment variables
load_dotenv()

app = Flask(__name__)
app.config['SQLALCHEMY_DATABASE_URI'] = os.getenv('DATABASE_URL')
app.config['SQLALCHEMY_TRACK_MODIFICATIONS'] = False

db.init_app(app)

def init_db():
    with app.app_context():
        print(f"Initializing database at: {os.getenv('DATABASE_URL')}")
        # db.drop_all() # Careful with this in production
        db.create_all()
        print("Database tables created/updated successfully!")

if __name__ == '__main__':
    init_db()
