package fr.tp.inf112.projects.robotsim.app.test;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import fr.tp.inf112.projects.robotsim.app.RemoteSimulatorController;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.RemoteFactoryPersistenceManager;
import fr.tp.inf112.projects.robotsim.model.Room;
import fr.tp.inf112.projects.robotsim.model.shapes.RectangularShape;

/**
 * Unit tests for RemoteSimulatorController
 */
public class RemoteSimulatorControllerTest {

	private Factory testFactory;
	private RemoteFactoryPersistenceManager mockPersistenceManager;
	private RemoteSimulatorController controller;

	@Before
	public void setUp() {
		testFactory = new Factory(100, 100, "Test Factory");
		new Room(testFactory, new RectangularShape(10, 10, 30, 30), "Test Room");
		testFactory.setId("test_factory.factory");

		mockPersistenceManager = new RemoteFactoryPersistenceManager(null, "localhost", 9999);

		controller = new RemoteSimulatorController(testFactory, mockPersistenceManager);
	}

	@Test
	public void testInitialState() {
		assertNotNull("Controller should not be null", controller);
		assertNotNull("Canvas should not be null", controller.getCanvas());
		assertFalse("Animation should not be running initially", controller.isAnimationRunning());
	}

	@Test
	public void testGetCanvas() {
		Factory canvas = (Factory) controller.getCanvas();
		assertNotNull("Canvas should not be null", canvas);
		assertEquals("Canvas name should match", "Test Factory", canvas.getName());
		assertEquals("Canvas ID should match", "test_factory.factory", canvas.getId());
	}

	@Test
	public void testSetCanvas() throws InterruptedException {
		Factory newFactory = new Factory(200, 200, "New Factory");
		newFactory.setId("new_factory.factory");

		controller.setCanvas(newFactory);

		int retries = 0;
		while (controller.getCanvas() != newFactory && retries < 20) {
			Thread.sleep(50);
			retries++;
		}

		Factory canvas = (Factory) controller.getCanvas();
		assertEquals("Canvas should be updated", "New Factory", canvas.getName());
		assertEquals("Canvas ID should be updated", "new_factory.factory", canvas.getId());
	}

	@Test
	public void testStopAnimationWhenNotRunning() {
		controller.stopAnimation();
		assertFalse("Animation should still not be running", controller.isAnimationRunning());
	}

	@Test
	public void testPersistenceManagerNotNull() {
		assertNotNull("Persistence manager should not be null", controller.getPersistenceManager());
	}

	@Test
	public void testMultipleStopCalls() {
		controller.stopAnimation();
		controller.stopAnimation();
		controller.stopAnimation();

		assertFalse("Animation should not be running", controller.isAnimationRunning());
	}
}