package com.example.notification.messaging.util;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.GetResponse;

import lombok.extern.slf4j.Slf4j;

/**
 * Utility to inspect RabbitMQ queues. Uses basic.get + nack(requeue=true)
 * to peek messages without permanently removing them.
 */
@Slf4j
@Component
public class RabbitQueueInspector {

    private final RabbitTemplate rabbitTemplate;

    public RabbitQueueInspector(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /**
     * Peek up to {@code maxMessages} from the queue without removing them.
     * This uses basic.get (noAck=false) and immediately nacks with requeue=true
     * so messages are returned to the ready state.
     *
     * Returns the message bodies as UTF-8 strings in queue order (oldest first).
     */
    public List<String> peekMessages(String queueName, int maxMessages) {
        if (maxMessages <= 0) {
            return List.of();
        }

        try {
            return rabbitTemplate.execute(channel -> {
                List<String> messages = new ArrayList<>();
                for (int i = 0; i < maxMessages; i++) {
                    GetResponse response = channel.basicGet(queueName, false);
                    if (response == null) {
                        break;
                    }

                    byte[] body = response.getBody();
                    String payload = body == null ? "" : new String(body, StandardCharsets.UTF_8);
                    messages.add(payload);

                    long tag = response.getEnvelope().getDeliveryTag();
                    try {
                        // Requeue the message so we don't remove it from the queue
                        channel.basicNack(tag, false, true);
                    } catch (IOException e) {
                        log.warn("Failed to basicNack message on queue {} (tag={}), attempting reject+requeue", queueName, tag, e);
                        try {
                            channel.basicReject(tag, true);
                        } catch (IOException ex) {
                            log.error("Failed to requeue message (tag={}) on queue {}", tag, queueName, ex);
                        }
                    }
                }
                return messages;
            });
        } catch (Exception ex) {
            log.error("Failed to peek messages from queue {}", queueName, ex);
            return List.of();
        }
    }

    /**
     * Get the number of ready messages for the given queue.
     * This uses a passive queue declare which does not modify queue contents.
     */
    public int getReadyMessageCount(String queueName) {
        try {
            Integer count = rabbitTemplate.execute(channel -> {
                AMQP.Queue.DeclareOk ok = channel.queueDeclarePassive(queueName);
                return ok.getMessageCount();
            });
            return count == null ? 0 : count;
        } catch (Exception ex) {
            log.error("Failed to get ready message count for queue {}", queueName, ex);
            return 0;
        }
    }
}
