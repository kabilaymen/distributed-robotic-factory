package fr.tp.inf112.projects.robotsim.app;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import fr.tp.inf112.projects.canvas.controller.Observer;
import fr.tp.inf112.projects.canvas.model.Canvas;
import fr.tp.inf112.projects.canvas.model.CanvasChooser;
import fr.tp.inf112.projects.canvas.model.CanvasPersistenceManager;
import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.LocalFactoryModelChangedNotifier;

public class RemoteSimulatorController extends SimulatorController {

	private static final Logger LOGGER = Logger.getLogger(RemoteSimulatorController.class.getName());
	private final HttpClient httpClient;
	private final String serviceBaseUrl;
	private final ExecutorService backgroundExecutor = Executors.newSingleThreadExecutor();
	private final LocalFactoryModelChangedNotifier localNotifier = new LocalFactoryModelChangedNotifier();

	private volatile boolean animationRunning = false;
	private FactorySimulationEventConsumer eventConsumer;
	private JDialog loadingDialog;

	public RemoteSimulatorController(Factory factoryModel, CanvasPersistenceManager persistenceManager) {
		this(factoryModel, persistenceManager, "http://localhost:8080/simulation");
	}

	public RemoteSimulatorController(Factory factoryModel, CanvasPersistenceManager persistenceManager,
			String serviceUrl) {
		super(factoryModel, persistenceManager);
		this.serviceBaseUrl = serviceUrl;
		this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	}

	public void updateCanvasDuringAnimation(final Factory remoteFactoryModel) {
		SwingUtilities.invokeLater(() -> {
			super.setCanvas(remoteFactoryModel);
			remoteFactoryModel.setNotifier(localNotifier);
			localNotifier.notifyObservers();
		});
	}

	@Override
	public void setCanvas(final Canvas canvasModel) {
		super.setCanvas(canvasModel);
		if (canvasModel instanceof Factory) {
			((Factory) canvasModel).setNotifier(localNotifier);
		}
		localNotifier.notifyObservers();
	}

	@Override
	public void startAnimation() {
		if (getCanvas() == null)
			return;
		final String factoryId = getCanvas().getId();

		if (factoryId == null) {
			saveFactoryBeforeSimulation();
			return;
		}

		if (animationRunning)
			return;

		showLoadingDialog();

		backgroundExecutor.submit(() -> {
			try {
				if (eventConsumer != null)
					eventConsumer.stop();

				LOGGER.info("1. Starting Consumer...");

				eventConsumer = new FactorySimulationEventConsumer(this, factoryId, () -> sendPrepareCommand(factoryId),
						() -> sendRunCommand(factoryId)
				);

				new Thread(() -> eventConsumer.consumeMessages()).start();
				animationRunning = true;

			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, "Start failed", e);
				animationRunning = false;
				closeLoadingDialog();
				SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, "Error: " + e.getMessage()));
			}
		});
	}

	private void sendPrepareCommand(String factoryId) {
		try {
			LOGGER.info("2. Sending PREPARE request...");
			URI uri = new URI(serviceBaseUrl + "/start/" + factoryId);
			httpClient.send(HttpRequest.newBuilder().uri(uri).GET().build(), HttpResponse.BodyHandlers.discarding());
		} catch (Exception e) {
			LOGGER.severe("Prepare failed: " + e.getMessage());
		}
	}

	private void sendRunCommand(String factoryId) {
		try {
			LOGGER.info("3. Sending RUN request...");
			URI uri = new URI(serviceBaseUrl + "/run/" + factoryId);
			httpClient.send(HttpRequest.newBuilder().uri(uri).GET().build(), HttpResponse.BodyHandlers.discarding());
		} catch (Exception e) {
			LOGGER.severe("Run failed: " + e.getMessage());
		}
	}

	public void simulationDidStart() {
		closeLoadingDialog();
	}

	@Override
	public void stopAnimation() {
		if (!animationRunning)
			return;
		String factoryId = getCanvas().getId();

		backgroundExecutor.submit(() -> {
			try {
				if (eventConsumer != null)
					eventConsumer.stop();
				animationRunning = false;

				LOGGER.info("Persisting state before reset...");
				if (getCanvas() != null)
					getPersistenceManager().persist(getCanvas());

				LOGGER.info("Resetting server...");
				URI uri = new URI(serviceBaseUrl + "/reset/" + factoryId);
				httpClient.send(HttpRequest.newBuilder().uri(uri).DELETE().build(),
						HttpResponse.BodyHandlers.discarding());

			} catch (Exception e) {
				LOGGER.log(Level.SEVERE, "Stop failed", e);
			}
		});
	}

	@Override
	public boolean isAnimationRunning() {
		return animationRunning;
	}

	private void showLoadingDialog() {
		SwingUtilities.invokeLater(() -> {
			if (loadingDialog != null && loadingDialog.isVisible())
				return;
			loadingDialog = new JDialog();
			loadingDialog.setTitle("Loading");
			loadingDialog.add(new JLabel("Synchronizing...", SwingConstants.CENTER));
			loadingDialog.setSize(300, 100);
			loadingDialog.setLocationRelativeTo(null);
			loadingDialog.setModal(false);
			loadingDialog.setVisible(true);
		});
	}

	private void closeLoadingDialog() {
		SwingUtilities.invokeLater(() -> {
			if (loadingDialog != null) {
				loadingDialog.setVisible(false);
				loadingDialog.dispose();
				loadingDialog = null;
			}
		});
	}

	private boolean saveFactoryBeforeSimulation() {
		try {
			CanvasChooser chooser = getPersistenceManager().getCanvasChooser();
			String id = chooser.newCanvasId();
			if (id != null) {
				getCanvas().setId(id);
				getPersistenceManager().persist(getCanvas());
				startAnimation();
				return true;
			}
		} catch (Exception e) {
			LOGGER.severe(e.getMessage());
		}
		return false;
	}

	@Override
	public boolean addObserver(Observer o) {
		return localNotifier.addObserver(o);
	}

	@Override
	public boolean removeObserver(Observer o) {
		return localNotifier.removeObserver(o);
	}
}