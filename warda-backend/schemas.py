"""
Pydantic models for request/response validation
"""
from pydantic import BaseModel, EmailStr, Field
from typing import Optional, List, Dict, Any
from datetime import datetime

# User schemas
class UserBase(BaseModel):
    name: str
    email: str
    phone: str

class UserCreate(UserBase):
    password: str

class UserLogin(BaseModel):
    email: str
    password: str

class UserPasswordReset(BaseModel):
    email: str
    new_password: str

class UserResponse(UserBase):
    id: int
    created_at: datetime
    
    class Config:
        orm_mode = True

# Chat schemas
class ChatMessageCreate(BaseModel):
    user_id: int
    message: str

class ChatMessageResponse(BaseModel):
    id: int
    user_id: int
    message: str
    response: str
    timestamp: datetime
    emotional_state: Optional[str] = None
    confidence: Optional[float] = None
    
    class Config:
        orm_mode = True

# Emotional state schemas
class EmotionalState(BaseModel):
    state: str
    confidence: float
    trend: str
    color_code: str
    description: str

class EmotionalHistoryItem(BaseModel):
    timestamp: str
    emotion: str
    confidence: float
    message: Optional[str] = None

class EmotionalStateWithHistory(EmotionalState):
    history: List[EmotionalHistoryItem]

class TimelineItem(BaseModel):
    timestamp: str
    emotion: str
    confidence: float
    color: str
    value: int
    short_message: Optional[str] = None

class TimelineSummary(BaseModel):
    state: str
    trend: str
    color: str
    description: str

class EmotionalTimeline(BaseModel):
    timeline: List[TimelineItem]
    summary: TimelineSummary

class Recommendation(BaseModel):
    current_state: Dict[str, Any]
    recommendations: List[str]

class InsightItem(BaseModel):
    type: str
    title: str
    description: str

class EmotionalInsights(BaseModel):
    total_interactions: int
    emotion_distribution: Dict[str, float]
    insights: List[InsightItem]
    patterns: Dict[str, Any]