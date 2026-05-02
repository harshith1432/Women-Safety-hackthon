import os
from flask import Flask
from flask_sqlalchemy import SQLAlchemy
from flask_jwt_extended import JWTManager
from flask_cors import CORS
from flask_socketio import SocketIO
from dotenv import load_dotenv

print("All imports successful!")
load_dotenv("../.env")
print(f"DATABASE_URL: {os.getenv('DATABASE_URL')}")
