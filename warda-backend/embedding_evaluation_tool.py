"""
Utility script to evaluate the quality of embeddings and their influence on responses
"""
import os
import torch
import pandas as pd
import numpy as np
import json
from sentence_transformers import SentenceTransformer, util
import requests
import time
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Load Groq API key
GROQ_API_KEY = os.getenv("GROQ_API_KEY")
GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"

# Function to load and prepare data
def load_data():
    """Load embeddings and transcript data"""
    try:
        # Load embeddings with map_location parameter
        data = torch.load("embeddings.pt", map_location=torch.device('cpu'))
        texts = data["texts"]
        embeddings = data["embeddings"]
        print(f"Loaded {len(texts)} embeddings successfully")
        
        # Load transcript data
        df = pd.read_csv("combined_transcript.csv")
        print(f"Loaded transcript data with {len(df)} rows")
        
        # Extract unique sessions
        session_ids = df['session_id'].unique()
        print(f"Found {len(session_ids)} unique sessions")
        
        return texts, embeddings, df, session_ids
    except Exception as e:
        print(f"Error loading data: {str(e)}")
        return None, None, None, None

# Function to test similarity search
def test_similarity_search(query, texts, embeddings, model, top_k=5):
    """Test similarity search with a query"""
    try:
        # Encode query
        query_embedding = model.encode(query, convert_to_tensor=True)
        
        # Calculate similarities
        similarities = util.pytorch_cos_sim(query_embedding, embeddings)[0]
        
        # Get top-k similar results
        top_results = torch.topk(similarities, k=min(top_k, len(texts)))
        
        # Print results
        print(f"\nTop {top_k} similar texts for query: '{query}'")
        for i, idx in enumerate(top_results.indices):
            idx = idx.item()
            score = top_results.values[i].item()
            print(f"\n--- Result {i+1} (Score: {score:.4f}) ---")
            print(texts[idx][:300] + "..." if len(texts[idx]) > 300 else texts[idx])
        
        return [idx.item() for idx in top_results.indices]
    except Exception as e:
        print(f"Error in similarity search: {str(e)}")
        return []

# Function to test response generation with and without context
def test_response_generation(query, context=None):
    """Generate responses with and without context to compare the influence"""
    if not GROQ_API_KEY:
        print("GROQ_API_KEY not found in environment variables")
        return None, None
    
    system_prompt = """You are WARDA (Wellness and Resilience Diagnostic AI), a compassionate mental health assistant. 
    Always respond with empathy and understanding. Your purpose is to:
    1. Listen and validate the user's feelings
    2. Ask thoughtful questions to understand their situation better
    3. Offer gentle perspective and coping strategies when appropriate
    4. Remind users they're not alone in their struggles
    5. Encourage professional help for serious concerns
    
    Keep responses warm, supportive, and non-judgmental. Never minimize someone's feelings.
    Use a thoughtful, kind tone similar to how a skilled therapist would respond.
    """
    
    headers = {
        "Authorization": f"Bearer {GROQ_API_KEY}",
        "Content-Type": "application/json"
    }
    
    # Generate response without context
    no_context_payload = {
        "model": "llama3-8b-8192",
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": f"User's message: {query}"}
        ],
        "temperature": 0.7
    }
    
    # Generate response with context
    context_prompt = system_prompt
    if context:
        context_prompt += "\n\nIMPORTANT: Base your response on the provided context information, which contains relevant examples of how a skilled therapist might respond to similar situations."
    
    with_context_payload = {
        "model": "llama3-8b-8192",
        "messages": [
            {"role": "system", "content": context_prompt},
            {"role": "user", "content": f"Context information: {context if context else 'No specific context available.'}\n\nUser's message: {query}"}
        ],
        "temperature": 0.7
    }
    
    try:
        # Get response without context
        print("\nGenerating response WITHOUT context...")
        no_context_response = requests.post(GROQ_API_URL, headers=headers, json=no_context_payload, timeout=30)
        no_context_text = no_context_response.json()["choices"][0]["message"]["content"] if no_context_response.status_code == 200 else "Error generating response"
        
        # Get response with context
        print("Generating response WITH context...")
        with_context_response = requests.post(GROQ_API_URL, headers=headers, json=with_context_payload, timeout=30)
        with_context_text = with_context_response.json()["choices"][0]["message"]["content"] if with_context_response.status_code == 200 else "Error generating response"
        
        # Display results
        print("\n=== Response WITHOUT Context ===")
        print(no_context_text)
        print("\n=== Response WITH Context ===")
        print(with_context_text)
        
        return no_context_text, with_context_text
    
    except Exception as e:
        print(f"Error generating responses: {str(e)}")
        return None, None

