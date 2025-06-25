package com.example.warda_therapist;

public class MessageModel {

    static String SENT_BY_ME = "me";
    static String SENT_BY_BOT = "bot";

    String message;
    String sentBy;

    public MessageModel(String message, String sentBy) {
        this.message = message;
        this.sentBy = sentBy;
    }

    public String getMessage() {
        return message;
    }

    public String getSentBy() {
        return sentBy;
    }
}