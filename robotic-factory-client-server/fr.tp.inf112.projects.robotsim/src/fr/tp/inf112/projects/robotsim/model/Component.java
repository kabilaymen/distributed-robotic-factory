package fr.tp.inf112.projects.robotsim.model;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.tp.inf112.projects.canvas.model.Figure;
import fr.tp.inf112.projects.canvas.model.Style;
import fr.tp.inf112.projects.robotsim.model.shapes.PositionedShape;
import fr.tp.inf112.projects.canvas.model.Shape;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public abstract class Component implements Figure, Serializable, Runnable {

	private static final long serialVersionUID = -5960950869184030220L;

	private static final Logger LOGGER = Logger.getLogger(Component.class.getName());

	private String id;

	@JsonBackReference("factory-components")
	private final Factory factory;

	@JsonProperty
	private PositionedShape positionedShape;

	@JsonProperty
	private String name;

	public Component() {
		this.factory = null;
		this.positionedShape = null;
		this.name = null;
	}

	protected Component(final Factory factory, final PositionedShape shape, final String name) {
		this.factory = factory;
		this.positionedShape = shape;
		this.name = name;

		if (factory != null) {
			factory.addComponent(this);
		}
	}

	@Override
	public void run() {
		String componentName = "[UNKNOWN]";
		try {
			componentName = getName();
			if (componentName == null) {
				componentName = "[NAME IS NULL - " + getClass().getSimpleName() + "]";
			}
			LOGGER.fine("Thread for component " + componentName + " started.");
			while (isSimulationStarted()) {

				behave();

				Thread.sleep(100);
			}
		} catch (InterruptedException e) {
			LOGGER.info("Thread for component " + componentName + " was interrupted.");
		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "!!! CRITICAL ERROR IN COMPONENT THREAD " + componentName + " !!!", e);
		}
		LOGGER.fine("Thread for component " + componentName + " terminated.");
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public PositionedShape getPositionedShape() {
		return positionedShape;
	}

	@JsonIgnore
	public Position getPosition() {
		final PositionedShape shape = getPositionedShape();
		return shape == null ? null : shape.getPosition();
	}

	protected Factory getFactory() {
		return factory;
	}

	@JsonIgnore
	@Override
	public int getxCoordinate() {
		final PositionedShape shape = getPositionedShape();
		return shape == null ? 0 : shape.getxCoordinate();
	}

	protected boolean setxCoordinate(int xCoordinate) {
		if (getPositionedShape().setxCoordinate(xCoordinate)) {
			notifyObservers();

			return true;
		}

		return false;
	}

	@JsonIgnore
	@Override
	public int getyCoordinate() {
		final PositionedShape shape = getPositionedShape();
		return shape == null ? 0 : shape.getyCoordinate();
	}

	protected boolean setyCoordinate(final int yCoordinate) {
		if (getPositionedShape().setyCoordinate(yCoordinate)) {
			notifyObservers();

			return true;
		}

		return false;
	}

	protected void notifyObservers() {
		if (getFactory() != null) {
			getFactory().notifyObservers();
		}
	}

	public String getName() {
		return name;
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + " [name=" + name + " xCoordinate=" + getxCoordinate() + ", yCoordinate="
				+ getyCoordinate() + ", shape=" + getPositionedShape();
	}

	@JsonIgnore
	public int getWidth() {
		final PositionedShape shape = getPositionedShape();
		return shape == null ? 0 : shape.getWidth();
	}

	@JsonIgnore
	public int getHeight() {
		final PositionedShape shape = getPositionedShape();
		return shape == null ? 0 : shape.getHeight();
	}

	public boolean behave() {
		return false;
	}

	@JsonIgnore
	public boolean isMobile() {
		return false;
	}

	public boolean overlays(final Component component) {
		return overlays(component.getPositionedShape());
	}

	public boolean overlays(final PositionedShape shape) {
		return getPositionedShape().overlays(shape);
	}

	public boolean canBeOverlayed(final PositionedShape shape) {
		return false;
	}

	@JsonIgnore
	@Override
	public Style getStyle() {
		return ComponentStyle.DEFAULT;
	}

	@JsonIgnore
	@Override
	public Shape getShape() {
		return getPositionedShape();
	}

	@JsonIgnore
	public boolean isSimulationStarted() {
		return getFactory() != null && getFactory().isSimulationStarted();
	}
}