# Function to evaluate influence of context
def evaluate_context_influence(no_context_response, with_context_response, sample_texts):
    """Evaluate how much the context influenced the response"""
    if not no_context_response or not with_context_response:
        return 0, "Unable to evaluate"
    
    try:
        # Load sentence transformer model
        model = SentenceTransformer("BAAI/bge-large-en")
        
        # Encode responses
        no_context_emb = model.encode(no_context_response, convert_to_tensor=True)
        with_context_emb = model.encode(with_context_response, convert_to_tensor=True)
        
        # Calculate similarity between responses
        response_similarity = util.pytorch_cos_sim(no_context_emb, with_context_emb)[0][0].item()
        
        # Encode sample texts
        sample_embs = model.encode(sample_texts, convert_to_tensor=True)
        
        # Calculate similarity of with_context to sample texts
        sample_similarities = util.pytorch_cos_sim(with_context_emb, sample_embs)[0]
        max_sample_similarity = torch.max(sample_similarities).item()
        avg_sample_similarity = torch.mean(sample_similarities).item()
        
        # Calculate influence score (lower response similarity and higher sample similarity = more influence)
        # Scale: 0 (no influence) to 1 (high influence)
        influence_score = (1 - response_similarity) * 0.5 + avg_sample_similarity * 0.5
        
        # Interpret the results
        if influence_score > 0.6:
            interpretation = "High influence: The context strongly affected the response"
        elif influence_score > 0.3:
            interpretation = "Moderate influence: The context somewhat affected the response"
        else:
            interpretation = "Low influence: The context had minimal effect on the response"
        
        print(f"\n=== Context Influence Analysis ===")
        print(f"Response similarity: {response_similarity:.4f} (lower = more different)")
        print(f"Max sample similarity: {max_sample_similarity:.4f} (higher = more similar to context)")
        print(f"Avg sample similarity: {avg_sample_similarity:.4f}")
        print(f"Influence score: {influence_score:.4f}")
        print(interpretation)
        
        return influence_score, interpretation
    
    except Exception as e:
        print(f"Error evaluating context influence: {str(e)}")
        return 0, "Error in evaluation"

def main():
    """Main function to test embeddings and response generation"""
    # Load data
    texts, embeddings, df, session_ids = load_data()
    if texts is None or embeddings is None:
        print("Failed to load required data. Exiting.")
        return
    
    # Load model
    try:
        model = SentenceTransformer("BAAI/bge-large-en")
        print("Loaded sentence transformer model successfully")
    except Exception as e:
        print(f"Error loading model: {str(e)}")
        return
    
    # Test queries
    test_queries = [
        "I've been feeling really sad lately and I don't know why",
        "I'm having trouble sleeping because of stress at work",
        "I'm worried I might have anxiety, what are the symptoms?",
        "My relationship with my partner is making me depressed"
    ]
    
    # Run tests
    results = []
    for query in test_queries:
        print(f"\n\n========== Testing Query: '{query}' ==========")
        
        # Test similarity search
        similar_indices = test_similarity_search(query, texts, embeddings, model)
        if not similar_indices:
            print("Failed to find similar texts. Skipping this query.")
            continue
        
        # Get context from similar texts
        similar_texts = [texts[idx] for idx in similar_indices]
        context = "\n\n---\n\n".join(similar_texts)
        
        # Test response generation
        no_context_response, with_context_response = test_response_generation(query, context)
        
        # Evaluate context influence
        influence_score, interpretation = evaluate_context_influence(
            no_context_response, 
            with_context_response, 
            similar_texts
        )
        
        # Save results
        results.append({
            "query": query,
            "influence_score": influence_score,
            "interpretation": interpretation
        })
    
    # Print summary
    print("\n\n========== SUMMARY ==========")
    avg_influence = sum(r["influence_score"] for r in results) / len(results) if results else 0
    print(f"Average influence score: {avg_influence:.4f}")
    
    if avg_influence > 0.6:
        print("Overall, the embeddings have a STRONG influence on responses")
    elif avg_influence > 0.3:
        print("Overall, the embeddings have a MODERATE influence on responses")
    else:
        print("Overall, the embeddings have a WEAK influence on responses")
    
    # Suggest improvements
    print("\nSuggestions for improvement:")
    if avg_influence < 0.4:
        print("1. Modify the system prompt to emphasize using the context")
        print("2. Format the context in a more structured way")
        print("3. Consider using a different model with better instruction following")
        print("4. Try incorporating more specific examples in the context")
    
    # Save results to file
    with open("embedding_evaluation_results.json", "w") as f:
        json.dump(results, f, indent=2)
    print("\nResults saved to embedding_evaluation_results.json")

if __name__ == "__main__":
    main()