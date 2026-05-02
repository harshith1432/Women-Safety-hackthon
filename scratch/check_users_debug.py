import psycopg2
import os
from dotenv import load_dotenv
from psycopg2.extras import RealDictCursor

load_dotenv()
DATABASE_URL = os.getenv('DATABASE_URL')

def check_users():
    conn = psycopg2.connect(DATABASE_URL, cursor_factory=RealDictCursor)
    cur = conn.cursor()
    cur.execute("SELECT id, full_name, is_online FROM users LIMIT 10")
    users = cur.fetchall()
    print("USERS IN DB:")
    for u in users:
        print(u)
    
    cur.execute("""
        SELECT u.id, u.full_name as name, l.latitude, l.longitude
        FROM users u
        LEFT JOIN locations l ON u.id = l.user_id
        ORDER BY l.created_at DESC LIMIT 5
    """)
    locs = cur.fetchall()
    print("\nLATEST LOCATIONS:")
    for l in locs:
        print(l)
    
    conn.close()

if __name__ == "__main__":
    check_users()
