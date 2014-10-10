package com.kjetland.dropwizard.activemq;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageProducer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ActiveMQSenderImpl implements ActiveMQSender {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final ConnectionFactory connectionFactory;
    private final ObjectMapper objectMapper;
    private final String destination;
    private final Optional<Integer> timeToLiveInSeconds;
    private final boolean persistent;
    protected final DestinationCreator destinationCreator = new DestinationCreatorImpl();


    public ActiveMQSenderImpl(ConnectionFactory connectionFactory, ObjectMapper objectMapper, String destination,
                              Optional<Integer> timeToLiveInSeconds, boolean persistent) {
        this.connectionFactory = connectionFactory;
        this.objectMapper = objectMapper;
        this.destination = destination;
        this.timeToLiveInSeconds = timeToLiveInSeconds;
        this.persistent = persistent;
    }

    @Override
    public void send(Object object) {
        try {

            final String json = objectMapper.writeValueAsString(object);
            internalSend(json);

        } catch (Exception e) {
            throw new RuntimeException("Error sending to jms", e);
        }

    }
    
    @Override
    public void send(byte[] object, Map<String, String> properties) {
        try {
            internalSend(object, properties);
        } catch (Exception e) {
            throw new RuntimeException("Error sending to jms", e);
        }
    }

    @Override
    public void sendJson(String json) {
        try {
            internalSend(json);
        } catch (Exception e) {
            throw new RuntimeException("Error sending to jms", e);
        }

    }

    private void internalSend(String json) throws JMSException {
        log.info("Sending to {}: {}", destination, json);
        internalSend( session -> {
            final TextMessage textMessage = session.createTextMessage(json);
            textMessage.setText(json);
            return textMessage;
        } );
    }

    private void internalSend(byte[] bytes, Map<String, String> properties) throws JMSException {
        log.info("Sending to {}: {}", destination, bytes);
        internalSend( session -> {
            BytesMessage bytesMessage = session.createBytesMessage();
            bytesMessage.writeBytes(bytes);
            if( properties != null && !properties.isEmpty() ) {
                properties.forEach((k, v) -> {
                    try {
                        bytesMessage.setStringProperty(k,  v);
                    } catch( JMSException e ) {
                        throw new RuntimeException("Error set properties string to bytesMessage", e);
                    }
                });
            }
            return bytesMessage;
        } );
    }

    private void internalSend(JMSFunction<Session, Message> messageCreator) throws JMSException {

        // Since we're using the pooled connectionFactory,
        // we can create connection, session and producer on the fly here.
        // as long as we do the cleanup / return to pool

        final Connection connection = connectionFactory.createConnection();
        try {

            final Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            try {

                final Destination d = destinationCreator.create(session, destination);
                final MessageProducer messageProducer = session.createProducer(d);
                try {
                    messageProducer.setDeliveryMode(persistent ? DeliveryMode.PERSISTENT : DeliveryMode.NON_PERSISTENT);
                    if (timeToLiveInSeconds.isPresent()) {
                        messageProducer.setTimeToLive(TimeUnit.SECONDS.toMillis(timeToLiveInSeconds.get()));
                    }

                    final Message message = messageCreator.apply(session);
                    messageProducer.send(message);

                } finally {
                    ActiveMQUtils.silent(() -> messageProducer.close());
                }
            } finally {
                ActiveMQUtils.silent(() -> session.close());
            }

        } finally {
            ActiveMQUtils.silent(() -> connection.close());
        }


    }

    @Override
    public void send(JMSFunction<Session, Message> messageCreator) {
        // Since we're using the pooled connectionFactory,
        // we can create connection, session and producer on the fly here.
        // as long as we do the cleanup / return to pool

        try {
            internalSend(messageCreator);
        } catch ( JMSException jmsException) {
            throw new RuntimeException("Error sending to jms", jmsException);
        }
    }
}
