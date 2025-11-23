package fr.tp.inf112.projects.robotsim.model.shapes;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import fr.tp.inf112.projects.canvas.model.OvalShape;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CircularShape extends PositionedShape implements OvalShape {

	private static final long serialVersionUID = -1912941556210518344L;

	private final int radius;

	public CircularShape() {
		super();
		this.radius = 0;
	}

	@JsonCreator
	public CircularShape(@JsonProperty("xCoordinate") final int xCoordinate,
			@JsonProperty("yCoordinate") final int yCoordinate, @JsonProperty("radius") final int radius) {
		super(xCoordinate, yCoordinate);

		this.radius = radius;
	}

	public int getRadius() {
		return radius;
	}

	@Override
	public int getWidth() {
		return 2 * radius;
	}

	@Override
	public int getHeight() {
		return getWidth();
	}

	@Override
	public String toString() {
		return super.toString() + " [radius=" + radius + "]";
	}
}