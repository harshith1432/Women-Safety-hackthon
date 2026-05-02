import os
import sys
import psycopg2
from flask import Flask, request, jsonify
from flask_cors import CORS
from dotenv import load_dotenv

load_dotenv(os.path.join(os.path.dirname(__file__), '../.env'))

app = Flask(__name__)
CORS(app)

print("--- STARTING SAFE MODE BACKEND (No SQLAlchemy) ---")

@app.route('/')
def index():
    return jsonify({"status": "running", "mode": "safe"})

@app.route('/test_db')
def test_db():
    try:
        conn = psycopg2.connect(os.getenv('DATABASE_URL'))
        cur = conn.cursor()
        cur.execute('SELECT 1')
        cur.close()
        conn.close()
        return jsonify({"status": "db_connected"})
    except Exception as e:
        return jsonify({"status": "db_error", "error": str(e)})

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
