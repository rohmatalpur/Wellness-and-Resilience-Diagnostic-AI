"""
Enhanced RAG (Retrieval-Augmented Generation) implementation
for WARDA Therapist Application
"""
import os
import torch
import pandas as pd
import numpy as np
import requests
import logging
from typing import Dict, Any, List, Optional, Tuple
from sentence_transformers import SentenceTransformer, util
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

# Configure logging
logger = logging.getLogger(__name__)

# Global variables
model = None
texts = []
embeddings = None
df = None
session_data = {}
GROQ_API_KEY = os.getenv("GROQ_API_KEY")
GROQ_API_URL = "https://api.groq.com/openai/v1/chat/completions"

def initialize_rag_system():
    """Initialize the RAG system by loading models and data"""
    global model, texts, embeddings, df, session_data, GROQ_API_KEY
    
    logger.info("Initializing RAG system...")
    
    # Load embeddings
    try:
        data = torch.load("embeddings.pt", map_location=torch.device('cpu'))
        texts = data["texts"]
        embeddings = data["embeddings"]
        logger.info(f"Loaded {len(texts)} embeddings successfully")
    except Exception as e:
        logger.error(f"Error loading embeddings: {str(e)}")
        texts = []
        embeddings = None
    
    # Load transcript data
    try:
        df = pd.read_csv("combined_transcript.csv")
        logger.info(f"Loaded transcript data with {len(df)} rows")
        
        # Extract session information
        for _, row in df.iterrows():
            session_id = row['session_id']
            if session_id not in session_data:
                session_data[session_id] = []
            session_data[session_id].append({
                'speaker': row['speaker'],
                'value': row['value'],
                'start_time': row['start_time'],
                'stop_time': row['stop_time']
            })
        
        logger.info(f"Extracted data from {len(session_data)} unique sessions")
    except Exception as e:
        logger.error(f"Error loading transcript data: {str(e)}")
        df = None
        session_data = {}
    
    # Initialize model
    try:
        model = SentenceTransformer("BAAI/bge-large-en")
        logger.info("Loaded sentence transformer model successfully")
    except Exception as e:
        logger.error(f"Error loading model: {str(e)}")
        model = None
    
    # Check API key
    if not GROQ_API_KEY:
        logger.warning("GROQ_API_KEY not found in environment variables")
    
    logger.info("RAG system initialization completed")

def get_rag_status():
    """Get the status of the RAG system"""
    return {
        "model_loaded": model is not None,
        "embeddings_loaded": embeddings is not None,
        "data_loaded": df is not None,
        "sessions_loaded": len(session_data) > 0,
        "api_key_available": GROQ_API_KEY is not None
    }

def analyze_message_emotion(message: str) -> Tuple[str, float]:
    """
    Analyze the emotional content of a message using the embeddings model
    
    Args:
        message (str): The user's message
        
    Returns:
        tuple: (emotion_label, confidence_score)
    """
    if model is None:
        return "neutral", 0.5
    
    try:
        # Emotion categories with example phrases
        emotions = {
            "distressed": [
                "I feel terrible", "I'm having a breakdown", "I can't take this anymore",
                "Everything is falling apart", "I'm at my lowest point"
            ],
            "sad": [
                "I feel sad", "I'm unhappy", "I'm feeling down", 
                "I'm depressed", "I feel empty inside"
            ],
            "anxious": [
                "I'm worried", "I feel anxious", "I'm nervous",
                "I'm panicking", "I can't stop worrying"
            ],
            "angry": [
                "I'm angry", "I'm frustrated", "I'm irritated",
                "I'm furious", "I hate this"
            ],
            "neutral": [
                "I'm okay", "I'm fine", "Things are normal",
                "I don't feel much", "I'm just here"
            ],
            "content": [
                "I'm happy", "I feel good", "I'm doing well",
                "Things are fine", "I'm content"
            ],
            "hopeful": [
                "I feel hopeful", "I'm optimistic", "Things are getting better",
                "I see progress", "I'm looking forward to the future"
            ]
        }
        
        # Encode the user message
        message_embedding = model.encode(message, convert_to_tensor=True)
        
        # Calculate similarity with each emotion category
        max_similarity = -1
        detected_emotion = "neutral"
        confidence = 0.5
        
        for emotion, examples in emotions.items():
            # Encode emotion examples
            emotion_embeddings = model.encode(examples, convert_to_tensor=True)
            
            # Calculate similarities
            similarities = util.pytorch_cos_sim(message_embedding, emotion_embeddings)[0]
            avg_similarity = torch.mean(similarities).item()
            
            if avg_similarity > max_similarity:
                max_similarity = avg_similarity
                detected_emotion = emotion
                confidence = max(0.5, min(0.95, avg_similarity))  # Scale to reasonable confidence
        
        # Check for specific keywords to override embedding results
        keyword_emotions = {
            "distressed": ["suicide", "kill myself", "want to die", "end my life", "better off dead"],
            "sad": ["depressed", "hopeless", "miserable", "heartbroken", "grief"],
            "anxious": ["panic attack", "terrified", "anxious", "worried sick", "fear"],
            "angry": ["furious", "hate", "rage", "fed up", "pissed off"]
        }
        
        for emotion, keywords in keyword_emotions.items():
            if any(keyword in message.lower() for keyword in keywords):
                return emotion, 0.9  # High confidence for keyword matches
        
        return detected_emotion, confidence
    
    except Exception as e:
        logger.error(f"Error in emotion analysis: {str(e)}")
        return "neutral", 0.5

