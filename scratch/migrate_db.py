import os
import psycopg2
from dotenv import load_dotenv

load_dotenv()

DATABASE_URL = os.getenv("DATABASE_URL")

def migrate():
    try:
        conn = psycopg2.connect(DATABASE_URL)
        cur = conn.cursor()
        
        print("Starting database migration...")
        
        # 1. Update 'users' table
        print("Updating 'users' table...")
        
        # Rename columns if they exist with old names
        cur.execute("""
            DO $$ 
            BEGIN 
                IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='name') THEN
                    ALTER TABLE users RENAME COLUMN name TO full_name;
                END IF;
                IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='phone') THEN
                    ALTER TABLE users RENAME COLUMN phone TO phone_number;
                END IF;
                IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='users' AND column_name='address') THEN
                    ALTER TABLE users RENAME COLUMN address TO home_address;
                END IF;
            END $$;
        """)
        
        # Add missing columns
        cur.execute("""
            ALTER TABLE users 
            ADD COLUMN IF NOT EXISTS guardian_details TEXT,
            ADD COLUMN IF NOT EXISTS emergency_contacts TEXT,
            ADD COLUMN IF NOT EXISTS device_status VARCHAR(20) DEFAULT 'OFFLINE',
            ADD COLUMN IF NOT EXISTS speed FLOAT DEFAULT 0.0,
            ADD COLUMN IF NOT EXISTS movement_status VARCHAR(20) DEFAULT 'STATIONARY';
        """)
        
        # 2. Update 'alerts' table
        print("Updating 'alerts' table...")
        cur.execute("""
            ALTER TABLE alerts 
            ADD COLUMN IF NOT EXISTS latitude NUMERIC(9,6),
            ADD COLUMN IF NOT EXISTS longitude NUMERIC(9,6),
            ADD COLUMN IF NOT EXISTS officer_assigned VARCHAR(100);
        """)
        
        # 3. Update 'locations' table
        print("Updating 'locations' table...")
        cur.execute("""
            DO $$ 
            BEGIN 
                IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='locations' AND column_name='timestamp') THEN
                    ALTER TABLE locations RENAME COLUMN timestamp TO created_at;
                END IF;
            END $$;
        """)
        
        cur.execute("""
            ALTER TABLE locations 
            ADD COLUMN IF NOT EXISTS speed FLOAT DEFAULT 0.0,
            ADD COLUMN IF NOT EXISTS movement_status VARCHAR(20) DEFAULT 'STATIONARY',
            ADD COLUMN IF NOT EXISTS risk_score INTEGER DEFAULT 0;
        """)
        
        # 4. Update 'unsafe_zones' table (adding created_at if missing)
        print("Updating 'unsafe_zones' table...")
        cur.execute("""
            ALTER TABLE unsafe_zones 
            ADD COLUMN IF NOT EXISTS created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP;
        """)

        conn.commit()
        print("Migration completed successfully!")
        
        cur.close()
        conn.close()
    except Exception as e:
        print(f"Migration Error: {e}")

if __name__ == "__main__":
    migrate()
