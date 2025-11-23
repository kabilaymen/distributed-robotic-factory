package fr.tp.inf112.projects.robotsim.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.logging.Level;

import fr.tp.inf112.projects.canvas.model.Style;
import fr.tp.inf112.projects.canvas.model.impl.RGBColor;
import fr.tp.inf112.projects.robotsim.model.motion.Motion;
import fr.tp.inf112.projects.robotsim.model.path.FactoryPathFinder;
import fr.tp.inf112.projects.robotsim.model.shapes.CircularShape;
import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;
import fr.tp.inf112.projects.robotsim.model.shapes.RectangularShape;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Robot extends Component {

	private static final long serialVersionUID = -1218857231970296747L;
	private static final Logger LOGGER = Logger.getLogger(Robot.class.getName());

	private static final Style STYLE = new ComponentStyle(RGBColor.GREEN, RGBColor.BLACK, 3.0f, null);
	private static final Style BLOCKED_STYLE = new ComponentStyle(RGBColor.RED, RGBColor.BLACK, 3.0f,
			new float[] { 4.0f });

	private final Battery battery;
	private int speed;

	private transient List<Component> targetComponents;

	@JsonProperty
	private final List<String> targetComponentNames = new ArrayList<>();

	@JsonIgnore
	private transient Iterator<Component> targetComponentsIterator;
	private transient Component currTargetComponent;
	@JsonProperty
	private String currTargetComponentName;

	@JsonIgnore
	private transient Iterator<Position> currentPathPositionsIter;
	@JsonProperty
	private boolean blocked;
	@JsonProperty
	private transient Position memorizedTargetPosition;
	@JsonIgnore
	@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY, property = "@class")
	private transient FactoryPathFinder pathFinder;

	@JsonIgnore
	private transient Position currentWaypoint;
	@JsonIgnore
	private transient int stepAsideCooldown = 0;
	@JsonIgnore
	private transient int blockedMoveCounter = 0;
	@JsonIgnore
	private transient Random random = new Random();

	@JsonIgnore
	private transient boolean stateRestored = false;
	@JsonIgnore
	private transient int moveAttempts = 0;
	@JsonIgnore
	private transient int successfulMoves = 0;
	@JsonIgnore
	private transient int blockedMoves = 0;

	public Robot() {
		super();
		this.battery = null;
		this.targetComponents = new ArrayList<>();
		this.random = new Random();
	}

	public Robot(final Factory factory, final FactoryPathFinder pathFinder, final CircularShape shape,
			final Battery battery, final String name) {
		super(factory, shape, name);
		this.pathFinder = pathFinder;
		this.battery = battery;

		targetComponents = new ArrayList<>();
		currTargetComponent = null;
		currentPathPositionsIter = null;
		speed = 5;
		blocked = false;
		memorizedTargetPosition = null;
		this.random = new Random();
	}

	@Override
	public void run() {
		String robotName = "UNKNOWN";
		stateRestored = false;

		try {
			robotName = getName();
			if (robotName == null) {
				robotName = "[NAME IS NULL]";
			}
			LOGGER.info(">>> " + robotName + " THREAD STARTED <<<");
			while (isSimulationStarted()) {
				boolean result = behave();
				if (!result) {
					LOGGER.fine(robotName + " behave() returned false.");
				}
				Thread.sleep(100);
			}
		} catch (InterruptedException e) {
			LOGGER.info(robotName + " thread interrupted.");
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "!!! CRITICAL ERROR IN " + robotName + " THREAD !!!", e);
		} finally {
			LOGGER.info(">>> " + robotName + " THREAD TERMINATED <<<");

			if (moveAttempts > 0) {
				double blockRate = (double) blockedMoves / moveAttempts * 100.0;
				LOGGER.info(String.format(
						"===== %s MOVEMENT STATS =====\n" + "  Total Move Attempts: %d\n"
								+ "  Successful Moves:    %d\n" + "  Blocked Moves:       %d\n"
								+ "  BLOCK RATE:          %.2f%%\n" + "====================================",
						robotName, moveAttempts, successfulMoves, blockedMoves, blockRate));
			} else {
				LOGGER.info("===== " + robotName + " MOVEMENT STATS: No moves attempted. =====");
			}
		}
	}

	private void ensureTransientState() {
		String robotName = getName();
		if (robotName == null)
			robotName = "[NAME IS NULL]";

		Factory factory = getFactory();
		if (factory == null)
			return;
		if (pathFinder == null)
			return;
		if (random == null)
			random = new Random();

		if (!stateRestored) {
			LOGGER.info(">>> " + robotName + " RESTORING TRANSIENT STATE (ONE-TIME) <<<");
			if (targetComponents == null)
				targetComponents = new ArrayList<>();

			if (targetComponents.isEmpty() && targetComponentNames != null && !targetComponentNames.isEmpty()) {
				Map<String, Component> componentMap = factory.getComponents().stream().filter(c -> c.getName() != null)
						.collect(Collectors.toMap(Component::getName, c -> c, (c1, c2) -> c1));
				for (String targetName : targetComponentNames) {
					Component target = componentMap.get(targetName);
					if (target != null)
						targetComponents.add(target);
				}
			}

			if (currTargetComponent == null && currTargetComponentName != null) {
				Map<String, Component> componentMap = factory.getComponents().stream().filter(c -> c.getName() != null)
						.collect(Collectors.toMap(Component::getName, c -> c, (c1, c2) -> c1));
				currTargetComponent = componentMap.get(currTargetComponentName);
			}

			LOGGER.info(">>> " + robotName + " CLEARING PATH STATE (FRESH START) <<<");
			currentPathPositionsIter = null;
			currentWaypoint = null;
			memorizedTargetPosition = null;
			blocked = false;
			blockedMoveCounter = 0;
			stepAsideCooldown = 0;

			moveAttempts = 0;
			successfulMoves = 0;
			blockedMoves = 0;

			targetComponentsIterator = null;
			LOGGER.info(robotName + ": ✓ State restored, ready to compute fresh paths");
			stateRestored = true;
		}
	}

	@Override
	public boolean behave() {
		ensureTransientState();
		if (pathFinder == null) {
			blocked = true;
			return false;
		}

		if (getTargetComponents().isEmpty())
			return false;

		if (stepAsideCooldown > 0) {
			LOGGER.info(getName() + ": In step-aside cooldown... " + stepAsideCooldown + " ticks left.");
			stepAsideCooldown--;
			blocked = true;
			return false;
		}

		if (currTargetComponent == null || hasReachedCurrentTarget()) {
			if (currTargetComponent != null) {
				LOGGER.info(getName() + ": Reached " + currTargetComponent.getName());
			}

			currTargetComponent = nextTargetComponentToVisit();
			if (currTargetComponent != null) {
				currTargetComponentName = currTargetComponent.getName();
				LOGGER.info(getName() + ": New target: " + currTargetComponent.getName());
			} else {
				currTargetComponentName = null;
				return false;
			}

			currentPathPositionsIter = null;
			currentWaypoint = null;
			memorizedTargetPosition = null;
			blockedMoveCounter = 0;
		}

		if (currentPathPositionsIter == null) {
			computePathToCurrentTargetComponent();
			currentWaypoint = null;
			if (currentPathPositionsIter == null || !currentPathPositionsIter.hasNext()) {
				blocked = true;
				currentPathPositionsIter = null;
				return false;
			}
		}

		if (currentWaypoint == null || getPosition().equals(currentWaypoint)) {
			if (currentWaypoint != null)
				memorizedTargetPosition = null;

			if (currentPathPositionsIter.hasNext()) {
				currentWaypoint = currentPathPositionsIter.next();
			} else {
				LOGGER.fine(getName() + ": Path finished, but target not reached. Re-computing.");
				currentPathPositionsIter = null;
				return false;
			}
		}

		return moveToWaypoint();
	}

	public boolean isBlocked() {
		return blocked;
	}

	public void setPathFinder(FactoryPathFinder pathFinder) {
		this.pathFinder = pathFinder;
	}

	@Override
	public String toString() {
		return super.toString() + " battery=" + battery + "]";
	}

	protected int getSpeed() {
		return speed;
	}

	protected void setSpeed(final int speed) {
		this.speed = speed;
	}

	public Position getMemorizedTargetPosition() {
		return memorizedTargetPosition;
	}

	private List<Component> getTargetComponents() {
		if (targetComponents == null)
			targetComponents = new ArrayList<>();
		return targetComponents;
	}

	public boolean addTargetComponent(final Component targetComponent) {
		if (targetComponent == null)
			return false;
		getTargetComponents().add(targetComponent);
		if (this.targetComponentNames != null && !this.targetComponentNames.contains(targetComponent.getName())) {
			this.targetComponentNames.add(targetComponent.getName());
		}
		return true;
	}

	public boolean removeTargetComponent(final Component targetComponent) {
		if (this.targetComponentNames != null)
			this.targetComponentNames.remove(targetComponent.getName());
		return getTargetComponents().remove(targetComponent);
	}

	@JsonIgnore
	@Override
	public boolean isMobile() {
		return true;
	}

	private boolean moveToWaypoint() {
		moveAttempts++;
		Position targetWaypoint = currentWaypoint;
		if (memorizedTargetPosition != null)
			targetWaypoint = memorizedTargetPosition;

		if (targetWaypoint == null)
			return false;

		int dx = targetWaypoint.getxCoordinate() - getPosition().getxCoordinate();
		int dy = targetWaypoint.getyCoordinate() - getPosition().getyCoordinate();
		double distance = Math.sqrt(dx * dx + dy * dy);

		Position nextStepPosition;

		if (distance <= getSpeed()) {
			nextStepPosition = targetWaypoint;
		} else {
			int currentSpeed = getSpeed();
			int stepX = 0;
			int stepY = 0;
			if (Math.abs(dx) > Math.abs(dy)) {
				stepX = (int) Math.signum(dx) * Math.min(Math.abs(dx), currentSpeed);
			} else {
				stepY = (int) Math.signum(dy) * Math.min(Math.abs(dy), currentSpeed);
			}
			nextStepPosition = new Position(getPosition().getxCoordinate() + stepX,
					getPosition().getyCoordinate() + stepY);
		}

		if (nextStepPosition.equals(getPosition())) {
			nextStepPosition = targetWaypoint;
		}

		final Motion motion = new Motion(getPosition(), nextStepPosition);
		final int displacement = getFactory().moveComponent(motion, this);

		if (displacement != 0) {
			successfulMoves++;
			notifyObservers();
			memorizedTargetPosition = null;
			blocked = false;
			blockedMoveCounter = 0;
			return true;
		} else {
			blockedMoves++;
			memorizedTargetPosition = nextStepPosition;
			blocked = true;
			blockedMoveCounter++;
			LOGGER.warning(
					getName() + " Move BLOCKED at " + nextStepPosition + " (Patience: " + blockedMoveCounter + ")");

			Component blocker = getFactory().getMobileComponentAt(nextStepPosition, this);
			if (blocker != null)
				LOGGER.warning(getName() + " Blocked by: " + blocker.getName());

			if (blockedMoveCounter > 5) {
				LOGGER.severe(getName() + " is STUCK. Trying to resolve...");
				if (blocker instanceof Robot) {
					Robot otherRobot = (Robot) blocker;
					if (getName().compareTo(otherRobot.getName()) > 0) {
						LOGGER.warning(getName() + " lost tie-break, stepping aside.");
						Position stepAsidePos = findRandomFreeNeighbouringPosition();
						if (stepAsidePos != null) {
							final Motion stepAsideMotion = new Motion(getPosition(), stepAsidePos);
							if (getFactory().moveComponent(stepAsideMotion, this) != 0) {
								LOGGER.info(getName() + " Successfully stepped aside to " + getPosition());
								notifyObservers();
								this.stepAsideCooldown = 10;
								currentPathPositionsIter = null;
								currentWaypoint = null;
								memorizedTargetPosition = null;
								blocked = false;
								blockedMoveCounter = 0;
								return true;
							}
						}
					} else {
						LOGGER.info(getName() + " won tie-break, holding position.");
						blockedMoveCounter = 0;
					}
				} else {
					LOGGER.warning(getName() + ": Blocked by non-robot. Forcing path re-computation.");
					currentPathPositionsIter = null;
					currentWaypoint = null;
					memorizedTargetPosition = null;
					blockedMoveCounter = 0;
				}
			}
			return false;
		}
	}

	private Component nextTargetComponentToVisit() {
		if (targetComponentsIterator == null || !targetComponentsIterator.hasNext()) {
			targetComponentsIterator = getTargetComponents().iterator();
		}
		return targetComponentsIterator.hasNext() ? targetComponentsIterator.next() : null;
	}

	private void computePathToCurrentTargetComponent() {
		if (pathFinder == null || currTargetComponent == null) {
			LOGGER.severe(getName() + " Cannot compute path: PathFinder=" + pathFinder);
			currentPathPositionsIter = new ArrayList<Position>().iterator();
			return;
		}

		LOGGER.info(getName() + " Computing path from " + getPosition() + " to " + currTargetComponent.getName());
		List<Position> currentPathPositions = pathFinder.findPath(this, currTargetComponent);

		if (currentPathPositions.isEmpty()) {
			LOGGER.warning(getName() + ": PathFinder returned EMPTY path.");
		} else {
			if (currentPathPositions.get(0).equals(getPosition())) {
				LOGGER.info(getName() + ": Path started with current position. Removing it.");
				currentPathPositions.remove(0);
			}

			if (!currentPathPositions.isEmpty()) {
				LOGGER.info(String.format("%s: Path found with %d steps. Next Waypoint: %s", getName(),
						currentPathPositions.size(), currentPathPositions.get(0)));
			}
		}

		currentPathPositionsIter = currentPathPositions.iterator();
	}

	private Position findRandomFreeNeighbouringPosition() {
		Factory factory = getFactory();
		Position currentPos = getPosition();
		final int stepSize = 5;
		if (random == null)
			random = new Random();

		List<Position> neighbors = new ArrayList<>();
		neighbors.add(new Position(currentPos.getxCoordinate() + stepSize, currentPos.getyCoordinate()));
		neighbors.add(new Position(currentPos.getxCoordinate() - stepSize, currentPos.getyCoordinate()));
		neighbors.add(new Position(currentPos.getxCoordinate(), currentPos.getyCoordinate() + stepSize));
		neighbors.add(new Position(currentPos.getxCoordinate(), currentPos.getyCoordinate() - stepSize));
		Collections.shuffle(neighbors, random);

		for (Position pos : neighbors) {
			RectangularShape shape = new RectangularShape(pos.getxCoordinate(), pos.getyCoordinate(), getWidth(),
					getHeight());
			if (!factory.hasObstacleAt(shape) && !factory.hasMobileComponentAt(shape, this))
				return pos;
		}
		return null;
	}

	public boolean isLivelyLocked() {
		if (memorizedTargetPosition == null)
			return false;
		final Component otherComponent = getFactory().getMobileComponentAt(memorizedTargetPosition, this);
		if (otherComponent instanceof Robot) {
			Robot otherRobot = (Robot) otherComponent;
			Position otherRobotMemorizedPos = otherRobot.getMemorizedTargetPosition();
			return getPosition().equals(otherRobotMemorizedPos);
		}
		return false;
	}

	private boolean hasReachedCurrentTarget() {
		if (currTargetComponent == null)
			return false;
		return getPositionedShape().overlays(currTargetComponent.getPositionedShape());
	}

	@Override
	public boolean canBeOverlayed(final PositionedShape shape) {
		return true;
	}

	public FactoryPathFinder getPathFinder() {
		return pathFinder;
	}

	@JsonIgnore
	@Override
	public Style getStyle() {
		return blocked ? BLOCKED_STYLE : STYLE;
	}
}