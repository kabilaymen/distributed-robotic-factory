package fr.tp.inf112.projects.robotsim.model;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ConnectException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import fr.tp.inf112.projects.canvas.model.Canvas;
import fr.tp.inf112.projects.canvas.model.CanvasChooser;
import fr.tp.inf112.projects.canvas.model.CanvasPersistenceManager;

public class RemoteFactoryPersistenceManager implements CanvasPersistenceManager {

	private static final Logger LOGGER = Logger.getLogger(RemoteFactoryPersistenceManager.class.getName());
	private static final int SOCKET_TIMEOUT_MS = 10000; // 10 seconds

	private final String serverHost;
	private final int serverPort;
	private CanvasChooser canvasChooser;

	public RemoteFactoryPersistenceManager(CanvasChooser canvasChooser, String serverHost, int serverPort) {
		this.canvasChooser = canvasChooser;
		this.serverHost = serverHost;
		this.serverPort = serverPort;
	}

	@Override
	public void persist(Canvas canvasModel) throws IOException {
		if (!(canvasModel instanceof Factory)) {
			throw new IOException("Unsupported canvas type: " + canvasModel.getClass().getName());
		}

		Factory factory = (Factory) canvasModel;
		String fileId = factory.getId();

		if (fileId == null || fileId.trim().isEmpty()) {
			LOGGER.severe("Factory has no ID - cannot persist");
			throw new IOException("Factory ID is required for persistence");
		}

		LOGGER.info("Persisting factory to server: " + fileId);

		Socket socket = null;
		try {
			socket = new Socket(serverHost, serverPort);
			socket.setSoTimeout(SOCKET_TIMEOUT_MS);

			try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
					ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

				out.writeObject(factory);
				out.flush();

				Object response = in.readObject();

				if (response instanceof String && "SUCCESS".equals(response)) {
					LOGGER.info("Factory persisted successfully: " + fileId);
				} else if (response instanceof IOException) {
					throw (IOException) response;
				} else {
					throw new IOException("Unexpected server response: " + response);
				}
			}

		} catch (ConnectException e) {
			String message = "Cannot connect to persistence server at " + serverHost + ":" + serverPort;
			LOGGER.log(Level.SEVERE, message, e);
			throw new IOException(message + ". Please ensure FactoryPersistenceServer is running.", e);

		} catch (SocketTimeoutException e) {
			String message = "Connection to persistence server timed out";
			LOGGER.log(Level.SEVERE, message, e);
			throw new IOException(message + ". Server may be overloaded or not responding.", e);

		} catch (ClassNotFoundException e) {
			LOGGER.log(Level.SEVERE, "Error reading server response", e);
			throw new IOException("Error reading server response: " + e.getMessage(), e);

		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Failed to persist factory", e);
			throw e;

		} finally {
			if (socket != null && !socket.isClosed()) {
				try {
					socket.close();
				} catch (IOException e) {
					LOGGER.log(Level.WARNING, "Error closing socket", e);
				}
			}
		}
	}

	@Override
	public Canvas read(String canvasId) throws IOException {
		LOGGER.info("Reading factory from server: " + canvasId);

		Socket socket = null;
		try {
			socket = new Socket(serverHost, serverPort);
			socket.setSoTimeout(SOCKET_TIMEOUT_MS);

			try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
					ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

				out.writeObject(canvasId);
				out.flush();

				Object response = in.readObject();

				if (response instanceof Factory) {
					LOGGER.info("Factory read successfully: " + canvasId);
					return (Factory) response;
				} else if (response instanceof IOException) {
					throw (IOException) response;
				} else {
					throw new IOException("Unexpected server response: " + response);
				}
			}

		} catch (ConnectException e) {
			String message = "Cannot connect to persistence server at " + serverHost + ":" + serverPort;
			LOGGER.log(Level.SEVERE, message, e);

			// Show dialog for read operations (not called during shutdown)
			SwingUtilities.invokeLater(() -> {
				JOptionPane.showMessageDialog(null, message + "\n\nPlease ensure FactoryPersistenceServer is running.",
						"Server Connection Error", JOptionPane.ERROR_MESSAGE);
			});

			throw new IOException(message, e);

		} catch (SocketTimeoutException e) {
			String message = "Connection to persistence server timed out";
			LOGGER.log(Level.SEVERE, message, e);

			SwingUtilities.invokeLater(() -> {
				JOptionPane.showMessageDialog(null, message + "\n\nServer may be overloaded or not responding.",
						"Connection Timeout", JOptionPane.ERROR_MESSAGE);
			});

			throw new IOException(message, e);

		} catch (ClassNotFoundException e) {
			String message = "Error reading server response";
			LOGGER.log(Level.SEVERE, message, e);
			throw new IOException(message + ": " + e.getMessage(), e);

		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Failed to read factory", e);
			throw e;

		} finally {
			if (socket != null && !socket.isClosed()) {
				try {
					socket.close();
				} catch (IOException e) {
					LOGGER.log(Level.WARNING, "Error closing socket", e);
				}
			}
		}
	}

	public String[] listFactoryFiles() throws IOException {
		LOGGER.info("Listing factory files from server");

		Socket socket = null;
		try {
			socket = new Socket(serverHost, serverPort);
			socket.setSoTimeout(SOCKET_TIMEOUT_MS);

			try (ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
					ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {

				out.writeObject("LIST");
				out.flush();

				Object response = in.readObject();

				if (response instanceof String[]) {
					String[] files = (String[]) response;
					LOGGER.info("Received " + files.length + " files from server");
					return files;
				} else {
					throw new IOException("Unexpected server response: " + response);
				}
			}

		} catch (ConnectException e) {
			// This method is called during window close, so don't show dialog
			LOGGER.log(Level.WARNING, "Cannot connect to server for file list (this is OK if server is down)", e);
			throw new IOException("Server not available", e);

		} catch (SocketTimeoutException e) {
			LOGGER.log(Level.WARNING, "Timeout listing files from server", e);
			throw new IOException("Server timeout", e);

		} catch (ClassNotFoundException e) {
			LOGGER.log(Level.SEVERE, "Error reading server response", e);
			throw new IOException("Error reading server response: " + e.getMessage(), e);

		} catch (IOException e) {
			LOGGER.log(Level.WARNING, "Failed to list factory files", e);
			throw e;

		} finally {
			if (socket != null && !socket.isClosed()) {
				try {
					socket.close();
				} catch (IOException e) {
					LOGGER.log(Level.WARNING, "Error closing socket", e);
				}
			}
		}
	}

	@Override
	public boolean delete(Canvas canvasModel) throws IOException {
		LOGGER.warning("Delete not implemented for remote persistence");
		throw new UnsupportedOperationException("Delete operation not supported by remote persistence");
	}

	@Override
	public CanvasChooser getCanvasChooser() {
		return canvasChooser;
	}

	public void setCanvasChooser(CanvasChooser canvasChooser) {
		this.canvasChooser = canvasChooser;
	}

	// Utility method to test connection
	public boolean testConnection() {
		try {
			Socket socket = new Socket(serverHost, serverPort);
			socket.setSoTimeout(3000);
			socket.close();
			return true;
		} catch (Exception e) {
			LOGGER.log(Level.WARNING, "Connection test failed", e);
			return false;
		}
	}
}