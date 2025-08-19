package com.example;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConnectionFormController {

    private static final Logger logger = LoggerFactory.getLogger(ConnectionFormController.class);

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private ProgressIndicator loadingSpinner;

    @FXML
    private Label skpFileNameLabel; 

    private String ip, knownHosts;
    private int port;
    private SFTPClient client;
    private Task<Void> connectTask;
    private Task<Void> testTask;

    private boolean validateInputs() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        if (ip == null || ip.isEmpty()) {
            showAlert("Validation Error", "You must import a .skp config file first.", false);
            logger.error("Validation Error: .skp config not loaded.");
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
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

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

                    Stage stage = (Stage) usernameField.getScene().getWindow();
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
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

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
    private void browseSKPFile() {
        javafx.stage.FileChooser fileChooser = new javafx.stage.FileChooser();
        fileChooser.setTitle("Select .skp Config File");
        fileChooser.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("SKP Files", "*.skp"));
        Stage stage = (Stage) usernameField.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);
        if (file != null) {
            try {
                parseSKPFile(file);
                skpFileNameLabel.setText(file.getName());
                showAlert("Loaded", "Loaded config from " + file.getName(), true);
            } catch (Exception e) {
                logger.error("Failed to parse .skp file: {}", e.getMessage(), e);
                showAlert("Error", "Failed to load .skp file: " + e.getMessage(), false);
            }
        }
    }

    private void parseSKPFile(File skpFile) throws Exception {
        String content = new String(Files.readAllBytes(skpFile.toPath()));

        Pattern ipPattern = Pattern.compile("server_ip\\s*=\\s*([\\d.]+)");
        Pattern portPattern = Pattern.compile("server_port\\s*=\\s*(\\d+)");
        Pattern keyTypePattern = Pattern.compile("ssh_key_type\\s*=\\s*([\\w-]+)");
        Pattern pubKeyPattern = Pattern.compile("server_pub_key\\s*=\\s*\"\"\"([\\s\\S]+?)\"\"\"", Pattern.MULTILINE);

        ip = findFirst(ipPattern, content);
        String portStr = findFirst(portPattern, content);
        String keyType = findFirst(keyTypePattern, content);
        String pubKey = findFirst(pubKeyPattern, content);

        if (ip == null || portStr == null || keyType == null || pubKey == null) {
            throw new IllegalArgumentException("Missing required fields in .skp file.");
        }

        port = Integer.parseInt(portStr.trim());

        File keysDir = new File("keys");
        if (!keysDir.exists()) keysDir.mkdirs();
        String keyFileName = ip + "_" + keyType + ".pub";
        File pubKeyFile = new File(keysDir, keyFileName);
        Files.write(pubKeyFile.toPath(), pubKey.trim().getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        knownHosts = pubKeyFile.getAbsolutePath();
    }

    private String findFirst(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String getKnownHostsPath() {
        return knownHosts;
    }
}
