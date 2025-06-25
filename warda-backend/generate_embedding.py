"""
Script to generate embeddings from the transcript dataset
"""
import pandas as pd
import torch
from sentence_transformers import SentenceTransformer
import logging
import os
import sys

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger(__name__)

def generate_embeddings():
    """Generate embeddings from the transcript dataset"""
    try:
        # Check if the file exists
        if not os.path.exists('combined_transcript.csv'):
            logger.error("combined_transcript.csv file not found. Please make sure it exists in the current directory.")
            sys.exit(1)
            
        # Load the transcript data
        logger.info("Loading transcript data...")
        df = pd.read_csv('combined_transcript.csv')
        logger.info(f"Loaded transcript data with {len(df)} rows")
        
        # Extract the text values
        texts = df["value"].astype(str).tolist()
        logger.info(f"Extracted {len(texts)} text samples for embedding")
        
        # Load the sentence transformer model
        logger.info("Loading sentence transformer model...")
        model = SentenceTransformer("BAAI/bge-large-en")
        logger.info("Model loaded successfully")
        
        # Generate embeddings
        logger.info("Generating embeddings... This may take some time.")
        embeddings = model.encode(texts, batch_size=32, convert_to_tensor=True, show_progress_bar=True)
        logger.info(f"Generated embeddings with shape: {embeddings.shape}")
        
        # Save embeddings
        logger.info("Saving embeddings...")
        torch.save({"texts": texts, "embeddings": embeddings}, "embeddings.pt")
        logger.info("Embeddings saved successfully as 'embeddings.pt'")
        
    except Exception as e:
        logger.error(f"Error generating embeddings: {str(e)}")
        sys.exit(1)

if __name__ == "__main__":
    generate_embeddings()