import os
import psycopg2
from dotenv import load_dotenv

load_dotenv()

def test_conn():
    url = os.getenv('DATABASE_URL')
    print(f"Testing connection to: {url}")
    try:
        conn = psycopg2.connect(url)
        print("Connection successful!")
        conn.close()
    except Exception as e:
        print(f"Connection failed: {e}")

if __name__ == '__main__':
    test_conn()
