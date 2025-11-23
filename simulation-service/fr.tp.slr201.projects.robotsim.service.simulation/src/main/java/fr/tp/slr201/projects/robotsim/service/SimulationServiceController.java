package fr.tp.slr201.projects.robotsim.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import fr.tp.inf112.projects.robotsim.model.Component;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.RemoteFactoryPersistenceManager;
import fr.tp.inf112.projects.robotsim.model.Robot;
import fr.tp.inf112.projects.robotsim.model.path.CustomDijkstraFactoryPathFinder;
import fr.tp.inf112.projects.robotsim.model.path.FactoryPathFinder;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.kafka.support.KafkaHeaders;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.kafka.core.KafkaTemplate;

@RestController
@RequestMapping("/simulation")
public class SimulationServiceController {

	private static final Logger LOGGER = Logger.getLogger(SimulationServiceController.class.getName());
	private final Map<String, Factory> activeSimulations = new ConcurrentHashMap<>();
	private final Map<String, FactoryPathFinder> customPathFinderCache = new ConcurrentHashMap<>();
	private final RemoteFactoryPersistenceManager persistenceManager;

	@Autowired
	private KafkaTemplate<String, Factory> simulationEventTemplate;

	public SimulationServiceController() {
		String persistenceHost = System.getenv("PERSISTENCE_HOST");
		if (persistenceHost == null)
			persistenceHost = "localhost";
		int persistencePort = 8090;
		this.persistenceManager = new RemoteFactoryPersistenceManager(null, persistenceHost, persistencePort);
	}

	@GetMapping("/start/{factoryId}")
	public ResponseEntity<String> prepareSimulation(@PathVariable String factoryId) {
		try {
			if (activeSimulations.containsKey(factoryId)) {
				// Resend state for reconnecting clients
				sendFactoryState(activeSimulations.get(factoryId));
				return ResponseEntity.ok("Simulation prepared (existing)");
			}

			final Factory finalFactory = (Factory) persistenceManager.read(factoryId);
			if (finalFactory == null)
				return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Factory not found");

			CompletableFuture.runAsync(() -> {
				try {
					FactoryPathFinder pf = customPathFinderCache.computeIfAbsent(factoryId, k -> {
						CustomDijkstraFactoryPathFinder newPf = new CustomDijkstraFactoryPathFinder(finalFactory, 5);
						newPf.init();
						return newPf;
					});
					for (Component c : finalFactory.getComponents()) {
						if (c instanceof Robot)
							((Robot) c).setPathFinder(pf);
					}

					finalFactory
							.setNotifier(new KafkaFactoryModelChangeNotifier(finalFactory, simulationEventTemplate));
					activeSimulations.put(factoryId, finalFactory);

					// Send T=0, but DO NOT START THREADS
					sendFactoryState(finalFactory);

				} catch (Exception e) {
					LOGGER.log(Level.SEVERE, "Setup failed", e);
					activeSimulations.remove(factoryId);
				}
			});

			return ResponseEntity.ok("Simulation prepared");
		} catch (Exception e) {
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(e.getMessage());
		}
	}

	private void sendFactoryState(Factory factory) {
		Message<Factory> message = MessageBuilder.withPayload(factory)
				.setHeader(KafkaHeaders.TOPIC, SimulationServiceUtils.getTopicName(factory)).build();
		simulationEventTemplate.send(message);
	}

	@GetMapping("/run/{factoryId}")
	public ResponseEntity<String> runSimulation(@PathVariable String factoryId) {
		Factory factory = activeSimulations.get(factoryId);
		if (factory != null && !factory.isSimulationStarted()) {
			factory.startSimulation();
			return ResponseEntity.ok("Running");
		}
		return ResponseEntity.ok("Already running or not found");
	}

	@GetMapping("/stop/{factoryId}")
	public ResponseEntity<String> stopSimulation(@PathVariable String factoryId) {
		Factory factory = activeSimulations.get(factoryId);
		if (factory != null)
			factory.stopSimulation();
		return ResponseEntity.ok("Stopped");
	}

	@DeleteMapping("/reset/{factoryId}")
	public ResponseEntity<String> resetSimulation(@PathVariable String factoryId) {
		Factory factory = activeSimulations.remove(factoryId);
		if (factory != null)
			factory.stopSimulation();

		// Delete topic to prevent history replay on next run
		Properties config = new Properties();
		config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, SimulationServiceUtils.BOOTSTRAP_SERVERS);
		try (AdminClient admin = AdminClient.create(config)) {
			admin.deleteTopics(Collections.singleton("simulation-" + factoryId));
		} catch (Exception e) {
		}

		return ResponseEntity.ok("Reset complete");
	}
}