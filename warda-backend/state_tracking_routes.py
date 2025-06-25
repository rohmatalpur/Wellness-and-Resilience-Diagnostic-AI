"""
State tracking routes for WARDA Therapist API
"""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import Dict, Any, List, Optional
from pydantic import BaseModel
from datetime import datetime, timedelta
import logging
import json

from database import get_db, User, ChatMessage
from enhanced_rag import analyze_message_emotion

# Configure logging
logger = logging.getLogger(__name__)

# Create router
router = APIRouter()

# Define response models
class EmotionalStateResponse(BaseModel):
    state: str
    confidence: float
    trend: str
    history: List[Dict[str, Any]]
    color_code: str
    description: str

class EmotionalTimelineResponse(BaseModel):
    timeline: List[Dict[str, Any]]
    summary: Dict[str, Any]

class RecommendationsResponse(BaseModel):
    current_state: Dict[str, Any]
    recommendations: List[str]

# Color mapping for emotional states
EMOTION_COLORS = {
    "distressed": "#FF3B30",  # Red
    "sad": "#FF9500",         # Orange
    "anxious": "#FFCC00",     # Yellow
    "angry": "#FF6347",       # Tomato
    "neutral": "#34C759",     # Green
    "content": "#30B0C7",     # Blue
    "hopeful": "#5856D6"      # Purple
}

# Description mapping for emotional states
EMOTION_DESCRIPTIONS = {
    "distressed": "You appear to be experiencing significant distress. It's important to be gentle with yourself during difficult times.",
    "sad": "You seem to be feeling down or sad. Remember that all emotions are valid and temporary.",
    "anxious": "Your messages suggest you may be feeling anxious or worried. This is a common response to uncertainty.",
    "angry": "You appear to be feeling frustrated or angry. These emotions often signal that something important to you is being affected.",
    "neutral": "Your emotional state appears balanced at the moment.",
    "content": "You seem to be in a positive emotional state. It's wonderful to recognize and appreciate these moments.",
    "hopeful": "Your messages reflect a sense of hope and optimism. This resilience is a powerful resource."
}

# Trend descriptions
TREND_DESCRIPTIONS = {
    "improving": "Your emotional wellbeing appears to be improving over recent conversations.",
    "stable": "Your emotional state has been relatively stable recently.",
    "declining": "There seems to be an increase in challenging emotions in recent conversations."
}

def track_emotional_state(user_id: int, db: Session):
    """
    Track the emotional state of a user over time
    
    Args:
        user_id (int): The user ID
        db (Session): Database session
        
    Returns:
        dict: Emotional state data
    """
    if not user_id:
        return {
            "state": "neutral",
            "confidence": 0.5,
            "trend": "stable",
            "history": []
        }
    
    try:
        # Get recent messages from the last 7 days
        seven_days_ago = datetime.now() - timedelta(days=7)
        recent_messages = db.query(ChatMessage).filter(
            ChatMessage.user_id == user_id,
            ChatMessage.timestamp >= seven_days_ago
        ).order_by(ChatMessage.timestamp.asc()).all()
        
        if not recent_messages:
            return {
                "state": "neutral",
                "confidence": 0.5,
                "trend": "stable",
                "history": []
            }
        
        # Analyze each message
        emotion_history = []
        for msg in recent_messages:
            emotion, confidence = analyze_message_emotion(msg.message)
            emotion_history.append({
                "timestamp": msg.timestamp.isoformat(),
                "emotion": emotion,
                "confidence": confidence,
                "message": msg.message[:50] + "..." if len(msg.message) > 50 else msg.message
            })
        
        # Determine current state (most recent emotion)
        current_state = emotion_history[-1]["emotion"] if emotion_history else "neutral"
        current_confidence = emotion_history[-1]["confidence"] if emotion_history else 0.5
        
        # Determine trend
        if len(emotion_history) >= 3:
            # Map emotions to numerical values for trend analysis
            emotion_values = {
                "distressed": -3,
                "sad": -2,
                "anxious": -1,
                "angry": -1,
                "neutral": 0,
                "content": 1,
                "hopeful": 2
            }
            
            # Get recent values
            recent_values = [emotion_values.get(e["emotion"], 0) for e in emotion_history[-3:]]
            
            if recent_values[2] > recent_values[0]:
                trend = "improving"
            elif recent_values[2] < recent_values[0]:
                trend = "declining"
            else:
                trend = "stable"
        else:
            trend = "stable"
        
        return {
            "state": current_state,
            "confidence": current_confidence,
            "trend": trend,
            "history": emotion_history
        }
        
    except Exception as e:
        logger.error(f"Error tracking emotional state: {str(e)}")
        return {
            "state": "neutral",
            "confidence": 0.5,
            "trend": "stable",
            "history": []
        }

