# Wellness and Resilience Diagnostic AI

![Python](https://img.shields.io/badge/python-3.10+-blue.svg)
![License](https://img.shields.io/badge/license-MIT-green.svg)
![Stars](https://img.shields.io/github/stars/rohmatalpur/Wellness-and-Resilience-Diagnostic-AI?style=social)
![Forks](https://img.shields.io/github/forks/rohmatalpur/Wellness-and-Resilience-Diagnostic-AI?style=social)

An AI-based virtual therapist that diagnoses and responds to user emotions in real-time conversations using a combination of **retrieval-augmented generation (RAG)** and **Meta’s LLaMA 3 8B 8192** model.

---

##  Project Overview

- **Frontend**: Java+XML using Android Studio 
- **Backend**: Python 
- **Model**: LLaMA 3 8B with Retrieval-Augmented Generation (RAG) 
- **Dataset**: [DAIC-WOZ](https://dcapswoz.ict.usc.edu/wwwdaicwoz/) 
  → 189 conversations and 47,000+ utterances used to train emotion detection models

---

## ✨ Features

- Emotion-aware AI chatbot that responds empathetically to users
- Detects emotions in user text input using fine-tuned deep learning models
- Mobile interface to interact with the virtual therapist
- Session history tracking and real-time storage in cloud database (e.g., Firebase)
- Supports local development via XAMPP server

---

## 📁 Folder Structure

Wellness-and-Resilience-Diagnostic-AI/
├── warda-backend/ # Python backend: LLaMA model, RAG, emotion detection
│ └── requirements.txt
├── Warda_Therapist/ # Android Studio app: mobile frontend
├── .gitattributes # Git LFS configuration
└── README.md

## 🚀 Getting Started

###  Backend Setup (Python)

    ```bash
    cd warda-backend
    python3 -m venv venv
    source venv/bin/activate
    pip install -r requirements.txt


###  Frontend Setup (Android)

    Open the Warda_Therapist/ folder in Android Studio

###  Local Server Setup (XAMPP)

    Download and install XAMPP

    Start Apache and MySQL from the XAMPP control panel

    Use localhost for API testing if the backend runs locally

    Optionally use ngrok or Firebase to expose the backend to your mobile app
