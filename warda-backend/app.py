"""
Main FastAPI application entry point for WARDA Therapist API
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import logging
import os
from dotenv import load_dotenv

# Import our database module
from database import create_tables

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

# Load environment variables
load_dotenv()

# Create FastAPI app
app = FastAPI(title="WARDA Therapist API", version="2.0.0")

# Configure CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # For development; restrict in production
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Create database tables at startup
@app.on_event("startup")
async def startup_event():
    create_tables()
    logger.info("Database tables created successfully")
    
    # Initialize and load models/data
    from enhanced_rag import initialize_rag_system
    initialize_rag_system()
    logger.info("RAG system initialized successfully")

@app.get("/")
async def root():
    return {
        "name": "WARDA Therapist API",
        "version": "2.0.0",
        "description": "Enhanced mental health assistant with emotional state tracking"
    }

@app.get("/health")
async def health_check():
    from enhanced_rag import get_rag_status
    rag_status = get_rag_status()
    
    return {
        "status": "healthy", 
        "model_loaded": rag_status["model_loaded"],
        "embeddings_loaded": rag_status["embeddings_loaded"],
        "version": "2.0.0"
    }

# Import and register all routes
from auth_routes import router as auth_router
from chat_routes import router as chat_router
from state_tracking_routes import router as state_router

app.include_router(auth_router, prefix="/auth", tags=["authentication"])
app.include_router(chat_router, prefix="/chat", tags=["chat"])
app.include_router(state_router, prefix="/state", tags=["state-tracking"])

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app:app", host="0.0.0.0", port=8000, reload=True)