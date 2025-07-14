package com.example;

import java.io.IOException;
import java.net.URL;

import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public class SFTPClientController {

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
            return false;
        }
        if (!ip.matches("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.|$)){4}$")) {
            showAlert("Validation Error", "Invalid IP address format.", false);
            return false;
        }
        if (portStr.isEmpty()) {
            showAlert("Validation Error", "Port number is required.", false);
            return false;
        }
        try {
            port = Integer.parseInt(portStr);
            if (port < 1 || port > 65535) {
                showAlert("Validation Error", "Invalid port number.", false);
                return false;
            }
        } catch (NumberFormatException e) {
            showAlert("Validation Error", "Invalid port number.", false);
            return false;
        }
        if (username.isEmpty()) {
            showAlert("Validation Error", "Username is required.", false);
            return false;
        }
        if (password.isEmpty()) {
            showAlert("Validation Error", "Password is required.", false);
            return false;
        }

        return true;
    }

    @FXML
    private void connect() {
        if (!validateInputs())
            return;

        loadingSpinner.setVisible(true);

        connectTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (isCancelled())
                    return null;
                client = new SFTPClient(knownHosts);
                if (isCancelled())
                    return null;
                client.connect(username, ip, port, password);
                return null;
            }

            @Override
            protected void succeeded() {
                loadingSpinner.setVisible(false);
                showAlert("Connection Successful", "Connected to " + ip + ":" + port, true);

                try {
                    URL shellView = getClass().getResource("/shell-view.fxml");
                    System.out.println("Shell view URL: " + shellView);
                    if (shellView == null) {
                        showAlert("Error", "shell-view.fxml not found in resources.", false);
                        return;
                    }

                    FXMLLoader loader = new FXMLLoader(shellView);
                    Parent shellRoot = loader.load();

                    ShellController shellController = loader.getController();
                    shellController.setClient(client);
                    shellController.setConnectionInfo(username, ip, port); 

                    Stage stage = (Stage) ipAddressField.getScene().getWindow();
                    stage.setScene(new Scene(shellRoot));
                    stage.setTitle("Skyport Terminal");
                    // stage.setFullScreen(true);

                    stage.setMaximized(true);

                } catch (IOException e) {
                    showAlert("Error", "Failed to load shell view: " + e.getMessage(), false);
                }
            }

            @Override
            protected void failed() {
                loadingSpinner.setVisible(false);
                showAlert("Connection Failed", getException().getMessage(), false);
            }

            @Override
            protected void cancelled() {
                loadingSpinner.setVisible(false);
                showAlert("Cancelled", "Connection attempt cancelled.", false);
            }
        };

        Thread thread = new Thread(connectTask);
        thread.setDaemon(true);
        thread.start();
    }

    @FXML
    private void testConnection() {
        if (!validateInputs())
            return;

        loadingSpinner.setVisible(true);

        testTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                if (isCancelled())
                    return null;
                SFTPClient testClient = new SFTPClient(knownHosts);
                if (isCancelled())
                    return null;
                testClient.connect(username, ip, port, password);
                if (isCancelled()) {
                    testClient.disconnect();
                    return null;
                }
                testClient.disconnect();
                return null;
            }

            @Override
            protected void succeeded() {
                loadingSpinner.setVisible(false);
                showAlert("Test Successful", "Successfully connected to " + ip + ":" + port, true);
            }

            @Override
            protected void failed() {
                loadingSpinner.setVisible(false);
                showAlert("Test Failed", "Failed to connect: " + getException().getMessage(), false);
            }

            @Override
            protected void cancelled() {
                loadingSpinner.setVisible(false);
                showAlert("Cancelled", "Test connection cancelled.", false);
            }
        };

        Thread thread = new Thread(testTask);
        thread.setDaemon(true);
        thread.start();
    }

    private void showAlert(String title, String message, boolean success) {
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

                    if (connectTask != null && connectTask.isRunning()) {
                        connectTask.cancel();
                        if (client != null) {
                            client.forceDisconnect(); // force disconnect ongoing connect
                        }
                    }
                    if (testTask != null && testTask.isRunning()) {
                        testTask.cancel();
                        if (client != null) {
                            client.forceDisconnect();
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

                    Alert disconnectedAlert = new Alert(Alert.AlertType.INFORMATION);
                    disconnectedAlert.setTitle("Disconnected");
                    disconnectedAlert.setHeaderText(null);
                    disconnectedAlert.setContentText("Disconnected successfully!");
                    disconnectedAlert.showAndWait();
                    client = null;

                }
            });
        } else {
            Alert noConnAlert = new Alert(Alert.AlertType.INFORMATION);
            noConnAlert.setTitle("No Active Connection");
            noConnAlert.setHeaderText(null);
            noConnAlert.setContentText("There is no active connection to cancel.");
            noConnAlert.showAndWait();
        }
    }

    @FXML
    private void browseKnownHosts() {
        System.out.println("Browse clicked!");
    }
}
