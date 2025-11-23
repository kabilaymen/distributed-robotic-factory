package fr.tp.inf112.projects.robotsim.server.test;

import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Logger;

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
 * Test class to verify remote persistence functionality. Make sure
 * FactoryPersistenceServer is running before running this test.
 * 
 * To run the server first: java
 * fr.tp.inf112.projects.robotsim.server.FactoryPersistenceServer 8090
 * server_factories
 */
public class PersistenceTest {

	private static final Logger LOGGER = Logger.getLogger(PersistenceTest.class.getName());

	private static final String SERVER_HOST = "localhost";
	private static final int SERVER_PORT = 8090;

	public static void main(String[] args) {
		LOGGER.info("=== Starting Remote Persistence Test ===");
		LOGGER.info("Make sure the server is running on " + SERVER_HOST + ":" + SERVER_PORT);
		LOGGER.info("");

		try {
			testPersistence();
			LOGGER.info("");
			LOGGER.info("=== All tests passed! ===");
		} catch (Exception e) {
			LOGGER.severe("Test failed: " + e.getMessage());
			e.printStackTrace();
			System.exit(1);
		}
	}

	private static void testPersistence() throws IOException {
		// Create manager with null CanvasChooser (not needed for testing)
		RemoteFactoryPersistenceManager manager = new RemoteFactoryPersistenceManager(null, SERVER_HOST, SERVER_PORT);

		// Test 1: Create and save a factory model
		LOGGER.info("Test 1: Creating factory model...");
		Factory factory = createTestFactory();
		factory.setId("test_factory.factory");

		LOGGER.info("Test 1: Factory created with " + factory.getComponents().size() + " components");
		LOGGER.info("Test 1: Factory ID: " + factory.getId());

		LOGGER.info("Test 1: Saving factory to server...");
		try {
			manager.persist(factory);
			LOGGER.info("Test 1: PASSED - Factory saved successfully");
			LOGGER.info("Test 1: Factory ID after save: " + factory.getId());
		} catch (IOException e) {
			LOGGER.severe("Test 1: FAILED - " + e.getMessage());
			throw e;
		}
		LOGGER.info("");

		// Test 2: List available files
		LOGGER.info("Test 2: Listing available files...");
		String[] files = null;
		try {
			files = manager.listFactoryFiles();
			LOGGER.info("Test 2: Found " + files.length + " files: " + Arrays.toString(files));
		} catch (IOException e) {
			LOGGER.severe("Test 2: FAILED - " + e.getMessage());
			throw e;
		}

		boolean found = false;
		for (String file : files) {
			if (file.equals("test_factory.factory")) {
				found = true;
				break;
			}
		}

		if (!found) {
			throw new RuntimeException("Test 2: FAILED - Saved file not found in list");
		}
		LOGGER.info("Test 2: PASSED - File appears in list");
		LOGGER.info("");

		// Test 3: Read the factory back
		LOGGER.info("Test 3: Reading factory from server...");
		Factory loadedFactory = null;
		try {
			loadedFactory = (Factory) manager.read("test_factory.factory");
		} catch (IOException e) {
			LOGGER.severe("Test 3: FAILED - " + e.getMessage());
			throw e;
		}

		if (loadedFactory == null) {
			throw new RuntimeException("Test 3: FAILED - Loaded factory is null");
		}

		LOGGER.info("Test 3: Loaded factory: " + loadedFactory.getName());
		LOGGER.info("Test 3: Loaded factory ID: " + loadedFactory.getId());
		LOGGER.info("Test 3: Components count: " + loadedFactory.getComponents().size());

		if (!loadedFactory.getName().equals(factory.getName())) {
			throw new RuntimeException("Test 3: FAILED - Factory names don't match. Expected: " + factory.getName()
					+ ", Got: " + loadedFactory.getName());
		}

		if (loadedFactory.getComponents().size() != factory.getComponents().size()) {
			throw new RuntimeException("Test 3: FAILED - Component counts don't match. Expected: "
					+ factory.getComponents().size() + ", Got: " + loadedFactory.getComponents().size());
		}

		// Verify the ID is correct (should be simple filename, not absolute path)
		if (!loadedFactory.getId().equals("test_factory.factory")) {
			throw new RuntimeException("Test 3: FAILED - Factory ID incorrect. Expected: test_factory.factory, Got: "
					+ loadedFactory.getId());
		}

		LOGGER.info("Test 3: PASSED - Factory loaded correctly");
		LOGGER.info("");

		// Test 4: Save another factory with different name
		LOGGER.info("Test 4: Saving second factory...");
		Factory factory2 = createTestFactory();
		factory2.setId("test_factory2.factory");

		try {
			manager.persist(factory2);
			LOGGER.info("Test 4: Second factory saved successfully");
		} catch (IOException e) {
			LOGGER.severe("Test 4: FAILED - " + e.getMessage());
			throw e;
		}

		try {
			files = manager.listFactoryFiles();
			LOGGER.info("Test 4: Now have " + files.length + " files: " + Arrays.toString(files));
		} catch (IOException e) {
			LOGGER.severe("Test 4: FAILED - " + e.getMessage());
			throw e;
		}

		if (files.length < 2) {
			throw new RuntimeException("Test 4: FAILED - Should have at least 2 files, but found: " + files.length);
		}

		LOGGER.info("Test 4: PASSED - Multiple factories can be saved");
		LOGGER.info("");

		// Test 5: Read second factory
		LOGGER.info("Test 5: Reading second factory from server...");
		Factory loadedFactory2 = null;
		try {
			loadedFactory2 = (Factory) manager.read("test_factory2.factory");
		} catch (IOException e) {
			LOGGER.severe("Test 5: FAILED - " + e.getMessage());
			throw e;
		}

		if (loadedFactory2 == null) {
			throw new RuntimeException("Test 5: FAILED - Loaded factory2 is null");
		}

		if (!loadedFactory2.getId().equals("test_factory2.factory")) {
			throw new RuntimeException("Test 5: FAILED - Factory2 ID incorrect. Expected: test_factory2.factory, Got: "
					+ loadedFactory2.getId());
		}

		LOGGER.info("Test 5: PASSED - Second factory loaded correctly");
		LOGGER.info("");

		// Test 6: Test error handling - try to read non-existent file
		LOGGER.info("Test 6: Testing error handling with non-existent file...");
		try {
			manager.read("non_existent.factory");
			throw new RuntimeException("Test 6: FAILED - Should have thrown IOException for non-existent file");
		} catch (IOException e) {
			LOGGER.info("Test 6: Correctly caught exception: " + e.getMessage());
			LOGGER.info("Test 6: PASSED - Error handling works correctly");
		}
		LOGGER.info("");
	}

	private static Factory createTestFactory() {
		Factory factory = new Factory(100, 100, "Test Factory");

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