def determine_response_style(query: str, emotional_state: str, conversation_history: List = None) -> Dict[str, str]:
    """
    Determine the appropriate response style based on query and emotional state
    
    Args:
        query (str): The user's query
        emotional_state (str): The detected emotional state
        conversation_history (list): Optional list of previous messages
        
    Returns:
        dict: Response style parameters
    """
    # Default style
    style = {
        "emotion": emotional_state,
        "length": "medium",
        "tone": "supportive"
    }
    
    # Adjust length based on query characteristics
    query_length = len(query)
    
    # Short queries typically need shorter responses
    if query_length < 25:
        style["length"] = "short"
    
    # Complex or detailed queries need longer responses
    elif any(word in query.lower() for word in ["explain", "tell me about", "describe", "help me understand", "what is"]):
        style["length"] = "long"
    
    # Questions asking how to deal with something need medium or long responses
    elif any(word in query.lower() for word in ["how do i", "how can i", "what should i", "how to"]):
        style["length"] = "medium"
    
    # Adjust tone based on emotional state
    if emotional_state in ["distressed", "sad"]:
        style["tone"] = "gentle"
    elif emotional_state == "angry":
        style["tone"] = "calm"
    elif emotional_state == "anxious":
        style["tone"] = "reassuring"
    elif emotional_state in ["content", "hopeful"]:
        style["tone"] = "affirming"
    
    # Adjust based on conversation history if available
    if conversation_history and len(conversation_history) >= 2:
        # For ongoing conversations, maintain continuity
        # If previous responses were detailed, continue with detailed responses
        if any(len(msg.get("response", "")) > 300 for msg in conversation_history[-2:]):
            style["length"] = "medium" if style["length"] == "short" else style["length"]
        
        # If conversation is intense, use more detailed responses
        if emotional_state in ["distressed", "anxious"]:
            style["length"] = "medium" if style["length"] == "short" else style["length"]
    
    logger.info(f"Determined response style: {style}")
    return style

