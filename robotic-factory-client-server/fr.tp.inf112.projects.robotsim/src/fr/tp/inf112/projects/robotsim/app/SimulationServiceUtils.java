package fr.tp.inf112.projects.robotsim.app;

import java.util.Properties;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import fr.tp.inf112.projects.robotsim.model.Factory;

/**
 * Utility class for Kafka configuration.
 *
 */
public class SimulationServiceUtils {

	public static final String BOOTSTRAP_SERVERS = "localhost:9092";
	private static final String GROUP_ID = "Factory-Simulation-Group";
	private static final String AUTO_OFFSET_RESET = "earliest";
	private static final String TOPIC_PREFIX = "simulation-";

	/**
	 * Gets the unique topic name for a given factory model.
	 *
	 * @param factoryModel The factory model.
	 * @return The Kafka topic name.
	 */
	public static String getTopicName(Factory factoryModel) {
		return TOPIC_PREFIX + factoryModel.getId();
	}

	/**
	 * Gets the default properties for a Kafka Consumer.
	 *
	 * @return A Properties object with consumer settings.
	 */
	public static Properties getDefaultConsumerProperties(String factoryId) {
		final Properties props = new Properties();
		props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
		props.put(ConsumerConfig.GROUP_ID_CONFIG, GROUP_ID);
		props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, AUTO_OFFSET_RESET);

		props.put(ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, factoryId);

		return props;
	}
}