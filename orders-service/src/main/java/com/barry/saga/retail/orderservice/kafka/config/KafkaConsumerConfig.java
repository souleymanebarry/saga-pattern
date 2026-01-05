package com.barry.saga.retail.orderservice.kafka.config;

import com.barry.saga.retail.orderservice.share.event.StockRejectedEvent;
import com.barry.saga.retail.orderservice.share.event.StockReservedEvent;
import lombok.extern.log4j.Log4j2;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
@Log4j2
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * common consumer properties
     */
    public Map<String, Object> baseConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // 🔑 Identité Kafka
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "orders-service-group");

        // 🔐 Offsets contrôlés par le métier
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        return props;
    }

    /* =========================================================
       🔹 Consumer: StockReservedEvent
    ========================================================= */
    @Bean
    public ConsumerFactory<String, StockReservedEvent> stockReservedConsumerFactory(){
        JsonDeserializer<StockReservedEvent> deserializer = new JsonDeserializer<>(StockReservedEvent.class);
        deserializer.addTrustedPackages("com.barry.saga.retail.orderservice.share.event");
        deserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(),
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deserializer));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, StockReservedEvent> stockReservedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, StockReservedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(stockReservedConsumerFactory());
        factory.setConcurrency(1);

        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(commonErrorHandler());
        return factory;
    }

    /* =========================================================
    🔹 Consumer: StockRejectedEvent
    ========================================================= */
    @Bean
    public ConsumerFactory<String, StockRejectedEvent> stockRejectedConsumerFactory(){
        JsonDeserializer<StockRejectedEvent> deserializer = new JsonDeserializer<>(StockRejectedEvent.class);
        deserializer.addTrustedPackages("com.barry.saga.retail.orderservice.share.event");
        deserializer.setUseTypeHeaders(false);

        return new DefaultKafkaConsumerFactory<>(
                baseConsumerProps(),
                new StringDeserializer(),
                new ErrorHandlingDeserializer<>(deserializer));
    }

   /* =========================================================
   🔹 Consumer: StockRejectedEvent
   ========================================================= */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, StockRejectedEvent>
    stockRejectedKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, StockRejectedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(stockRejectedConsumerFactory());
        factory.setConcurrency(1);

        factory.getContainerProperties()
                .setAckMode(ContainerProperties.AckMode.MANUAL);

        factory.setCommonErrorHandler(commonErrorHandler());

        return factory;
    }

    /* =========================================================
      🔹 Error handler commun
      ========================================================= */
    @Bean
    public DefaultErrorHandler commonErrorHandler() {
        return new DefaultErrorHandler((record, exception) ->
                log.error(
                        "❌ Kafka error | topic={} | partition={} | offset={}",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        exception
                )
        );
    }
}
