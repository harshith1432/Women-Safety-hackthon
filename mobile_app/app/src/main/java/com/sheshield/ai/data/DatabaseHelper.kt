package com.sheshield.ai.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, "SheShield.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase?) {
        db?.execSQL("""
            CREATE TABLE IF NOT EXISTS offline_alerts (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                risk_score INTEGER,
                status TEXT,
                latitude REAL,
                longitude REAL,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                is_synced INTEGER DEFAULT 0
            )
        """)
        
        db?.execSQL("""
            CREATE TABLE IF NOT EXISTS offline_locations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                latitude REAL,
                longitude REAL,
                timestamp DATETIME DEFAULT CURRENT_TIMESTAMP,
                is_synced INTEGER DEFAULT 0
            )
        """)
    }

    override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
        db?.execSQL("DROP TABLE IF EXISTS offline_alerts")
        db?.execSQL("DROP TABLE IF EXISTS offline_locations")
        onCreate(db)
    }
}
