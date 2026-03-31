package com.example.notification.messaging.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

	public static final String NOTIFICATION_EXCHANGE = "notification.exchange";
	public static final String EMAIL_QUEUE = "notification.email.queue";
	public static final String EMAIL_DLQ = "notification.email.dlq";
	public static final String EMAIL_ROUTING_PATTERN = "notification.email.*";
	public static final String EMAIL_DLQ_ROUTING_KEY = "notification.email.dlq";

	@Bean
	public TopicExchange notificationExchange() {
		return ExchangeBuilder.topicExchange(NOTIFICATION_EXCHANGE).durable(true).build();
	}

	@Bean
	public Queue emailQueue() {
		Map<String, Object> args = new HashMap<>();
		args.put("x-dead-letter-exchange", NOTIFICATION_EXCHANGE);
		args.put("x-dead-letter-routing-key", EMAIL_DLQ_ROUTING_KEY);
		return QueueBuilder.durable(EMAIL_QUEUE).withArguments(args).build();
	}

	@Bean
	public Queue emailDlq() {
		return QueueBuilder.durable(EMAIL_DLQ).build();
	}

	@Bean
	public Binding emailBinding() {
		return BindingBuilder.bind(emailQueue())
				.to(notificationExchange())
				.with(EMAIL_ROUTING_PATTERN);
	}

	@Bean
	public Binding dlqBinding() {
		return BindingBuilder.bind(emailDlq())
				.to(notificationExchange())
				.with(EMAIL_DLQ_ROUTING_KEY);
	}

	@Bean
	public MessageConverter messageConverter() {
		return new Jackson2JsonMessageConverter();
	}

	@Bean
	public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
			ConnectionFactory connectionFactory,
			RabbitTemplate rabbitTemplate,
			MessageConverter messageConverter,
			@Value("${spring.rabbitmq.listener.simple.auto-startup:true}") boolean listenerAutoStartup) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setAutoStartup(listenerAutoStartup);
		factory.setDefaultRequeueRejected(false);
		factory.setMessageConverter(messageConverter);
		factory.setAdviceChain(
				RetryInterceptorBuilder.stateless()
						.maxAttempts(3)
						.recoverer(new RepublishMessageRecoverer(
								rabbitTemplate,
								NOTIFICATION_EXCHANGE,
								EMAIL_DLQ_ROUTING_KEY))
						.build());
		return factory;
	}
}

