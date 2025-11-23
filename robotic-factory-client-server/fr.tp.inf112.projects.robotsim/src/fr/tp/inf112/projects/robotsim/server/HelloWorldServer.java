package fr.tp.inf112.projects.robotsim.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple "Hello World" server that receives messages and responds.
 */
public class HelloWorldServer {

	private static final Logger LOGGER = Logger.getLogger(HelloWorldServer.class.getName());

	private static final int DEFAULT_PORT = 8080;

	private final int port;

	private ServerSocket serverSocket;

	private boolean running;

	public HelloWorldServer(final int port) {
		this.port = port;
		this.running = false;
	}

	public void start() throws IOException {
		serverSocket = new ServerSocket(port);
		running = true;

		LOGGER.info("Server started on port " + port);

		while (running) {
			try {
				// Accept client connection
				Socket clientSocket = serverSocket.accept();
				LOGGER.info("Client connected: " + clientSocket.getInetAddress());

				// Handle client in a new thread
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
		try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
				PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);) {
			// Read message from client
			String message = in.readLine();
			LOGGER.info("Received message: " + message);

			// Send response
			String response = "I received \"" + message + "\"!";
			out.println(response);
			LOGGER.info("Sent response: " + response);

		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Error handling client", e);
		} finally {
			try {
				clientSocket.close();
				LOGGER.info("Client disconnected");
			} catch (IOException e) {
				LOGGER.log(Level.WARNING, "Error closing client socket", e);
			}
		}
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

		if (args.length > 0) {
			try {
				port = Integer.parseInt(args[0]);
			} catch (NumberFormatException e) {
				LOGGER.warning("Invalid port number, using default: " + DEFAULT_PORT);
			}
		}

		HelloWorldServer server = new HelloWorldServer(port);

		try {
			server.start();
		} catch (IOException e) {
			LOGGER.log(Level.SEVERE, "Failed to start server", e);
		}
	}
}