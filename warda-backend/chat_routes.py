"""
Chat routes for WARDA Therapist API
"""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import Dict, Any, List, Optional
from pydantic import BaseModel
import logging
import time

from database import get_db, ChatMessage
from enhanced_rag import generate_response, analyze_message_emotion

# Configure logging
logger = logging.getLogger(__name__)

# Create router
router = APIRouter()

# Define request and response models
class ChatRequest(BaseModel):
    query: str
    user_id: Optional[int] = None

class ChatResponse(BaseModel):
    query: str
    response: str
    emotional_state: Optional[str] = None
    confidence: Optional[float] = None
    error: Optional[str] = None

@router.post("/message", response_model=ChatResponse)
async def chat_message(request: ChatRequest, db: Session = Depends(get_db)):
    """
    Process a chat message and generate a response
    """
    start_time = time.time()
    logger.info(f"Received chat request: user_id={request.user_id}, query='{request.query}'")
    
    try:
        # Get conversation history if user_id provided
        conversation_history = []
        if request.user_id:
            try:
                recent_messages = db.query(ChatMessage).filter(
                    ChatMessage.user_id == request.user_id
                ).order_by(ChatMessage.timestamp.desc()).limit(5).all()
                
                if recent_messages:
                    for msg in reversed(recent_messages):
                        conversation_history.append({
                            "query": msg.message,
                            "response": msg.response
                        })
                    logger.info(f"Retrieved {len(conversation_history)} messages from history")
            except Exception as e:
                logger.error(f"Error retrieving conversation history: {str(e)}")
        
        # Generate response
        result = generate_response(
            query=request.query,
            user_id=request.user_id,
            conversation_history=conversation_history
        )
        
        # Save to database if user_id is provided
        if request.user_id:
            try:
                chat_message = ChatMessage(
                    user_id=request.user_id,
                    message=request.query,
                    response=result["response"]
                )
                db.add(chat_message)
                db.commit()
                logger.info(f"Chat message saved to database for user_id: {request.user_id}")
            except Exception as e:
                logger.error(f"Error saving chat message: {str(e)}")
                db.rollback()
        
        # Log processing time
        processing_time = time.time() - start_time
        logger.info(f"Request processed in {processing_time:.2f} seconds")
        
        # Return response
        return {
            "query": request.query,
            "response": result["response"],
            "emotional_state": result["emotional_state"],
            "confidence": result["confidence"],
            "error": result["error"]
        }
    except Exception as e:
        logger.error(f"Error processing chat request: {str(e)}")
        return {
            "query": request.query,
            "response": "I encountered an error while processing your request. Please try again.",
            "emotional_state": "neutral",
            "confidence": 0.5,
            "error": str(e)
        }

@router.get("/history/{user_id}", response_model=List[Dict[str, Any]])
async def get_chat_history(user_id: int, limit: int = 20, db: Session = Depends(get_db)):
    """
    Get chat history for a user
    """
    try:
        # Get recent messages
        messages = db.query(ChatMessage).filter(
            ChatMessage.user_id == user_id
        ).order_by(ChatMessage.timestamp.desc()).limit(limit).all()
        
        # Format response
        history = []
        for msg in messages:
            # Analyze emotional state if not already done
            emotional_state, confidence = analyze_message_emotion(msg.message)
            
            history.append({
                "id": msg.id,
                "user_id": msg.user_id,
                "query": msg.message,
                "response": msg.response,
                "timestamp": msg.timestamp.isoformat(),
                "emotional_state": emotional_state,
                "confidence": confidence
            })
        
        return history
    except Exception as e:
        logger.error(f"Error retrieving chat history: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))

@router.delete("/history/{message_id}")
async def delete_chat_message(message_id: int, db: Session = Depends(get_db)):
    """
    Delete a specific chat message
    """
    try:
        # Get message
        message = db.query(ChatMessage).filter(
            ChatMessage.id == message_id
        ).first()
        
        if not message:
            raise HTTPException(status_code=404, detail="Message not found")
        
        # Delete message
        db.delete(message)
        db.commit()
        
        return {"status": "success", "message": "Chat message deleted successfully"}
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Error deleting chat message: {str(e)}")
        raise HTTPException(status_code=500, detail=str(e))