def get_conversation_examples(emotion: str, length_type: str, query: str = None) -> List[Dict[str, Any]]:
    """
    Get relevant conversation examples from the dataset
    
    Args:
        emotion (str): Target emotional state
        length_type (str): Desired response length
        query (str): Optional user query for relevance matching
        
    Returns:
        list: List of conversation examples
    """
    if not session_data or not model:
        return []
    
    try:
        # Define sample sizes for different length types
        length_limits = {
            "short": 150,
            "medium": 300,
            "long": 500
        }
        
        # Map emotions to keywords for filtering
        emotion_keywords = {
            "distressed": ["terrible", "breakdown", "can't take", "falling apart", "lowest"],
            "sad": ["sad", "unhappy", "down", "depressed", "empty"],
            "anxious": ["worried", "anxious", "nervous", "panic", "stress"],
            "angry": ["angry", "frustrated", "irritated", "furious", "hate"],
            "neutral": ["okay", "fine", "normal", "don't feel", "just here"],
            "content": ["happy", "good", "well", "fine", "content"],
            "hopeful": ["hopeful", "optimistic", "better", "progress", "forward"]
        }
        
        matching_examples = []
        
        # Get keywords for the requested emotion
        target_keywords = emotion_keywords.get(emotion, emotion_keywords["neutral"])
        
        # Embed query if available
        query_embedding = None
        if query:
            query_embedding = model.encode(query, convert_to_tensor=True)
        
        # Look through sessions to find matching responses
        for session_id, messages in session_data.items():
            for i in range(len(messages) - 1):
                # Skip if not a client/user message
                if messages[i]['speaker'] not in ['client', 'user']:
                    continue
                
                # Skip if next message is not a therapist/assistant response
                if i + 1 >= len(messages) or messages[i+1]['speaker'] not in ['therapist', 'assistant']:
                    continue
                
                user_message = messages[i]['value']
                response = messages[i+1]['value']
                response_length = len(response)
                
                # Skip responses that don't match the target length
                max_length = length_limits.get(length_type, length_limits["medium"])
                if length_type == "short" and response_length > max_length:
                    continue
                elif length_type == "medium" and (response_length < length_limits["short"] or response_length > max_length):
                    continue
                elif length_type == "long" and response_length < length_limits["medium"]:
                    continue
                
                # Check for keyword matches in the response
                has_keyword = any(keyword.lower() in response.lower() for keyword in target_keywords)
                
                # Skip if not matching emotion keywords (unless neutral)
                if not has_keyword and emotion != "neutral":
                    continue
                
                example = {
                    "user_message": user_message,
                    "response": response,
                    "length": response_length,
                    "relevance": 0.5  # Default relevance score
                }
                
                # Calculate relevance to query if available
                if query_embedding is not None:
                    try:
                        user_msg_embedding = model.encode(user_message, convert_to_tensor=True)
                        relevance = float(util.pytorch_cos_sim(query_embedding, user_msg_embedding)[0][0])
                        example["relevance"] = relevance
                    except Exception as e:
                        logger.error(f"Error calculating relevance: {str(e)}")
                
                matching_examples.append(example)
        
        # Sort by relevance to query if available
        if query_embedding is not None:
            matching_examples.sort(key=lambda x: x.get("relevance", 0), reverse=True)
        else:
            # Sort by keyword frequency
            matching_examples.sort(
                key=lambda x: sum(1 for kw in target_keywords if kw.lower() in x["response"].lower()), 
                reverse=True
            )
        
        # Return top examples (max 3)
        return matching_examples[:3]
        
    except Exception as e:
        logger.error(f"Error getting conversation examples: {str(e)}")
        return []

def get_emotion_guidance(emotion: str) -> str:
    """
    Get guidance for responding to different emotional states
    
    Args:
        emotion (str): The emotional state
        
    Returns:
        str: Response guidance for the emotional state
    """
    guidance = {
        "distressed": """
        This user is in significant distress. Your response should:
        - Lead with validation and empathy for their difficult emotions
        - Use a calm, steady tone
        - Focus on immediate emotional stabilization
        - Provide clear, simple grounding techniques
        - Gently encourage professional support if appropriate
        - Avoid overwhelming with too many suggestions
        - End with a note of realistic hope and support
        """,
        
        "sad": """
        This user is feeling sad or down. Your response should:
        - Validate their feelings without minimizing them
        - Express empathy for their experience
        - Normalize sadness as a natural emotion
        - Offer gentle perspective while honoring their feelings
        - Suggest small, achievable actions for self-care
        - Balance realism with gentle encouragement
        """,
        
        "anxious": """
        This user is experiencing anxiety. Your response should:
        - Acknowledge their worries without amplifying them
        - Use calming, measured language
        - Provide factual information if they have specific fears
        - Suggest grounding or breathing techniques
        - Help them distinguish between productive and unproductive worry
        - Gently challenge catastrophic thinking if present
        """,
        
        "angry": """
        This user is feeling angry or frustrated. Your response should:
        - Validate their feelings without judgment
        - Maintain a calm, non-defensive tone
        - Acknowledge any legitimate grievances
        - Help identify the underlying needs or values at stake
        - Suggest constructive ways to channel the energy
        - Provide perspective without dismissing their feelings
        """,
        
        "neutral": """
        This user is in a relatively neutral emotional state. Your response should:
        - Match their neutral tone while remaining warm
        - Be straightforward and informative
        - Focus on their specific questions or needs
        - Provide balanced perspective
        - Offer practical suggestions relevant to their situation
        """,
        
        "content": """
        This user is in a positive emotional state. Your response should:
        - Celebrate and reinforce their positive feelings
        - Match their optimistic tone
        - Help them identify what's working well
        - Suggest ways to build on current successes
        - Provide additional insights that might be helpful
        - Maintain realistic optimism
        """,
        
        "hopeful": """
        This user is feeling hopeful or optimistic. Your response should:
        - Affirm and strengthen their sense of hope
        - Build on their positive momentum
        - Offer additional perspectives or resources
        - Help them set realistic but meaningful goals
        - Balance optimism with practical next steps
        - Encourage continued growth and reflection
        """
    }
    
    return guidance.get(emotion, guidance["neutral"]).strip()

