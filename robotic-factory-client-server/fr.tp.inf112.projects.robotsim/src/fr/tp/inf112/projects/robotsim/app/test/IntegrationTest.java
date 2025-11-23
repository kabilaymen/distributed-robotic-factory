package fr.tp.inf112.projects.robotsim.app.test;

import java.util.logging.Logger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import fr.tp.inf112.projects.robotsim.model.Area;
import fr.tp.inf112.projects.robotsim.model.Battery;
import fr.tp.inf112.projects.robotsim.model.Door;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.Machine;
import fr.tp.inf112.projects.robotsim.model.RemoteFactoryPersistenceManager;
import fr.tp.inf112.projects.robotsim.model.Robot;
import fr.tp.inf112.projects.robotsim.model.Room;
import fr.tp.inf112.projects.robotsim.model.path.CustomDijkstraFactoryPathFinder;
import fr.tp.inf112.projects.robotsim.model.shapes.CircularShape;
import fr.tp.inf112.projects.robotsim.model.shapes.RectangularShape;

/**
 * Integration tests that verify the complete system works correctly when all
 * components are running.
 * 
 * Prerequisites: 1. FactoryPersistenceServer must be running on port 8090 2.
 * Simulation microservice must be running on port 8080
 * 
 * To run the servers: 1. Start FactoryPersistenceServer: java
 * fr.tp.inf112.projects.robotsim.server.FactoryPersistenceServer 8090
 * test_factories 2. Start Spring Boot simulation service: mvn spring-boot:run
 * (or run SimulationServiceApplication)
 */
public class IntegrationTest {

	private static final Logger LOGGER = Logger.getLogger(IntegrationTest.class.getName());
	private static final String TEST_HOST = "localhost";
	private static final int TEST_PORT = 8090;
	private static final String TEST_FACTORY_ID = "integration_test.factory";

	private RemoteFactoryPersistenceManager persistenceManager;
	private Factory testFactory;

	@Before
	public void setUp() {
		LOGGER.info("=== Starting Integration Test Setup ===");

		persistenceManager = new RemoteFactoryPersistenceManager(null, TEST_HOST, TEST_PORT);
		testFactory = createTestFactory();
		testFactory.setId(TEST_FACTORY_ID);

		LOGGER.info("Test factory created with ID: " + TEST_FACTORY_ID);
	}

	@After
	public void tearDown() {
		LOGGER.info("=== Integration Test Teardown ===");
	}

	@Test
	public void testServerConnection() {
		LOGGER.info("Test 1: Testing server connection...");

		boolean connected = persistenceManager.testConnection();

		if (!connected) {
			fail("Cannot connect to persistence server at " + TEST_HOST + ":" + TEST_PORT
					+ "\n\nPlease start FactoryPersistenceServer first:\n"
					+ "java fr.tp.inf112.projects.robotsim.server.FactoryPersistenceServer 8090 test_factories");
		}

		LOGGER.info("Test 1: PASSED - Connected to server");
	}

	@Test
	public void testPersistAndRead() {
		LOGGER.info("Test 2: Testing persist and read...");

		try {
			// Test connection first
			if (!persistenceManager.testConnection()) {
				fail("Server not available. Please start FactoryPersistenceServer.");
			}

			// Persist factory
			persistenceManager.persist(testFactory);
			LOGGER.info("Factory persisted successfully");

			// Read it back
			Factory loadedFactory = (Factory) persistenceManager.read(TEST_FACTORY_ID);
			assertNotNull("Loaded factory should not be null", loadedFactory);

			assertEquals("Factory names should match", testFactory.getName(), loadedFactory.getName());
			assertEquals("Factory IDs should match", testFactory.getId(), loadedFactory.getId());
			assertEquals("Component counts should match", testFactory.getComponents().size(),
					loadedFactory.getComponents().size());

			LOGGER.info("Test 2: PASSED - Factory persisted and read correctly");

		} catch (Exception e) {
			LOGGER.severe("Test 2 FAILED: " + e.getMessage());
			fail("Persist/Read test failed: " + e.getMessage());
		}
	}

