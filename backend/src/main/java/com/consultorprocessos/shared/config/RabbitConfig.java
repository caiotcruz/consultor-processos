package com.consultorprocessos.shared.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE_CRAWL        = "crawl.exchange";
    public static final String EXCHANGE_CRAWL_DLX    = "crawl.dlx";
    public static final String EXCHANGE_NOTIFICATION = "notification.exchange";

    public static final String ROUTING_KEY_CRAWL_REQUEST = "crawl.request";
    public static final String ROUTING_KEY_CRAWL_RETRY   = "crawl.retry";
    public static final String ROUTING_KEY_NOTIFICATION  = "notification";

    public static final String QUEUE_CRAWL_REQUESTS = "crawl.requests";
    public static final String QUEUE_CRAWL_RETRY    = "crawl.retry";
    public static final String QUEUE_CRAWL_DLQ      = "crawl.dlq";
    public static final String QUEUE_NOTIFICATIONS  = "notifications";

    @Bean
    public DirectExchange crawlExchange() {
        return new DirectExchange(EXCHANGE_CRAWL, true, false);
    }

    @Bean
    public DirectExchange crawlDlx() {
        return new DirectExchange(EXCHANGE_CRAWL_DLX, true, false);
    }

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(EXCHANGE_NOTIFICATION, true, false);
    }

    @Bean
    public Queue crawlRequestsQueue() {
        return QueueBuilder.durable(QUEUE_CRAWL_REQUESTS)
                .withArgument("x-dead-letter-exchange",    EXCHANGE_CRAWL_DLX)
                .withArgument("x-dead-letter-routing-key", "crawl.dead")
                .build();
    }

    @Bean
    public Queue crawlRetryQueue() {
        return QueueBuilder.durable(QUEUE_CRAWL_RETRY)
                .withArgument("x-dead-letter-exchange",    EXCHANGE_CRAWL)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY_CRAWL_REQUEST)
                .build();
    }

    @Bean
    public Queue crawlDlqQueue() {
        return QueueBuilder.durable(QUEUE_CRAWL_DLQ).build();
    }

    @Bean
    public Queue notificationsQueue() {
        return QueueBuilder.durable(QUEUE_NOTIFICATIONS).build();
    }

    @Bean
    public Binding bindCrawlRequests(Queue crawlRequestsQueue, DirectExchange crawlExchange) {
        return BindingBuilder.bind(crawlRequestsQueue)
                .to(crawlExchange)
                .with(ROUTING_KEY_CRAWL_REQUEST);
    }

    @Bean
    public Binding bindCrawlRetry(Queue crawlRetryQueue, DirectExchange crawlExchange) {
        return BindingBuilder.bind(crawlRetryQueue)
                .to(crawlExchange)
                .with(ROUTING_KEY_CRAWL_RETRY);
    }

    @Bean
    public Binding bindCrawlDlq(Queue crawlDlqQueue, DirectExchange crawlDlx) {
        return BindingBuilder.bind(crawlDlqQueue)
                .to(crawlDlx)
                .with("crawl.dead");
    }

    @Bean
    public Binding bindNotifications(Queue notificationsQueue,
                                     DirectExchange notificationExchange) {
        return BindingBuilder.bind(notificationsQueue)
                .to(notificationExchange)
                .with(ROUTING_KEY_NOTIFICATION);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}