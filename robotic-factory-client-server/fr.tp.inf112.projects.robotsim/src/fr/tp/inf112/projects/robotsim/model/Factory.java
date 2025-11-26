package fr.tp.inf112.projects.robotsim.model;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.logging.Logger;

import fr.tp.inf112.projects.canvas.controller.Observable;
import fr.tp.inf112.projects.canvas.controller.Observer;
import fr.tp.inf112.projects.canvas.model.Canvas;
import fr.tp.inf112.projects.canvas.model.Figure;
import fr.tp.inf112.projects.canvas.model.Style;
import fr.tp.inf112.projects.robotsim.model.motion.Motion;
import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;
import fr.tp.inf112.projects.robotsim.model.shapes.RectangularShape;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;

public class Factory extends Component implements Canvas, Observable {

	private static final long serialVersionUID = 5156526483612458192L;
	private static final Logger LOGGER = Logger.getLogger(Factory.class.getName());

	private static final ComponentStyle DEFAULT = new ComponentStyle(5.0f);

	@JsonManagedReference("factory-components")
	private final List<Component> components;
	
	@JsonIgnore
	private transient FactoryModelChangedNotifier notifier = new LocalFactoryModelChangedNotifier();

	private transient boolean simulationStarted;

	public Factory() {
		super();
		components = new ArrayList<>();
		notifier = new LocalFactoryModelChangedNotifier();
		simulationStarted = false;
	}

	public Factory(final int width, final int height, final String name) {
		super(null, new RectangularShape(0, 0, width, height), name);
		components = new ArrayList<>();
		notifier = new LocalFactoryModelChangedNotifier();
		simulationStarted = false;
		LOGGER.config("Factory created: " + name + " (" + width + "x" + height + ")");
	}

	public void setNotifier(FactoryModelChangedNotifier notifier) {
		this.notifier = notifier;
	}

	public synchronized void debugLogMobileComponents() {
		LOGGER.info("=== MOBILE COMPONENTS STATE ===");
		for (final Component component : getComponents()) {
			if (component.isMobile()) {
				LOGGER.info("  " + component.getName() + " at " + component.getPosition() + " - Running: "
						+ component.isSimulationStarted());
			}
		}
		LOGGER.info("=== END MOBILE COMPONENTS ===");
	}

	public synchronized String debugCheckPosition(Position pos) {
		Component blocker = getMobileComponentAt(pos, null);
		if (blocker != null) {
			return "Position " + pos + " occupied by: " + blocker.getName();
		}

		RectangularShape shape = new RectangularShape(pos.getxCoordinate(), pos.getyCoordinate(), 5, 5);
		if (hasObstacleAt(shape)) {
			return "Position " + pos + " has obstacle";
		}

		return "Position " + pos + " is FREE";
	}

	@Override
	public boolean addObserver(Observer observer) {
		return notifier.addObserver(observer);
	}

	@Override
	public boolean removeObserver(Observer observer) {
		return notifier.removeObserver(observer);
	}

	public void notifyObservers() {
		if (notifier != null) {
			notifier.notifyObservers();
		}
	}

	public boolean addComponent(final Component component) {
		if (components.add(component)) {
			LOGGER.fine("Component added: " + component.getName());
			notifyObservers();

			return true;
		}

		return false;
	}

	public boolean removeComponent(final Component component) {
		if (components.remove(component)) {
			LOGGER.fine("Component removed: " + component.getName());
			notifyObservers();

			return true;
		}

		return false;
	}

	public List<Component> getComponents() {
		return components;
	}

	@JsonIgnore
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Override
	public Collection<Figure> getFigures() {
		return (Collection) components;
	}

	@Override
	public String toString() {
		return super.toString() + " components=" + components + "]";
	}

	public void setSimulationStarted(boolean simulationStarted) {
		if (this.simulationStarted == simulationStarted)
			return;
		this.simulationStarted = simulationStarted;
		notifyObservers();
	}

	public void startSimulation() {
		if (!isSimulationStarted()) {
			LOGGER.info("Starting simulation...");

			setSimulationStarted(true);

			behave();

			LOGGER.info("Simulation started successfully.");
		}
	}

	public void stopSimulation() {
		if (isSimulationStarted()) {
			LOGGER.info("Stopping simulation...");

			setSimulationStarted(false);

			LOGGER.info("Simulation stopped.");
		}
	}

	public boolean isSimulationStarted() {
		return simulationStarted;
	}

	public synchronized int moveComponent(final Motion motion, final Component componentToMove) {

		Position targetPos = motion.getTargetPosition();
		PositionedShape componentShape = componentToMove.getPositionedShape();

		RectangularShape targetShape = new RectangularShape(targetPos.getxCoordinate(), targetPos.getyCoordinate(),
				componentShape.getWidth(), componentShape.getHeight());

		if (hasMobileComponentAt(targetShape, componentToMove)) {
			LOGGER.finest("Factory blocked " + componentToMove.getName() + " from moving to " + targetPos);
			return 0;
		}

		LOGGER.finest("Factory allowing " + componentToMove.getName() + " to move to " + targetPos);
		return motion.moveToTarget();
	}

	@Override
	public boolean behave() {
		LOGGER.info("Launching component threads...");
		for (final Component component : getComponents()) {
			Thread componentThread = new Thread(component);
			componentThread.start();
			LOGGER.fine("Thread started for component: " + component.getName());
		}

		LOGGER.info("All component threads launched.");
		return true;
	}

	@Override
	public Style getStyle() {
		return DEFAULT;
	}

	public boolean hasObstacleAt(final PositionedShape shape) {
		for (final Component component : getComponents()) {
			if (component.overlays(shape) && !component.canBeOverlayed(shape)) {
				return true;
			}
		}

		return false;
	}

	public boolean hasMobileComponentAt(final PositionedShape shape, final Component movingComponent) {
		for (final Component component : getComponents()) {
			if (component != movingComponent && component.isMobile() && component.overlays(shape)) {
				return true;
			}
		}

		return false;
	}

	public Component getMobileComponentAt(final Position position, final Component ignoredComponent) {
		if (position == null) {
			return null;
		}

		return getMobileComponentAt(new RectangularShape(position.getxCoordinate(), position.getyCoordinate(), 2, 2),
				ignoredComponent);
	}

	public Component getMobileComponentAt(final PositionedShape shape, final Component ignoredComponent) {
		if (shape == null) {
			return null;
		}

		for (final Component component : getComponents()) {
			if (component != ignoredComponent && component.isMobile() && component.overlays(shape)) {
				return component;
			}
		}

		return null;
	}
	
	private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
		in.defaultReadObject();
		if (this.notifier == null) {
			this.notifier = new LocalFactoryModelChangedNotifier();
		}
		this.simulationStarted = false;
	}
}