def get_length_guidance(length_type: str) -> str:
    """
    Get guidance for response length
    
    Args:
        length_type (str): The desired response length
        
    Returns:
        str: Response guidance for the length type
    """
    guidance = {
        "short": """
        Keep your response concise and supportive (1-2 short paragraphs).
        Focus on validation and perhaps one simple suggestion or question.
        Ideal for brief check-ins or straightforward questions.
        """,
        
        "medium": """
        Provide a balanced response (2-3 paragraphs) with:
        - Validation of their experience
        - Gentle insights or perspective
        - 1-2 specific suggestions or questions
        This length is ideal for most therapeutic exchanges.
        """,
        
        "long": """
        Give a comprehensive response (4+ paragraphs) with:
        - Thorough validation and normalization
        - Detailed perspective or psychoeducation
        - Multiple coping strategies or techniques
        - Thoughtful questions to deepen exploration
        - Clear structure with natural progression
        This length is best for complex issues or educational content.
        """
    }
    
    return guidance.get(length_type, guidance["medium"]).strip()

def retrieve_enhanced_context(query: str, response_style: Dict[str, str]) -> str:
    """
    Retrieve enhanced context for response generation
    
    Args:
        query (str): The user's query
        response_style (dict): Response style parameters
        
    Returns:
        str: Retrieved context
    """
    if model is None or embeddings is None:
        logger.warning("Model or embeddings not available for context retrieval")
        return ""
    
    try:
        emotion = response_style.get("emotion", "neutral")
        length_type = response_style.get("length", "medium")
        tone = response_style.get("tone", "supportive")
        
        logger.info(f"Retrieving context for: '{query}' (emotion: {emotion}, length: {length_type}, tone: {tone})")
        
        # Embed the query
        query_embedding = model.encode(query, convert_to_tensor=True)
        
        # Get semantic similarity results
        similarities = util.pytorch_cos_sim(query_embedding, embeddings)[0]
        
        # Determine number of results to retrieve based on query complexity
        query_complexity = min(1.0, len(query) / 100)  # Scale from 0.0 to 1.0 based on length
        base_results = 5
        
        # Adjust based on emotional state and desired response length
        emotion_factors = {
            "distressed": 1.5,  # More results for distressed users
            "sad": 1.2,
            "anxious": 1.2,
            "angry": 1.3,
            "neutral": 1.0,
            "content": 0.9,
            "hopeful": 0.9
        }
        
        length_factors = {
            "short": 0.8,
            "medium": 1.0,
            "long": 1.5
        }
        
        emotion_factor = emotion_factors.get(emotion, 1.0)
        length_factor = length_factors.get(length_type, 1.0)
        
        top_k = max(3, int(base_results * query_complexity * emotion_factor * length_factor))
        logger.info(f"Using top_k = {top_k} for retrieval")
        
        # Get top-k results
        top_indices = torch.topk(similarities, k=min(top_k * 2, len(texts))).indices
        top_values = torch.topk(similarities, k=min(top_k * 2, len(texts))).values
        
        # Filter results to avoid duplicates and low similarity
        min_similarity = 0.3
        retrieved_texts = []
        seen_fingerprints = set()
        
        for i, idx in enumerate(top_indices):
            idx_item = idx.item()
            similarity = top_values[i].item()
            
            # Skip if similarity is too low
            if similarity < min_similarity:
                continue
            
            text = texts[idx_item]
            
            # Create a fingerprint to detect near-duplicates
            text_fingerprint = ' '.join(text.split()[:10])
            if text_fingerprint in seen_fingerprints:
                continue
                
            seen_fingerprints.add(text_fingerprint)
            retrieved_texts.append((similarity, text))
        
        # Sort by similarity and take top results
        retrieved_texts.sort(reverse=True, key=lambda x: x[0])
        top_texts = retrieved_texts[:top_k]
        
        # Format context sections
        context_parts = []
        
        # 1. Add semantic search results
        if top_texts:
            semantic_context = "\n\n".join([text for _, text in top_texts])
            context_parts.append(f"## Relevant Information\n{semantic_context}")
        
        # 2. Add conversation examples
        examples = get_conversation_examples(emotion, length_type, query)
        if examples:
            examples_text = "## Example Conversations\n\n"
            for i, example in enumerate(examples):
                examples_text += f"### Example {i+1}\n"
                examples_text += f"User: {example['user_message']}\n"
                examples_text += f"Therapist: {example['response']}\n\n"
            
            context_parts.append(examples_text)
        
        # 3. Add guidance based on response style
        emotion_guidance = get_emotion_guidance(emotion)
        if emotion_guidance:
            context_parts.append(f"## Response Tone\n{emotion_guidance}")
        
        length_guidance = get_length_guidance(length_type)
        if length_guidance:
            context_parts.append(f"## Response Length\n{length_guidance}")
        
        # Combine all context parts
        final_context = "\n\n".join(context_parts)
        
        logger.info(f"Retrieved context with {len(final_context)} characters")
        return final_context
    
    except Exception as e:
        logger.error(f"Error retrieving context: {str(e)}")
        return ""

