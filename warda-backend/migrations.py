#!/usr/bin/env python3
"""
Database migration script for WARDA Therapist application.
This script creates the necessary tables in the MySQL database.
"""

import os
import sys
import logging
from sqlalchemy import create_engine, text
from dotenv import load_dotenv

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# Load environment variables
load_dotenv()

# Get database connection details
DB_USER = os.getenv("DB_USER", "root")
DB_PASSWORD = os.getenv("DB_PASSWORD", "")
DB_HOST = os.getenv("DB_HOST", "localhost")
DB_PORT = os.getenv("DB_PORT", "3306")
DB_NAME = os.getenv("DB_NAME", "warda_therapist")

# Create the database URL
DATABASE_URL = f"mysql+pymysql://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}"

def create_database():
    """Create the database if it doesn't exist."""
    try:
        engine = create_engine(DATABASE_URL)
        with engine.connect() as conn:
            conn.execute(text(f"CREATE DATABASE IF NOT EXISTS {DB_NAME}"))
            logger.info(f"Database '{DB_NAME}' created or already exists")
    except Exception as e:
        logger.error(f"Error creating database: {str(e)}")
        sys.exit(1)

def create_tables():
    """Create the tables if they don't exist."""
    try:
        # Connect to the database
        engine = create_engine(f"{DATABASE_URL}/{DB_NAME}")
        
        # Create Users table
        with engine.connect() as conn:
            conn.execute(text("""
            CREATE TABLE IF NOT EXISTS users (
                id INT AUTO_INCREMENT PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                email VARCHAR(255) NOT NULL UNIQUE,
                phone VARCHAR(20) NOT NULL,
                password VARCHAR(255) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """))
            logger.info("Users table created or already exists")
            
            # Create Chat Messages table
            conn.execute(text("""
            CREATE TABLE IF NOT EXISTS chat_messages (
                id INT AUTO_INCREMENT PRIMARY KEY,
                user_id INT NOT NULL,
                message TEXT NOT NULL,
                response TEXT NOT NULL,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                INDEX user_idx (user_id)
            )
            """))
            logger.info("Chat Messages table created or already exists")
            
    except Exception as e:
        logger.error(f"Error creating tables: {str(e)}")
        sys.exit(1)

def main():
    """Main function to run the migration."""
    logger.info("Starting database migration")
    
    # Create the database
    create_database()
    
    # Create the tables
    create_tables()
    
    logger.info("Database migration completed successfully")

if __name__ == "__main__":
    main()