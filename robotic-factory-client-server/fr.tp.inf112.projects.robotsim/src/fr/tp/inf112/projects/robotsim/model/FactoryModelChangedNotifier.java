package fr.tp.inf112.projects.robotsim.model; // Or your equivalent model package

import fr.tp.inf112.projects.canvas.controller.Observer;

/**
 * Interface for notifying observers of changes to the factory model.
 * 
 */
public interface FactoryModelChangedNotifier {

	/**
	 * Notifies all registered observers that the model has changed.
	 * 
	 */
	void notifyObservers();

	/**
	 * Adds an observer to the notification list.
	 * 
	 * @param observer The observer to add.
	 * @return true if the observer was added, false otherwise.
	 */
	boolean addObserver(Observer observer);

	/**
	 * Removes an observer from the notification list.
	 * 
	 * @param observer The observer to remove.
	 * @return true if the observer was removed, false otherwise.
	 */
	boolean removeObserver(Observer observer);
}