package com.example;

import java.io.File;
import java.io.IOException;
import java.net.URL;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SFTPClientController {

    private static final Logger logger = LoggerFactory.getLogger(SFTPClientController.class);

    @FXML
    private TextField ipAddressField;

    @FXML
    private TextField portField;

    @FXML
    private TextField knownHostsField;

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private ProgressIndicator loadingSpinner;

    private SFTPClient client;

    private Task<Void> connectTask;
    private Task<Void> testTask;

    private String ip, username, password, knownHosts;
    private int port;

    private boolean validateInputs() {
        ip = ipAddressField.getText().trim();
        String portStr = portField.getText().trim();
        username = usernameField.getText().trim();
        password = passwordField.getText();
        knownHosts = knownHostsField.getText().trim();

        if (ip.isEmpty()) {
            showAlert("Validation Error", "IP address is required.", false);
            logger.error("Validation Error: IP address is required.");
            return false;
        }
        if (!ip.matches("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.|$)){4}$")) {
            showAlert("Validation Error", "Invalid IP address format.", false);
            logger.error("Validation Error: Invalid IP address format.");
            return false;
        }
        if (portStr.isEmpty()) {
            showAlert("Validation Error", "Port number is required.", false);
            logger.error("Validation Error: Port number is required.");
            return false;
        }
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                showAlert("Validation Error", "Invalid port number.", false);
                logger.error("Validation Error: Invalid port number.");
                return false;
            }
        } catch (NumberFormatException e) {
            showAlert("Validation Error", "Invalid port number.", false);
            logger.error("Validation Error: Invalid port number.", e);
            return false;
        }
        if (username.isEmpty()) {
            showAlert("Validation Error", "Username is required.", false);
            logger.error("Validation Error: Username is required.");
            return false;
        }
        if (password.isEmpty()) {
            showAlert("Validation Error", "Password is required.", false);
            logger.error("Validation Error: Password is required.");
            return false;
        }

        return true;
    }

    @FXML
    private void connect() {
        logger.info("Connect button clicked. Attempting connection to {}:{} with user '{}'", ip, port, username);
        if (!validateInputs()) {
            logger.warn("Connection aborted due to invalid inputs.");
            return;
        }

        loadingSpinner.setVisible(true);

        connectTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (isCancelled())
                    return null;
                logger.info("Initializing SFTPClient with knownHosts: {}", knownHosts);
                client = new SFTPClient(getKnownHostsPath());
                if (isCancelled())
                    return null;
                logger.info("Connecting to SFTP server {}:{} as {}", ip, port, username);
                client.connect(username, ip, port, password);
                return null;
            }

            @Override
            protected void succeeded() {
                loadingSpinner.setVisible(false);
                logger.info("Connection to {}:{} succeeded.", ip, port);
                showAlert("Connection Successful", "Connected to " + ip + ":" + port, true);

                try {
                    URL browserView = getClass().getResource("/file-browser-view.fxml");
                    FXMLLoader loader = new FXMLLoader(browserView);
                    Parent browserRoot = loader.load();
                    FileBrowserController browserController = loader.getController();
                    browserController.setClient(client);

                    Stage stage = (Stage) ipAddressField.getScene().getWindow();
                    stage.setScene(new Scene(browserRoot));
                    stage.setTitle("SFTP File Browser");
                    stage.setMaximized(true);
                    logger.info("Switched to file browser view for user '{}'", username);

                } catch (IOException e) {
                    logger.error("Failed to load file browser view: {}", e.getMessage(), e);
                    showAlert("Error", "Failed to load file browser view: " + e.getMessage(), false);
                }
            }

            @Override
            protected void failed() {
                loadingSpinner.setVisible(false);
                Throwable ex = getException();
                String msg = ex != null ? ex.getMessage() : "Unknown error";
                logger.error("Connection to {}:{} failed: {}", ip, port, msg, ex);

                if (msg != null && msg.toLowerCase().contains("timed out")) {
                    logger.error("Connection timed out to {}:{}", ip, port);
                    showAlert("Connection Timeout",
                            "Cannot connect to SFTP.\nPlease check the IP address, port, or network status and try again.",
                            false);
                } else {
                    showAlert("Connection Failed", "Connection failed: " + msg, false);
                }
            }

            @Override
            protected void cancelled() {
                loadingSpinner.setVisible(false);
                logger.warn("Connection attempt to {}:{} cancelled.", ip, port);
                showAlert("Cancelled", "Connection attempt cancelled.", false);
            }
        };

        Thread thread = new Thread(connectTask);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void testConnection() {
        logger.info("Test connect button clicked. Attempting connection to {}:{} with user '{}'", ip, port, username);
        if (!validateInputs()) {
            logger.warn("Test connection aborted due to invalid inputs.");
            return;
        }

        loadingSpinner.setVisible(true);

        testTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (isCancelled())
                    return null;
                logger.info("Initializing SFTPClient for test with knownHosts: {}", knownHosts);
                SFTPClient testClient = new SFTPClient(getKnownHostsPath());
                if (isCancelled())
                    return null;
                logger.info("Testing connection to SFTP server {}:{} as {}", ip, port, username);
                testClient.connect(username, ip, port, password);
                if (isCancelled()) {
                    logger.warn("Test connection cancelled, disconnecting test client.");
                    testClient.disconnect();
                    return null;
                }
                testClient.disconnect();
                logger.info("Test connection to {}:{} succeeded.", ip, port);
                return null;
            }

            @Override
            protected void succeeded() {
                loadingSpinner.setVisible(false);
                logger.info("Test connection to {}:{} succeeded.", ip, port);
                showAlert("Test Successful", "Successfully connected to " + ip + ":" + port, true);
            }

            @Override
            protected void failed() {
                loadingSpinner.setVisible(false);
                Throwable ex = getException();
                String msg = ex != null ? ex.getMessage() : "Unknown error";
                logger.error("Test connection to {}:{} failed: {}", ip, port, msg, ex);

                if (msg != null && msg.toLowerCase().contains("timed out")) {
                    logger.error("Test connection timed out to {}:{}", ip, port);
                    showAlert("Test Connection Timeout",
                            "Cannot connect to SFTP.\nPlease check the IP address, port, or network status and try again.",
                            false);
                } else {
                    showAlert("Test Failed", "Failed to connect: " + msg, false);
                }
            }

            @Override
            protected void cancelled() {
                loadingSpinner.setVisible(false);
                logger.warn("Test connection attempt to {}:{} cancelled.", ip, port);
                showAlert("Cancelled", "Test connection cancelled.", false);
            }
        };

        Thread thread = new Thread(testTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void showAlert(String title, String message, boolean success) {
        logger.info("Showing alert: [{}] {}", title, message);
        javafx.application.Platform.runLater(() -> {
            Alert.AlertType type = success ? Alert.AlertType.INFORMATION : Alert.AlertType.ERROR;
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    @FXML
    private void cancel() {
        logger.info("Cancel button clicked.");
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Confirm Cancel");
        confirmAlert.setHeaderText(null);
        confirmAlert.setContentText("Do you really want to cancel the operation or disconnect?");

        ButtonType yesButton = new ButtonType("Yes", ButtonBar.ButtonData.YES);
        ButtonType noButton = new ButtonType("No", ButtonBar.ButtonData.NO);
        confirmAlert.getButtonTypes().setAll(yesButton, noButton);

        boolean isRunning = (connectTask != null && connectTask.isRunning())
                || (testTask != null && testTask.isRunning());
        if (isRunning) {
            confirmAlert.showAndWait().ifPresent(response -> {
                if (response == yesButton) {
                    logger.warn("User confirmed cancellation of running task.");
                    if (connectTask != null && connectTask.isRunning()) {
                        connectTask.cancel();
                        logger.warn("connectTask cancelled.");
                        if (client != null) {
                            client.forceDisconnect();
                            logger.warn("Forced disconnect on client.");
                        }
                    }
                    if (testTask != null && testTask.isRunning()) {
                        testTask.cancel();
                        logger.warn("testTask cancelled.");
                        if (client != null) {
                            client.forceDisconnect();
                            logger.warn("Forced disconnect on client.");
                        }
                    }

                    loadingSpinner.setVisible(false);

                    Alert cancelledAlert = new Alert(Alert.AlertType.INFORMATION);
                    cancelledAlert.setTitle("Operation Cancelled");
                    cancelledAlert.setHeaderText(null);
                    cancelledAlert.setContentText("Operation has been cancelled.");
                    cancelledAlert.showAndWait();

                } else if (client != null) {
                    client.disconnect();
                    logger.info("User disconnected from SFTP server.");

                    Alert disconnectedAlert = new Alert(Alert.AlertType.INFORMATION);
                    disconnectedAlert.setTitle("Disconnected");
                    disconnectedAlert.setHeaderText(null);
                    disconnectedAlert.setContentText("Disconnected successfully!");
                    disconnectedAlert.showAndWait();
                    client = null;
                }
            });
        } else {
            logger.info("No active connection to cancel.");
            Alert noConnAlert = new Alert(Alert.AlertType.INFORMATION);
            noConnAlert.setTitle("No Active Connection");
            noConnAlert.setHeaderText(null);
            noConnAlert.setContentText("There is no active connection to cancel.");
            noConnAlert.showAndWait();
        }
    }

    @FXML
    private void browseKnownHosts() {
        logger.info("Browse known hosts button clicked.");
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select known_hosts file");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("All Files", "*.*"));
        Stage stage = (Stage) knownHostsField.getScene().getWindow();
        java.io.File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            knownHostsField.setText(file.getAbsolutePath());
            logger.info("Selected known_hosts file: {}", file.getAbsolutePath());
        } else {
            logger.info("No known_hosts file selected.");
        }
    }

    private String getKnownHostsPath() {
        String path = knownHostsField.getText();
        if (path == null || path.trim().isEmpty()) {
            String userHome = System.getProperty("user.home");
            path = userHome + File.separator + ".ssh" + File.separator + "known_hosts";
        }
        return path;
    }
}
