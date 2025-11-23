package fr.tp.inf112.projects.robotsim.app;

import java.awt.Component;
import java.util.Arrays;
import java.util.logging.Logger;

import javax.swing.SwingUtilities;

import fr.tp.inf112.projects.canvas.model.impl.BasicVertex;
import fr.tp.inf112.projects.canvas.view.CanvasViewer;
import fr.tp.inf112.projects.canvas.view.FileCanvasChooser;
import fr.tp.inf112.projects.robotsim.model.Area;
import fr.tp.inf112.projects.robotsim.model.Battery;
import fr.tp.inf112.projects.robotsim.model.ChargingStation;
import fr.tp.inf112.projects.robotsim.model.Conveyor;
import fr.tp.inf112.projects.robotsim.model.Door;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.FactoryPersistenceManager;
import fr.tp.inf112.projects.robotsim.model.Machine;
import fr.tp.inf112.projects.robotsim.model.RemoteFactoryPersistenceManager;
import fr.tp.inf112.projects.robotsim.model.Robot;
import fr.tp.inf112.projects.robotsim.model.Room;
import fr.tp.inf112.projects.robotsim.model.path.AbstractFactoryPathFinder;
import fr.tp.inf112.projects.robotsim.model.path.CustomDijkstraFactoryPathFinder;
import fr.tp.inf112.projects.robotsim.model.shapes.BasicPolygonShape;
import fr.tp.inf112.projects.robotsim.model.shapes.CircularShape;
import fr.tp.inf112.projects.robotsim.model.shapes.RectangularShape;

public class SimulatorApplication {

	private static final Logger LOGGER = Logger.getLogger(SimulatorApplication.class.getName());
	private static final String DEFAULT_SERVER_HOST = "localhost";
	private static final int DEFAULT_SERVER_PORT = 8090;
	private static final boolean USE_REMOTE_PERSISTENCE = true;

	public static void main(String[] args) {
		LOGGER.info("Starting the robot simulator...");
		LOGGER.config("With parameters " + Arrays.toString(args) + ".");

		String serverHost = DEFAULT_SERVER_HOST;
		int serverPort = DEFAULT_SERVER_PORT;
		if (USE_REMOTE_PERSISTENCE) {
			if (args.length > 0)
				serverHost = args[0];
			if (args.length > 1) {
				try {
					serverPort = Integer.parseInt(args[1]);
				} catch (NumberFormatException ex) {
					LOGGER.warning("Invalid port, using default: " + DEFAULT_SERVER_PORT);
				}
			}
		}

		final Factory factory = createDemoFactory();
		LOGGER.info("Factory model created with " + factory.getComponents().size() + " components.");
		startGui(factory, serverHost, serverPort);
	}

	private static void startGui(final Factory factory, final String serverHost, final int serverPort) {
		SwingUtilities.invokeLater(() -> {
			LOGGER.info("Starting GUI...");

			CanvasViewer viewer;
			if (USE_REMOTE_PERSISTENCE) {
				LOGGER.info("Using remote persistence AND remote simulation: " + serverHost + ":" + serverPort);
				final RemoteFactoryPersistenceManager remotePersistenceManager = new RemoteFactoryPersistenceManager(
						null, serverHost, serverPort);
				final RemoteFileCanvasChooser remoteCanvasChooser = new RemoteFileCanvasChooser("factory",
						"Puck Factory", remotePersistenceManager);
				remotePersistenceManager.setCanvasChooser(remoteCanvasChooser);

				final RemoteSimulatorController controller = new RemoteSimulatorController(factory,
						remotePersistenceManager);

				viewer = new CanvasViewer(controller);
				remoteCanvasChooser.setViewer((Component) viewer);

				new Thread(() -> LOGGER.info("Remote persistence and simulation controller ready")).start();

			} else {
				LOGGER.info("Using local file persistence");
				final FileCanvasChooser localCanvasChooser = new FileCanvasChooser("factory", "Puck Factory");
				final FactoryPersistenceManager localPersistenceManager = new FactoryPersistenceManager(
						localCanvasChooser);
				final SimulatorController controller = new SimulatorController(factory, localPersistenceManager);
				viewer = new CanvasViewer(controller);
				localCanvasChooser.setViewer((Component) viewer);
			}
		});
	}

