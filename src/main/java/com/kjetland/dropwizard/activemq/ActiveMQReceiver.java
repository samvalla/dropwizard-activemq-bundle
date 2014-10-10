package com.kjetland.dropwizard.activemq;

import javax.jms.Message;

public interface ActiveMQReceiver<T> {
    public void receive(Message jmsMessage, T content, byte[] file);
}
