import os
import sys
from app import app, db
from models import User

with app.app_context():
    users = User.query.all()
    print(f"Total Users: {len(users)}")
    for u in users:
        print(f"ID: {u.id}, Name: {u.full_name}, Email: {u.email}")
