"""
Authentication routes for WARDA Therapist API
"""
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import Dict, Any
import hashlib
import logging

from database import get_db, User
import schemas

# Configure logging
logger = logging.getLogger(__name__)

# Create router
router = APIRouter()

# Hash password function
def hash_password(password: str) -> str:
    """Hash password using SHA-256"""
    return hashlib.sha256(password.encode()).hexdigest()

@router.post("/register", response_model=Dict[str, Any])
async def register_user(user: schemas.UserCreate, db: Session = Depends(get_db)):
    """
    Register a new user
    """
    logger.info(f"Registering new user: {user.email}")
    
    try:
        # Check if email already exists
        db_user = db.query(User).filter(User.email == user.email).first()
        if db_user:
            logger.warning(f"Registration failed: Email {user.email} already registered")
            raise HTTPException(status_code=400, detail="Email already registered")
        
        # Create new user with hashed password
        hashed_password = hash_password(user.password)
        new_user = User(
            name=user.name,
            email=user.email,
            phone=user.phone,
            password=hashed_password
        )
        
        db.add(new_user)
        db.commit()
        db.refresh(new_user)
        
        logger.info(f"User registered successfully: {user.email}, ID: {new_user.id}")
        
        return {
            "status": "success", 
            "message": "User registered successfully", 
            "user_id": new_user.id,
            "name": new_user.name,
            "email": new_user.email
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error registering user: {str(e)}")
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/login", response_model=Dict[str, Any])
async def login_user(user_data: schemas.UserLogin, db: Session = Depends(get_db)):
    """
    Login a user
    """
    logger.info(f"Login attempt: {user_data.email}")
    
    try:
        # Hash the provided password
        hashed_password = hash_password(user_data.password)
        
        # Find the user
        user = db.query(User).filter(
            User.email == user_data.email,
            User.password == hashed_password
        ).first()
        
        if not user:
            logger.warning(f"Login failed: Invalid credentials for {user_data.email}")
            raise HTTPException(
                status_code=status.HTTP_401_UNAUTHORIZED,
                detail="Invalid email or password"
            )
        
        logger.info(f"Login successful: {user_data.email}, ID: {user.id}")
        
        return {
            "status": "success",
            "message": "Login successful",
            "user_id": user.id,
            "name": user.name,
            "email": user.email
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error logging in user: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/reset-password", response_model=Dict[str, Any])
async def reset_password(reset_data: schemas.UserPasswordReset, db: Session = Depends(get_db)):
    """
    Reset user password
    """
    logger.info(f"Password reset attempt for: {reset_data.email}")
    
    try:
        # Find the user by email
        user = db.query(User).filter(User.email == reset_data.email).first()
        if not user:
            logger.warning(f"Password reset failed: User not found for {reset_data.email}")
            raise HTTPException(status_code=404, detail="User not found")
        
        # Update the password
        hashed_password = hash_password(reset_data.new_password)
        user.password = hashed_password
        db.commit()
        
        logger.info(f"Password reset successful for: {reset_data.email}")
        
        return {
            "status": "success", 
            "message": "Password reset successfully"
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error resetting password: {str(e)}")
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/profile/{user_id}", response_model=schemas.UserResponse)
async def get_user_profile(user_id: int, db: Session = Depends(get_db)):
    """
    Get user profile
    """
    try:
        # Find user
        user = db.query(User).filter(User.id == user_id).first()
        if not user:
            raise HTTPException(status_code=404, detail="User not found")
        
        return user
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error getting user profile: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@router.put("/profile/{user_id}", response_model=schemas.UserResponse)
async def update_user_profile(user_id: int, user_data: schemas.UserBase, db: Session = Depends(get_db)):
    """
    Update user profile
    """
    try:
        # Find user
        user = db.query(User).filter(User.id == user_id).first()
        if not user:
            raise HTTPException(status_code=404, detail="User not found")
        
        # Update user data
        user.name = user_data.name
        user.email = user_data.email
        user.phone = user_data.phone
        
        db.commit()
        db.refresh(user)
        
        return user
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error updating user profile: {str(e)}")
        db.rollback()
        raise HTTPException(status_code=500, detail=str(e))