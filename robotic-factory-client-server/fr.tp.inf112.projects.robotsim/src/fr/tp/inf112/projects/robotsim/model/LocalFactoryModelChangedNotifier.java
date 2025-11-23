package fr.tp.inf112.projects.robotsim.model;

import java.util.ArrayList;
import java.util.List;
import fr.tp.inf112.projects.canvas.controller.Observer;

/**
 * A local notifier that manages a list of observers directly.
 * 
 */
public class LocalFactoryModelChangedNotifier implements FactoryModelChangedNotifier {

	private transient List<Observer> observers;

	private List<Observer> getObservers() {
		if (observers == null) {
			observers = new ArrayList<>();
		}
		return observers;
	}

	@Override
	public void notifyObservers() {
		for (final Observer observer : getObservers()) {
			observer.modelChanged();
		}
	}

	@Override
	public boolean addObserver(Observer observer) {
		return getObservers().add(observer);
	}

	@Override
	public boolean removeObserver(Observer observer) {
		return getObservers().remove(observer);
	}
}