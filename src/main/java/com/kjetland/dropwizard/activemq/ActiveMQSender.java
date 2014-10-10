package com.kjetland.dropwizard.activemq;

import java.util.Map;

import javax.jms.Message;
import javax.jms.Session;

public interface ActiveMQSender {

    void sendJson(String json);
    void send(Object object);
    void send(JMSFunction<Session, Message> messageCreator);
    void send(byte[] object, Map<String, String> properties);

}
