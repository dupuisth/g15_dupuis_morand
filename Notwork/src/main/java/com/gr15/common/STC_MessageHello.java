package com.gr15.common;

public class STC_MessageHello {
    public static final int ID = MessageSTC.HELLO.getId();

    private final String welcomeMessage;

    private STC_MessageHello(String welcomeMessage) {
        this.welcomeMessage = welcomeMessage;
    }

    public static Message CreateMessage(String welcomeMessage) {
        Message message = new Message(ID);
        message.addString(welcomeMessage);
        return message;
    }   

    public static STC_MessageHello ReadMessage(Message message) {
        String welcome = message.readString();
        return new STC_MessageHello(welcome);
    }

    public String getWelcomeMessage() {
        return welcomeMessage;
    }
}