def check_for_crisis_content(message: str) -> bool:
    """
    Check if a message contains crisis indicators
    
    Args:
        message (str): The user's message
        
    Returns:
        bool: True if crisis content detected
    """
    crisis_keywords = [
        "suicide", "kill myself", "end my life", "don't want to live", 
        "want to die", "better off dead", "hurt myself", "harm myself",
        "no reason to live", "can't go on", "ending it all"
    ]
    
    return any(keyword in message.lower() for keyword in crisis_keywords)

def is_mental_health_related(query: str) -> bool:
    """
    Check if query is related to mental health
    
    Args:
        query (str): The user's query
        
    Returns:
        bool: True if mental health related
    """
    if model is None:
        return True  # Default to allow if model not available
    
    try:
        # Simple keyword matching for common mental health terms
        keywords = [
            "sad", "depress", "anxi", "stress", "worry", "feel", "emotion", 
            "mental", "health", "therapy", "help", "relationship", "lonely",
            "tired", "exhausted", "overwhelm", "grief", "trauma", "fear",
            "panic", "angry", "upset", "life", "live", "death", "happiness",
            "cry", "hurt", "pain", "alone", "friend", "family", "love"
        ]
        
        # Check for keyword matches
        for keyword in keywords:
            if keyword.lower() in query.lower():
                return True
        
        # Use embedding similarity as a backup
        mental_health_topics = [
            "I'm feeling sad", "I'm depressed", "I'm stressed", "I feel anxious",
            "I have panic attacks", "I'm feeling overwhelmed", "I'm lonely",
            "I lost a loved one", "I failed an exam", "I'm dealing with a breakup",
            "I feel like giving up", "How to cope with depression?", "What are anxiety symptoms?",
            "How do I handle stress?", "How to feel better after failure?",
            "How to improve my mental health?", "How can I sleep better with stress?",
            "I'm feeling hopeless", "I need emotional support"
        ]
        
        blocked_topics = [
            "how to dance", "how to cook", "how to eat", "how to exercise", 
            "how to sleep better at night", "how to tie a tie", "how to write an email",
            "how to make a shake", "how to clean my room", "how to be fashionable"
        ]
        
        # Encode texts
        query_embedding = model.encode(query, convert_to_tensor=True)
        mh_embeddings = model.encode(mental_health_topics, convert_to_tensor=True)
        blocked_embeddings = model.encode(blocked_topics, convert_to_tensor=True)
        
        # Calculate similarities
        mh_similarities = util.pytorch_cos_sim(query_embedding, mh_embeddings)[0]
        blocked_similarities = util.pytorch_cos_sim(query_embedding, blocked_embeddings)[0]
        
        max_mh_similarity = torch.max(mh_similarities).item()
        max_blocked_similarity = torch.max(blocked_similarities).item()
        
        # More permissive thresholds
        return max_mh_similarity > 0.3 or max_blocked_similarity < 0.8
        
    except Exception as e:
        logger.error(f"Error in mental health topic detection: {str(e)}")
        return True  # Default to allow if there's an error

