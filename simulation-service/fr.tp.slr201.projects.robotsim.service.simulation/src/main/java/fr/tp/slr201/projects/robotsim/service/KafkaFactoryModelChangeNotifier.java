package fr.tp.slr201.projects.robotsim.service;

import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import fr.tp.inf112.projects.canvas.controller.Observer;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.FactoryModelChangedNotifier;

public class KafkaFactoryModelChangeNotifier implements FactoryModelChangedNotifier {

	private static final Logger LOGGER = Logger.getLogger(KafkaFactoryModelChangeNotifier.class.getName());

	private final Factory factoryModel;
	private final KafkaTemplate<String, Factory> simulationEventTemplate;
	private final String topicName;

	public KafkaFactoryModelChangeNotifier(Factory factory, KafkaTemplate<String, Factory> kafkaTemplate) {
		this.factoryModel = factory;
		this.simulationEventTemplate = kafkaTemplate;
		this.topicName = SimulationServiceUtils.getTopicName(factoryModel);
	}

	@Override
	public void notifyObservers() {
		final Message<Factory> factoryMessage = MessageBuilder.withPayload(factoryModel)
				.setHeader(KafkaHeaders.TOPIC, topicName).build();

		final CompletableFuture<SendResult<String, Factory>> sendResult = simulationEventTemplate.send(factoryMessage);

		// Asynchronous callback for logging. Does not block.
		sendResult.whenComplete((result, ex) -> {
			if (ex != null) {
				LOGGER.log(Level.WARNING, "Failed to send async message: " + ex.getMessage());
			} else {
				LOGGER.fine("Sent factory state to " + result.getRecordMetadata().topic());
			}
		});
	}

	@Override
	public boolean addObserver(Observer observer) {
		return false;
	}

	@Override
	public boolean removeObserver(Observer observer) {
		return false;
	}
}