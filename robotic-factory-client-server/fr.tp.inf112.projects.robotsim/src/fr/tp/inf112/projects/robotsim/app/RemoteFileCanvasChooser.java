package fr.tp.inf112.projects.robotsim.app;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.swing.JOptionPane;

import fr.tp.inf112.projects.canvas.view.FileCanvasChooser;
import fr.tp.inf112.projects.robotsim.model.RemoteFactoryPersistenceManager;

public class RemoteFileCanvasChooser extends FileCanvasChooser {

	private static final Logger LOGGER = Logger.getLogger(RemoteFileCanvasChooser.class.getName());

	private final RemoteFactoryPersistenceManager remotePersistenceManager;

	public RemoteFileCanvasChooser(final String fileExtension, final String fileTypeDescription,
			final RemoteFactoryPersistenceManager remotePersistenceManager) {
		super(fileExtension, fileTypeDescription);
		this.remotePersistenceManager = remotePersistenceManager;
	}

	@Override
	protected String browseCanvases(boolean open) {
		if (open) {
			return browseRemoteCanvasesToOpen();
		} else {
			return promptForFileName();
		}
	}

	private String browseRemoteCanvasesToOpen() {
		try {
			String[] factoryFiles = remotePersistenceManager.listFactoryFiles();

			if (factoryFiles == null || factoryFiles.length == 0) {
				JOptionPane.showMessageDialog(getViewer(), "No factory files found on server.", "No Files",
						JOptionPane.INFORMATION_MESSAGE);
				return null;
			}

			Object selectedFile = JOptionPane.showInputDialog(getViewer(), "Select a factory file to open:",
					"Open Factory", JOptionPane.QUESTION_MESSAGE, null, factoryFiles, factoryFiles[0]);

			if (selectedFile != null) {
				String fileName = selectedFile.toString();
				LOGGER.info("User selected file: " + fileName);
				return fileName;
			}

		} catch (IOException e) {
			LOGGER.log(Level.SEVERE,
					"Failed to get file list from server (during open/close). This is OK if server is down.", e);
		}

		return null;
	}

	private String promptForFileName() {
		String fileName = JOptionPane.showInputDialog(getViewer(), "Enter file name (without extension):",
				"Save Factory", JOptionPane.QUESTION_MESSAGE);

		if (fileName != null && !fileName.trim().isEmpty()) {
			fileName = fileName.trim();

			if (!fileName.endsWith(".factory")) {
				fileName += ".factory";
			}

			LOGGER.info("User entered file name: " + fileName);
			return fileName;
		}

		LOGGER.info("User cancelled file name entry");
		return null;
	}
}