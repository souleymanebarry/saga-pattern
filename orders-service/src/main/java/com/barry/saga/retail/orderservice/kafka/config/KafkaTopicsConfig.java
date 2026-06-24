package com.barry.saga.retail.orderservice.kafka.config;

import lombok.Getter;
import lombok.Setter;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConfigurationProperties(prefix = "spring.kafka")
@Getter
@Setter
public class KafkaTopicsConfig {

    // Un seul broker en local (cf. docker-compose) → réplication = 1.
    private static final int PARTITIONS = 3;
    private static final short REPLICAS = 1;

    private Topics topics = new Topics();

    @Getter @Setter
    public static class Topics {
        private String orderPlaced;
        private String stockReserved;
        private String stockRejected;
        private String orderConfirmed;
        private String orderFailed;
    }

    // ------------------------------------------------------------------
    // Provisioning des topics du domaine "order" (orders-service = owner).
    // Les topics "stock.*" sont créés par le stock-service (autre micro-service),
    // car c'est lui qui les produit. La clé Kafka = orderId garantit l'ordre
    // par commande quel que soit le nombre de partitions.
    // ------------------------------------------------------------------

    @Bean
    public NewTopic orderPlacedTopic() {
        return TopicBuilder.name(topics.getOrderPlaced())
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic orderConfirmedTopic() {
        return TopicBuilder.name(topics.getOrderConfirmed())
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }

    @Bean
    public NewTopic orderFailedTopic() {
        return TopicBuilder.name(topics.getOrderFailed())
                .partitions(PARTITIONS)
                .replicas(REPLICAS)
                .build();
    }
}