	private static Factory createDemoFactory() {
		final Factory factory = new Factory(200, 200, "Simple Test Puck Factory");
		final Room room1 = new Room(factory, new RectangularShape(20, 20, 75, 75), "Production Room 1");
		new Door(room1, Room.WALL.BOTTOM, 10, 20, true, "Entrance");
		final Area area1 = new Area(room1, new RectangularShape(35, 35, 50, 50), "Production Area 1");
		final Machine machine1 = new Machine(area1, new RectangularShape(50, 50, 15, 15), "Machine 1");
		final Room room2 = new Room(factory, new RectangularShape(120, 22, 75, 75), "Production Room 2");
		new Door(room2, Room.WALL.LEFT, 10, 20, true, "Entrance");
		final Area area2 = new Area(room2, new RectangularShape(135, 35, 50, 50), "Production Area 2");
		final Machine machine2 = new Machine(area2, new RectangularShape(150, 50, 15, 15), "Machine 2");
		final int baselineSize = 3;
		final int xCoordinate = 10, yCoordinate = 165, width = 10, height = 30;
		final BasicPolygonShape conveyorShape = new BasicPolygonShape();
		conveyorShape.addVertex(new BasicVertex(xCoordinate, yCoordinate));
		conveyorShape.addVertex(new BasicVertex(xCoordinate + width, yCoordinate));
		conveyorShape.addVertex(new BasicVertex(xCoordinate + width, yCoordinate + height - baselineSize));
		conveyorShape
				.addVertex(new BasicVertex(xCoordinate + width + baselineSize, yCoordinate + height - baselineSize));
		conveyorShape.addVertex(new BasicVertex(xCoordinate + width + baselineSize, yCoordinate + height));
		conveyorShape.addVertex(new BasicVertex(xCoordinate - baselineSize, yCoordinate + height));
		conveyorShape.addVertex(new BasicVertex(xCoordinate - baselineSize, yCoordinate + height - baselineSize));
		conveyorShape.addVertex(new BasicVertex(xCoordinate, yCoordinate + height - baselineSize));
		final Room chargingRoom = new Room(factory, new RectangularShape(125, 125, 50, 50), "Charging Room");
		new Door(chargingRoom, Room.WALL.RIGHT, 10, 20, true, "Entrance");
		final ChargingStation chargingStation = new ChargingStation(factory, new RectangularShape(150, 145, 15, 15),
				"Charging Station");
		final AbstractFactoryPathFinder<?, ?> customPathFinder = new CustomDijkstraFactoryPathFinder(factory, 5);

		LOGGER.info("Initializing path finders...");
		customPathFinder.init();
		LOGGER.info("Path finders initialized.");
		final Robot robot1 = new Robot(factory, customPathFinder, new CircularShape(5, 5, 2), new Battery(10),
				"Robot 1");
		robot1.addTargetComponent(machine1);
		robot1.addTargetComponent(machine2);
		robot1.addTargetComponent(new Conveyor(factory, conveyorShape, "Conveyor 1"));
		robot1.addTargetComponent(chargingStation);

		final Robot robot2 = new Robot(factory, customPathFinder, new CircularShape(15, 5, 2), new Battery(10),
				"Robot 2");
		robot2.addTargetComponent(machine1);
		robot2.addTargetComponent(machine2);

		final Robot robot3 = new Robot(factory, customPathFinder, new CircularShape(25, 5, 2), new Battery(10),
				"Robot 3");
		robot3.addTargetComponent(machine1);
		robot3.addTargetComponent(machine2);
		final Robot robot4 = new Robot(factory, customPathFinder, new CircularShape(35, 5, 2), new Battery(10),
				"Robot 4");
		robot4.addTargetComponent(machine1);
		robot4.addTargetComponent(machine2);

		return factory;
	}
}