def generate_response(query: str, user_id: Optional[int] = None, conversation_history: Optional[List] = None) -> Dict[str, Any]:
    """
    Generate a response using enhanced RAG
    
    Args:
        query (str): The user's query
        user_id (int): Optional user ID
        conversation_history (list): Optional conversation history
        
    Returns:
        dict: Response data including text and emotional state
    """
    logger.info(f"Generating response for query: '{query}'")
    
    # Initialize response data
    response_data = {
        "response": "",
        "emotional_state": "neutral",
        "confidence": 0.5,
        "processing_time": 0,
        "error": None
    }
    
    # Check for API key
    if not GROQ_API_KEY:
        response_data["error"] = "GROQ_API_KEY not found in environment variables"
        response_data["response"] = "I'm experiencing technical difficulties. Please make sure the API key is configured."
        return response_data
    
    # Check for crisis content
    if check_for_crisis_content(query):
        logger.warning(f"Crisis content detected in query: '{query}'")
        response_data["response"] = (
            "I'm concerned about what you're sharing. If you're having thoughts of hurting yourself, "
            "please reach out to a crisis helpline immediately: National Suicide Prevention Lifeline "
            "at 988 or 1-800-273-8255. They have trained counselors available 24/7. Would you like "
            "me to provide other resources that might help?"
        )
        response_data["emotional_state"] = "distressed"
        response_data["confidence"] = 0.9
        return response_data
    
    # Check if query is mental health related
    if not is_mental_health_related(query):
        logger.info(f"Query not mental health related: '{query}'")
        response_data["response"] = (
            "I'm a mental health assistant designed to help with emotional well-being and mental health concerns. "
            "I don't have expertise in other topics. Could you please ask me something related to mental health "
            "or emotional support?"
        )
        return response_data
    
    try:
        # Analyze emotional state
        emotion, confidence = analyze_message_emotion(query)
        response_data["emotional_state"] = emotion
        response_data["confidence"] = confidence
        
        # Determine response style
        response_style = determine_response_style(query, emotion, conversation_history)
        
        # Retrieve enhanced context
        context = retrieve_enhanced_context(query, response_style)
        
        # Create the system prompt
        system_prompt = f"""You are WARDA (Wellness and Resilience Diagnostic AI), a compassionate mental health assistant. 
        Always respond with empathy and understanding. Your purpose is to:
        1. Listen and validate the user's feelings
        2. Ask thoughtful questions to understand their situation better
        3. Offer gentle perspective and coping strategies when appropriate
        4. Remind users they're not alone in their struggles
        5. Encourage professional help for serious concerns
        
        The user appears to be in a {emotion} emotional state. Respond with appropriate tone and depth.
        
        Keep responses warm, supportive, and non-judgmental. Never minimize someone's feelings.
        Use a thoughtful, kind tone similar to how a skilled therapist would respond.
        
        IMPORTANT: Use the retrieved context to inform your response. This includes relevant information and examples
        of how skilled therapists respond to similar situations. Follow the guidance on tone and length.
        """
        
        # Set up request to Groq API
        headers = {
            "Authorization": f"Bearer {GROQ_API_KEY}",
            "Content-Type": "application/json"
        }
        
        # Build user content
        user_content = f"User's query: {query}\n\n"
        
        # Add conversation history if available
        if conversation_history and len(conversation_history) > 0:
            history_text = "Recent conversation history:\n"
            for i, msg in enumerate(conversation_history[-3:]):  # Include up to 3 recent messages
                history_text += f"User: {msg.get('query', '')}\n"
                history_text += f"Assistant: {msg.get('response', '')}\n\n"
            user_content += f"{history_text}\n"
        
        # Add context
        if context:
            user_content += f"Retrieved context information:\n{context}\n\n"
        
        # Create payload
        payload = {
            "model": "llama3-8b-8192",
            "messages": [
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_content}
            ],
            "temperature": 0.7,
            "max_tokens": 4000
        }
        
        # Make request to Groq API
        logger.info("Making request to Groq API")
        response = requests.post(GROQ_API_URL, headers=headers, json=payload, timeout=30)
        
        if response.status_code != 200:
            logger.error(f"API Error: {response.text}")
            response_data["error"] = f"API Error: {response.status_code}"
            response_data["response"] = "I'm having trouble connecting to my brain. Please try again."
            return response_data
        
        response_json = response.json()
        if "choices" not in response_json or len(response_json["choices"]) == 0:
            logger.error(f"Unexpected API response format: {response.text}")
            response_data["error"] = "Unexpected API response format"
            response_data["response"] = "I received an unexpected response. Please try again."
            return response_data
        
        result = response_json["choices"][0]["message"]["content"]
        logger.info(f"Got successful response from Groq (length: {len(result)})")
        
        response_data["response"] = result
        return response_data
        
    except Exception as e:
        logger.error(f"Error generating response: {str(e)}")
        response_data["error"] = str(e)
        response_data["response"] = "I encountered an error while processing your request. Please try again."
        return response_data