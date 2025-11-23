package fr.tp.inf112.projects.robotsim.server;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import fr.tp.inf112.projects.robotsim.model.Factory;
import fr.tp.inf112.projects.robotsim.model.FactoryPersistenceManager;

public class FactoryPersistenceServer {

	private static final Logger LOGGER = Logger.getLogger(FactoryPersistenceServer.class.getName());
	private static final int DEFAULT_PORT = 8090;

	private final int port;
	private ServerSocket serverSocket;
	private boolean running;
	private final File workingDirectory;

	public FactoryPersistenceServer(final int port, final File workingDirectory) {
		this.port = port;
		this.running = false;
		this.workingDirectory = workingDirectory;

		if (!workingDirectory.exists()) {
			workingDirectory.mkdirs();
			LOGGER.info("Created working directory: " + workingDirectory.getAbsolutePath());
		}
	}

	public void start() throws IOException {
		serverSocket = new ServerSocket(port);
		running = true;

		LOGGER.info("Factory Persistence Server started on port " + port);
		LOGGER.info("Working directory: " + workingDirectory.getAbsolutePath());

		while (running) {
			try {
				Socket clientSocket = serverSocket.accept();
				LOGGER.info("Client connected: " + clientSocket.getInetAddress());

				Thread clientThread = new Thread(() -> handleClient(clientSocket));
				clientThread.start();

			} catch (IOException e) {
				if (running) {
					LOGGER.log(Level.SEVERE, "Error accepting client connection", e);
				}
			}
		}
	}

	private void handleClient(Socket clientSocket) {
		try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
				ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

			Object request = in.readObject();
			LOGGER.info("Received request of type: " + request.getClass().getName());

			if (request instanceof String) {
				String command = (String) request;
				if ("LIST".equals(command)) {
					handleListRequest(out);
				} else {
					handleReadRequest(command, out);
				}
			} else if (request instanceof Factory) {
				handlePersistRequest((Factory) request, out);
			} else {
				LOGGER.warning("Unknown request type: " + request.getClass().getName());
				out.writeObject(new IOException("Unknown request type"));
			}

		} catch (Exception e) {
			LOGGER.log(Level.SEVERE, "Error handling client request", e);
		} finally {
			try {
				clientSocket.close();
				LOGGER.info("Client disconnected");
			} catch (IOException e) {
				LOGGER.log(Level.WARNING, "Error closing client socket", e);
			}
		}
	}

	private void handleReadRequest(String fileId, ObjectOutputStream out) throws IOException {
		LOGGER.info("Processing READ request for file: " + fileId);

		File factoryFile = new File(workingDirectory, fileId);

		if (!factoryFile.exists()) {
			LOGGER.warning("File not found: " + factoryFile.getAbsolutePath());
			out.writeObject(new IOException("File not found: " + fileId));
			return;
		}

		try {
			FactoryPersistenceManager manager = new FactoryPersistenceManager(null);
			Factory factory = (Factory) manager.read(factoryFile.getAbsolutePath());
			factory.setId(fileId);

			out.writeObject(factory);
			LOGGER.info("Successfully sent factory model: " + fileId);

		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Error reading factory file", e);
			out.writeObject(e);
		}
	}

	private void handlePersistRequest(Factory factory, ObjectOutputStream out) throws IOException {
		String fileId = factory.getId();
		LOGGER.info("Processing PERSIST request for file: " + fileId);

		if (fileId == null || fileId.trim().isEmpty()) {
			IOException error = new IOException("Invalid file ID");
			LOGGER.log(Level.SEVERE, "Invalid file ID", error);
			out.writeObject(error);
			return;
		}

		File factoryFile = new File(workingDirectory, fileId);

		try {
			String originalId = factory.getId();
			factory.setId(factoryFile.getAbsolutePath());

			FactoryPersistenceManager manager = new FactoryPersistenceManager(null);
			manager.persist(factory);

			factory.setId(originalId);

			out.writeObject("SUCCESS");
			LOGGER.info("Successfully saved factory: " + factoryFile.getAbsolutePath());

		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Error persisting factory file", e);
			out.writeObject(e);
		}
	}

	private void handleListRequest(ObjectOutputStream out) throws IOException {
		LOGGER.info("Processing LIST request");

		File[] files = workingDirectory.listFiles((dir, name) -> name.endsWith(".factory"));

		if (files == null || files.length == 0) {
			out.writeObject(new String[0]);
			LOGGER.info("No factory files found");
			return;
		}

		String[] fileNames = new String[files.length];
		for (int i = 0; i < files.length; i++) {
			fileNames[i] = files[i].getName();
		}

		out.writeObject(fileNames);
		LOGGER.info("Sent list of " + fileNames.length + " factory files");
	}

	public void stop() throws IOException {
		running = false;
		if (serverSocket != null && !serverSocket.isClosed()) {
			serverSocket.close();
			LOGGER.info("Server stopped");
		}
	}

	public static void main(String[] args) {
		int port = DEFAULT_PORT;
		String workingDir = "server_factories";

		if (args.length > 0) {
			try {
				port = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				LOGGER.warning("Invalid port, using default: " + DEFAULT_PORT);
			}
		}

		if (args.length > 1) {
			workingDir = args[1];
		}

		FactoryPersistenceServer server = new FactoryPersistenceServer(port, new File(workingDir));

		try {
			server.start();
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Failed to start server", e);
		}
	}
}