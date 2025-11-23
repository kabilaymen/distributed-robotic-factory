package fr.tp.slr201.projects.robotsim.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import fr.tp.inf112.projects.robotsim.model.Component;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;
import fr.tp.inf112.projects.canvas.model.impl.BasicVertex;

import java.util.ArrayList;
import java.util.LinkedHashSet;

@Configuration
public class SimulationRegisterModuleConfig {

    @Bean
    @Primary
    ObjectMapper objectMapper() {
		final PolymorphicTypeValidator typeValidator = BasicPolymorphicTypeValidator.builder()
				.allowIfSubType(PositionedShape.class.getPackageName()).allowIfSubType(Component.class.getPackageName())
				.allowIfSubType(BasicVertex.class.getPackageName()).allowIfSubType(ArrayList.class.getName())
				.allowIfSubType(LinkedHashSet.class.getName()).build();

		final ObjectMapper objectMapper = new ObjectMapper();

		objectMapper.activateDefaultTyping(typeValidator, ObjectMapper.DefaultTyping.NON_FINAL);

		return objectMapper;
	}

    /**
     * Configures the Kafka Producer Factory.
     *
     */
    @Bean
    ProducerFactory<String, Factory> producerFactory() {
		final Map<String, Object> config = new HashMap<>();
		// Set the Kafka broker address
		config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SimulationServiceUtils.BOOTSTRAP_SERVERS);

		// Create a JSON serializer for the Factory object, using our custom ObjectMapper
		final JsonSerializer<Factory> factorySerializer = new JsonSerializer<>(objectMapper());

		// Create the producer factory
		return new DefaultKafkaProducerFactory<>(config, new StringSerializer(),
				factorySerializer);
	}

    /**
     * Creates the KafkaTemplate bean for sending messages.
     *
     */
    @Bean
    @Primary
    KafkaTemplate<String, Factory> kafkaTemplate() {
		return new KafkaTemplate<>(producerFactory());
	}
}