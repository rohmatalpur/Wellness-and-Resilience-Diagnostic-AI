#!/bin/bash

# Display message
echo "Starting WARDA Therapist Backend..."

# Install required packages
echo "Installing dependencies..."
pip install -r requirements.txt

# Run database migrations
echo "Setting up database..."
python migrations.py

# Make sure environment variables are set
if [ ! -f .env ]; then
    echo "Creating .env file..."
    echo "# Database configuration" > .env
    echo "DB_USER=root" >> .env
    echo "DB_PASSWORD=" >> .env 
    echo "DB_HOST=localhost" >> .env
    echo "DB_PORT=3306" >> .env
    echo "DB_NAME=warda_therapist" >> .env
    echo "" >> .env
    echo "# API Keys" >> .env
    echo "GROQ_API_KEY=your-groq-api-key-here" >> .env
    
    echo ".env file created. Please edit it with your actual API key."
fi

# Check if embedding file exists
if [ ! -f "embeddings.pt" ]; then
    echo "Warning: embeddings.pt file not found."
    echo "Attempting to generate embeddings..."
    
    if [ -f "combined_transcript.csv" ]; then
        python generate_embedding.py
    else
        echo "Error: combined_transcript.csv file not found."
        echo "Please provide the transcript file and run generate_embedding.py manually."
    fi
fi

# Run the FastAPI server
echo "Starting server..."
python -m uvicorn app:app --host 0.0.0.0 --port 8000 --reload