@router.get("/current/{user_id}", response_model=EmotionalStateResponse)
async def get_current_emotional_state(user_id: int, db: Session = Depends(get_db)):
    """
    Get the current emotional state of a user
    """
    try:
        # Check if user exists
        user = db.query(User).filter(User.id == user_id).first()
        if not user:
            raise HTTPException(status_code=404, detail="User not found")
        
        # Get emotional state data
        user_state = track_emotional_state(user_id, db)
        
        # Add color code
        emotion = user_state["state"]
        color_code = EMOTION_COLORS.get(emotion, "#34C759")  # Default to green if not found
        
        # Create description
        emotion_desc = EMOTION_DESCRIPTIONS.get(emotion, "")
        trend_desc = TREND_DESCRIPTIONS.get(user_state["trend"], "")
        description = f"{emotion_desc} {trend_desc}".strip()
        
        # Return enhanced state data
        return {
            "state": user_state["state"],
            "confidence": user_state["confidence"],
            "trend": user_state["trend"],
            "history": user_state["history"],
            "color_code": color_code,
            "description": description
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error getting emotional state: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/timeline/{user_id}", response_model=EmotionalTimelineResponse)
async def get_emotional_timeline(user_id: int, days: int = 7, db: Session = Depends(get_db)):
    """
    Get emotional state timeline for visualization
    """
    try:
        # Check if user exists
        user = db.query(User).filter(User.id == user_id).first()
        if not user:
            raise HTTPException(status_code=404, detail="User not found")
        
        # Get messages from the specified time period
        start_date = datetime.now() - timedelta(days=days)
        messages = db.query(ChatMessage).filter(
            ChatMessage.user_id == user_id,
            ChatMessage.timestamp >= start_date
        ).order_by(ChatMessage.timestamp.asc()).all()
        
        if not messages:
            return {"timeline": [], "summary": {"state": "neutral", "trend": "stable"}}
        
        # Analyze emotional state for each message
        timeline = []
        emotion_values = {
            "distressed": -3,
            "sad": -2,
            "anxious": -1,
            "angry": -1,
            "neutral": 0,
            "content": 1,
            "hopeful": 2
        }
        
        numerical_values = []
        
        for msg in messages:
            emotion, confidence = analyze_message_emotion(msg.message)
            color = EMOTION_COLORS.get(emotion, "#34C759")
            numerical_value = emotion_values.get(emotion, 0)
            numerical_values.append(numerical_value)
            
            timeline.append({
                "timestamp": msg.timestamp.isoformat(),
                "emotion": emotion,
                "confidence": confidence,
                "color": color,
                "value": numerical_value,
                "short_message": msg.message[:50] + "..." if len(msg.message) > 50 else msg.message
            })
        
        # Calculate trend
        trend = "stable"
        if len(numerical_values) >= 3:
            # Simple linear regression
            x = list(range(len(numerical_values)))
            y = numerical_values
            
            n = len(x)
            sum_x = sum(x)
            sum_y = sum(y)
            sum_x_squared = sum(i ** 2 for i in x)
            sum_xy = sum(x[i] * y[i] for i in range(n))
            
            # Calculate slope
            m = (n * sum_xy - sum_x * sum_y) / (n * sum_x_squared - sum_x ** 2) if (n * sum_x_squared - sum_x ** 2) != 0 else 0
            
            if m > 0.1:
                trend = "improving"
            elif m < -0.1:
                trend = "declining"
        
        # Get current state (last message)
        current_state = timeline[-1]["emotion"] if timeline else "neutral"
        
        return {
            "timeline": timeline,
            "summary": {
                "state": current_state,
                "trend": trend,
                "color": EMOTION_COLORS.get(current_state, "#34C759"),
                "description": f"{EMOTION_DESCRIPTIONS.get(current_state, '')} {TREND_DESCRIPTIONS.get(trend, '')}".strip()
            }
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error getting emotional timeline: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/recommendations/{user_id}", response_model=RecommendationsResponse)
async def get_recommendations(user_id: int, db: Session = Depends(get_db)):
    """
    Get personalized recommendations based on emotional state
    """
    try:
        # Check if user exists
        user = db.query(User).filter(User.id == user_id).first()
        if not user:
            raise HTTPException(status_code=404, detail="User not found")
        
        # Get current emotional state
        user_state = track_emotional_state(user_id, db)
        emotion = user_state["state"]
        trend = user_state["trend"]
        
        # Define recommendations for each emotional state
        recommendations = {
            "distressed": [
                "Practice deep breathing for 5 minutes",
                "Reach out to a trusted friend or family member",
                "Consider speaking with a mental health professional",
                "Use grounding techniques (name 5 things you can see, 4 you can touch, etc.)",
                "Take a break from stressful activities"
            ],
            "sad": [
                "Engage in a small activity you usually enjoy",
                "Listen to uplifting music",
                "Spend time in nature if possible",
                "Journal about your feelings",
                "Practice self-compassion meditation"
            ],
            "anxious": [
                "Try progressive muscle relaxation",
                "Write down specific worries and examine evidence for/against them",
                "Limit caffeine and sugar intake",
                "Practice mindfulness meditation",
                "Break large tasks into smaller, manageable steps"
            ],
            "angry": [
                "Take a timeout before responding",
                "Physical activity to release tension",
                "Write out your thoughts before expressing them",
                "Practice assertive (not aggressive) communication",
                "Identify triggers and prepare coping strategies"
            ],
            "neutral": [
                "Maintain regular sleep schedule",
                "Continue physical activity routines",
                "Practice gratitude journaling",
                "Connect with others socially",
                "Learn something new today"
            ],
            "content": [
                "Savor this positive state with mindfulness",
                "Express gratitude to someone in your life",
                "Document what's working well for future reference",
                "Share your positive energy with others",
                "Build on this foundation with activities you enjoy"
            ],
            "hopeful": [
                "Set meaningful goals while in this positive state",
                "Reflect on your strengths and resources",
                "Create a vision board or journal about aspirations",
                "Practice optimistic thinking about specific challenges",
                "Share your hope with someone who may need encouragement"
            ]
        }
        
        # Get recommendations for the current state
        state_recommendations = recommendations.get(emotion, recommendations["neutral"])
        
        # Additional recommendations based on trend
        trend_recommendations = []
        if trend == "declining" and emotion in ["neutral", "content", "hopeful"]:
            trend_recommendations = [
                "Notice early signs of stress and address them proactively",
                "Maintain supportive routines as prevention"
            ]
        elif trend == "declining" and emotion in ["sad", "anxious", "angry", "distressed"]:
            trend_recommendations = [
                "Consider reaching out to a mental health professional",
                "Increase self-care activities and supportive connections"
            ]
        elif trend == "improving" and emotion in ["sad", "anxious", "angry", "distressed"]:
            trend_recommendations = [
                "Notice what's helping and continue those practices",
                "Document effective coping strategies for future reference"
            ]
        
        # Combine recommendations
        all_recommendations = state_recommendations + trend_recommendations
        
        return {
            "current_state": {
                "emotion": emotion,
                "trend": trend
            },
            "recommendations": all_recommendations
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error getting recommendations: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/insights/{user_id}")
async def get_emotional_insights(user_id: int, days: int = 30, db: Session = Depends(get_db)):
    """
    Get insights about emotional patterns over time
    """
    try:
        # Check if user exists
        user = db.query(User).filter(User.id == user_id).first()
        if not user:
            raise HTTPException(status_code=404, detail="User not found")
        
        # Get messages from the specified time period
        start_date = datetime.now() - timedelta(days=days)
        messages = db.query(ChatMessage).filter(
            ChatMessage.user_id == user_id,
            ChatMessage.timestamp >= start_date
        ).order_by(ChatMessage.timestamp.asc()).all()
        
        if not messages:
            return {
                "total_interactions": 0,
                "insights": [],
                "patterns": {}
            }
        
        # Analyze emotional states
        emotions = []
        for msg in messages:
            emotion, _ = analyze_message_emotion(msg.message)
            emotions.append(emotion)
        
        # Count occurrences of each emotion
        emotion_counts = {}
        for emotion in emotions:
            if emotion in emotion_counts:
                emotion_counts[emotion] += 1
            else:
                emotion_counts[emotion] = 1
        
        # Calculate percentages
        total = len(emotions)
        emotion_percentages = {emotion: (count / total) * 100 for emotion, count in emotion_counts.items()}
        
        # Identify patterns
        patterns = {}
        
        # Time of day patterns
        time_of_day = {
            "morning": 0,
            "afternoon": 0,
            "evening": 0,
            "night": 0
        }
        
        for msg in messages:
            hour = msg.timestamp.hour
            if 5 <= hour < 12:
                time_of_day["morning"] += 1
            elif 12 <= hour < 17:
                time_of_day["afternoon"] += 1
            elif 17 <= hour < 22:
                time_of_day["evening"] += 1
            else:
                time_of_day["night"] += 1
        
        patterns["time_of_day"] = time_of_day
        
        # Weekly patterns
        day_of_week = {
            0: 0,  # Monday
            1: 0,
            2: 0,
            3: 0,
            4: 0,
            5: 0,
            6: 0   # Sunday
        }
        
        for msg in messages:
            day = msg.timestamp.weekday()
            day_of_week[day] += 1
        
        patterns["day_of_week"] = day_of_week
        
        # Generate insights
        insights = []
        
        # Most common emotion
        if emotion_counts:
            most_common_emotion = max(emotion_counts, key=emotion_counts.get)
            insights.append({
                "type": "most_common_emotion",
                "title": f"Most Common Emotion: {most_common_emotion.title()}",
                "description": f"You most frequently express {most_common_emotion} emotions ({emotion_percentages[most_common_emotion]:.1f}% of interactions)."
            })
        
        # Emotional range
        if len(emotion_counts) >= 3:
            insights.append({
                "type": "emotional_range",
                "title": "Diverse Emotional Expression",
                "description": f"You've expressed {len(emotion_counts)} different emotional states, showing a healthy range of emotional experiences."
            })
        
        # Time patterns
        most_active_time = max(time_of_day, key=time_of_day.get)
        insights.append({
            "type": "time_pattern",
            "title": f"Most Active Time: {most_active_time.title()}",
            "description": f"You tend to engage most during the {most_active_time}."
        })
        
        return {
            "total_interactions": total,
            "emotion_distribution": emotion_percentages,
            "insights": insights,
            "patterns": patterns
        }
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error getting emotional insights: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))