	@Test
	public void testListFiles() {
		LOGGER.info("Test 3: Testing file listing...");

		try {
			if (!persistenceManager.testConnection()) {
				fail("Server not available");
			}

			// First persist a factory
			persistenceManager.persist(testFactory);

			// List files
			String[] files = persistenceManager.listFactoryFiles();
			assertNotNull("File list should not be null", files);
			assertTrue("File list should not be empty", files.length > 0);

			// Check our file is in the list
			boolean found = false;
			for (String file : files) {
				if (file.equals(TEST_FACTORY_ID)) {
					found = true;
					break;
				}
			}
			assertTrue("Our test factory should be in the list", found);

			LOGGER.info("Test 3: PASSED - Found " + files.length + " files including our test factory");

		} catch (Exception e) {
			LOGGER.severe("Test 3 FAILED: " + e.getMessage());
			fail("List files test failed: " + e.getMessage());
		}
	}

	@Test
	public void testReadNonExistentFile() {
		LOGGER.info("Test 4: Testing error handling for non-existent file...");

		try {
			if (!persistenceManager.testConnection()) {
				fail("Server not available");
			}

			persistenceManager.read("non_existent_file.factory");
			fail("Should have thrown IOException for non-existent file");

		} catch (Exception e) {
			LOGGER.info("Test 4: PASSED - Correctly threw exception: " + e.getMessage());
		}
	}

	@Test
	public void testPersistWithoutId() {
		LOGGER.info("Test 5: Testing error handling for factory without ID...");

		try {
			if (!persistenceManager.testConnection()) {
				fail("Server not available");
			}

			Factory factoryWithoutId = new Factory(50, 50, "No ID Factory");
			// Don't set ID

			persistenceManager.persist(factoryWithoutId);
			fail("Should have thrown IOException for factory without ID");

		} catch (Exception e) {
			// Expected exception
			assertTrue("Exception message should mention ID", e.getMessage().toLowerCase().contains("id"));
			LOGGER.info("Test 5: PASSED - Correctly rejected factory without ID");
		}
	}

	@Test
	public void testMultiplePersistOperations() {
		LOGGER.info("Test 6: Testing multiple persist operations...");

		try {
			if (!persistenceManager.testConnection()) {
				fail("Server not available");
			}

			// Create and persist multiple factories
			for (int i = 1; i <= 3; i++) {
				Factory factory = createTestFactory();
				factory.setId("multi_test_" + i + ".factory");
				persistenceManager.persist(factory);
				LOGGER.info("Persisted factory " + i);
			}

			// Verify all were saved
			String[] files = persistenceManager.listFactoryFiles();
			int foundCount = 0;
			for (String file : files) {
				if (file.startsWith("multi_test_")) {
					foundCount++;
				}
			}

			assertTrue("Should find all 3 test factories", foundCount >= 3);
			LOGGER.info("Test 6: PASSED - All " + foundCount + " factories saved correctly");

		} catch (Exception e) {
			LOGGER.severe("Test 6 FAILED: " + e.getMessage());
			fail("Multiple persist test failed: " + e.getMessage());
		}
	}

	private Factory createTestFactory() {
		Factory factory = new Factory(100, 100, "Integration Test Factory");

		Room room1 = new Room(factory, new RectangularShape(10, 10, 40, 40), "Room 1");
		new Door(room1, Room.WALL.BOTTOM, 5, 10, true, "Door 1");

		Area area1 = new Area(room1, new RectangularShape(15, 15, 30, 30), "Area 1");
		new Machine(area1, new RectangularShape(25, 25, 10, 10), "Machine 1");

		CustomDijkstraFactoryPathFinder pathFinder = new CustomDijkstraFactoryPathFinder(factory, 5);
		pathFinder.init();

		new Robot(factory, pathFinder, new CircularShape(5, 5, 2), new Battery(10), "Robot 1");

		return factory;
	}
}