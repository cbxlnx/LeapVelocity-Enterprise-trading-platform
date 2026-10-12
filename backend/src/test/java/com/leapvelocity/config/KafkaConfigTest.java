package com.leapvelocity.config;

import com.leapvelocity.messaging.ExecutionEvent;
import com.leapvelocity.messaging.OrderEvent;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("deprecation")
class KafkaConfigTest {

    private final KafkaProducerConfig producerConfig = new KafkaProducerConfig();
    private final KafkaConsumerConfig consumerConfig = new KafkaConsumerConfig();

    @Test
    void kafkaAdminUsesConfiguredBootstrapServers() {
        KafkaAdmin admin = producerConfig.kafkaAdmin("kafka:9092");

        assertEquals("kafka:9092", admin.getConfigurationProperties().get(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG));
    }

    @Test
    void producerFactorySetsExpectedReliableProducerProperties() {
        ProducerFactory<String, OrderEvent> factory = producerConfig.orderEventProducerFactory("kafka:9092");

        assertInstanceOf(DefaultKafkaProducerFactory.class, factory);
        Map<String, Object> properties = ((DefaultKafkaProducerFactory<String, OrderEvent>) factory).getConfigurationProperties();
        assertEquals("kafka:9092", properties.get(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals(StringSerializer.class, properties.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));
        assertEquals(JsonSerializer.class, properties.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        assertEquals("all", properties.get(ProducerConfig.ACKS_CONFIG));
        assertEquals(3, properties.get(ProducerConfig.RETRIES_CONFIG));
        assertEquals(true, properties.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));
        assertEquals(false, properties.get(JsonSerializer.ADD_TYPE_INFO_HEADERS));
    }

    @Test
    void kafkaTemplateUsesProvidedProducerFactory() {
        ProducerFactory<String, OrderEvent> factory = producerConfig.orderEventProducerFactory("kafka:9092");

        KafkaTemplate<String, OrderEvent> template = producerConfig.orderEventKafkaTemplate(factory);

        assertSame(factory, template.getProducerFactory());
    }

    @Test
    void consumerFactorySetsExpectedJsonDeserializerProperties() {
        ConsumerFactory<String, ExecutionEvent> factory = consumerConfig.executionEventConsumerFactory("kafka:9092");

        assertInstanceOf(DefaultKafkaConsumerFactory.class, factory);
        Map<String, Object> properties = ((DefaultKafkaConsumerFactory<String, ExecutionEvent>) factory).getConfigurationProperties();
        assertEquals("kafka:9092", properties.get(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
        assertEquals(StringDeserializer.class, properties.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
        assertEquals(JsonDeserializer.class, properties.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        assertEquals("earliest", properties.get(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
        assertEquals(ExecutionEvent.class.getName(), properties.get(JsonDeserializer.VALUE_DEFAULT_TYPE));
        assertEquals(false, properties.get(JsonDeserializer.USE_TYPE_INFO_HEADERS));
        assertTrue(String.valueOf(properties.get(JsonDeserializer.TRUSTED_PACKAGES)).contains("com.leapvelocity.messaging"));
    }

    @Test
    void listenerContainerFactoryUsesManualImmediateAcknowledgment() {
        ConsumerFactory<String, ExecutionEvent> consumerFactory = consumerConfig.executionEventConsumerFactory("kafka:9092");

        var factory = consumerConfig.kafkaListenerContainerFactory(consumerFactory);

        assertSame(consumerFactory, factory.getConsumerFactory());
        assertEquals(ContainerProperties.AckMode.MANUAL_IMMEDIATE, factory.getContainerProperties().getAckMode());
    }

    @Test
    void jwtPropertiesExposeConfiguredValues() {
        JwtProperties properties = new JwtProperties("secret", "issuer", 15);

        assertEquals("secret", properties.secret());
        assertEquals("issuer", properties.issuer());
        assertEquals(15, properties.expirationMinutes());
    }
}