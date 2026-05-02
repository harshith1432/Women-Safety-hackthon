import os
import psycopg2
from psycopg2.extras import RealDictCursor
from dotenv import load_dotenv

load_dotenv(os.path.join(os.path.dirname(__file__), '../.env'))

class DBManager:
    def __init__(self):
        self.db_url = os.getenv('DATABASE_URL')
    
    def get_conn(self):
        return psycopg2.connect(self.db_url)

    def execute_query(self, query, params=None, fetch=False):
        conn = self.get_conn()
        cur = conn.cursor(cursor_factory=RealDictCursor)
        try:
            cur.execute(query, params)
            if fetch:
                result = cur.fetchall()
            else:
                conn.commit()
                result = True
        except Exception as e:
            print(f"Database Error: {e}")
            conn.rollback()
            result = None
        finally:
            cur.close()
            conn.close()
        return result

    def execute_one(self, query, params=None):
        conn = self.get_conn()
        cur = conn.cursor(cursor_factory=RealDictCursor)
        try:
            cur.execute(query, params)
            result = cur.fetchone()
        except Exception as e:
            print(f"Database Error: {e}")
            result = None
        finally:
            cur.close()
            conn.close()
        return result

db_manager = DBManager()
