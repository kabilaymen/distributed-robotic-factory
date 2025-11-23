package fr.tp.inf112.projects.robotsim.app;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.io.IOException;

import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.serialization.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

import fr.tp.inf112.projects.robotsim.model.*;
import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;
import fr.tp.inf112.projects.canvas.model.impl.BasicVertex;

class FactoryDeserializer implements Deserializer<Factory> {
	private final ObjectMapper objectMapper;

	public FactoryDeserializer(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	@Override
	public Factory deserialize(String topic, byte[] data) {
		if (data == null)
			return null;
		try {
			return objectMapper.readValue(new String(data, "UTF-8"), Factory.class);
		} catch (IOException e) {
			return null;
		}
	}
}

public class FactorySimulationEventConsumer {

	private static final Logger LOGGER = Logger.getLogger(FactorySimulationEventConsumer.class.getName());
	private final KafkaConsumer<String, Factory> consumer;
	private final String topicName;
	private final RemoteSimulatorController controller;

	private final Runnable onListeningCallback;
	private final Runnable onFirstFrameCallback;

	private volatile boolean running = true;
	private boolean isListening = false;
	private boolean hasReceivedFrame = false;

	public FactorySimulationEventConsumer(RemoteSimulatorController controller, String factoryId,
			Runnable onListeningCallback, Runnable onFirstFrameCallback) {

		this.controller = controller;
		this.topicName = "simulation-" + factoryId;
		this.onListeningCallback = onListeningCallback;
		this.onFirstFrameCallback = onFirstFrameCallback;

		BasicPolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
				.allowIfSubType(Component.class.getPackageName()).allowIfSubType(PositionedShape.class.getPackageName())
				.allowIfSubType(BasicVertex.class.getPackageName()).allowIfSubType(ArrayList.class.getName())
				.allowIfSubType(LinkedHashSet.class.getName()).build();

		ObjectMapper mapper = new ObjectMapper();
		mapper.activateDefaultTyping(ptv, ObjectMapper.DefaultTyping.NON_FINAL);

		Properties props = SimulationServiceUtils.getDefaultConsumerProperties(factoryId);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, "viewer-" + UUID.randomUUID().toString());
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
		props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);

		this.consumer = new KafkaConsumer<>(props, new StringDeserializer(), new FactoryDeserializer(mapper));
		LOGGER.info("Consumer initialized for topic: " + topicName);
	}

	public void consumeMessages() {
		try {
			consumer.subscribe(Collections.singletonList(topicName));

			while (running) {
				ConsumerRecords<String, Factory> records = consumer.poll(Duration.ofMillis(100));

				if (!isListening && !consumer.assignment().isEmpty()) {
					isListening = true;
					LOGGER.info("Kafka Connected. Requesting Prepare...");
					if (onListeningCallback != null)
						onListeningCallback.run();
				}

				if (records.isEmpty())
					continue;

				for (ConsumerRecord<String, Factory> record : records) {
					if (record.value() == null)
						continue;

					controller.updateCanvasDuringAnimation(record.value());

					if (!hasReceivedFrame) {
						hasReceivedFrame = true;
						LOGGER.info("First Frame Received. Requesting Run...");
						if (onFirstFrameCallback != null)
							onFirstFrameCallback.run();
						controller.simulationDidStart();
					}
				}
			}
		} catch (Exception e) {
			if (running)
				LOGGER.log(Level.SEVERE, "Consumer error", e);
		} finally {
			consumer.close();
		}
	}

	public void stop() {
		running = false;
		consumer.wakeup();
	}
}