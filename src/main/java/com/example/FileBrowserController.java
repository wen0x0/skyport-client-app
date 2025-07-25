package com.example;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import javafx.stage.FileChooser;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.fxml.FXMLLoader;
import com.jcraft.jsch.ChannelSftp;
import com.jcraft.jsch.SftpException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.util.Stack;
import java.util.Vector;

public class FileBrowserController {
    private static final Logger logger = LoggerFactory.getLogger(FileBrowserController.class);

    @FXML
    private ListView<String> fileListView;
    @FXML
    private Label statusLabel;
    @FXML
    private Label pathLabel;
    @FXML
    private Label fileInfoLabel;
    @FXML
    private Label serverInfoLabel;

    private SFTPClient client;
    private String currentDir = ".";
    private Stack<String> dirHistory = new Stack<>();
    private String homeDir = null;

    public void setClient(SFTPClient client) {
        this.client = client;
        try {
            currentDir = client.pwd();
            homeDir = currentDir; // Lưu lại homeDir khi login
            String user = client.session.getUserName();
            String host = client.session.getHost();
            int port = client.session.getPort();
            serverInfoLabel.setText(user + "@" + host + ":" + port);
        } catch (Exception e) {
            currentDir = ".";
            homeDir = ".";
            serverInfoLabel.setText("Unknown");
        }
        refreshFileList();
    }

    @FXML
    private void refresh() {
        refreshFileList();
    }

    private void refreshFileList() {
        if (client == null) return;
        try {
            ObservableList<String> items = FXCollections.observableArrayList();
            Vector<ChannelSftp.LsEntry> entries = client.ls(currentDir);
            for (ChannelSftp.LsEntry entry : entries) {
                String name = entry.getFilename();
                if (!name.equals(".") && !name.equals("..")) {
                    items.add(name + (entry.getAttrs().isDir() ? "/" : ""));
                }
            }
            fileListView.setItems(items);
            pathLabel.setText(currentDir);
            logger.info("Loaded file list for directory: {}", currentDir);
            fileInfoLabel.setText("File details");
        } catch (SftpException e) {
            statusLabel.setText("Failed to load file list: " + e.getMessage());
            logger.error("Failed to load file list: {}", e.getMessage());
        }
    }

    @FXML
    private void upload() {
        if (client == null) return;
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select file to upload");
        File file = fileChooser.showOpenDialog(fileListView.getScene().getWindow());
        if (file != null) {
            try {
                client.put(file.getAbsolutePath(), currentDir + "/" + file.getName());
                statusLabel.setText("Uploaded: " + file.getName());
                logger.info("Uploaded file: {}", file.getName());
                refreshFileList();
            } catch (Exception e) {
                statusLabel.setText("Upload failed: " + e.getMessage());
                logger.error("Upload failed: {}", e.getMessage());
            }
        }
    }

    @FXML
    private void download() {
        if (client == null) return;
        String selected = fileListView.getSelectionModel().getSelectedItem();
        if (selected == null || selected.endsWith("/")) {
            statusLabel.setText("Select a file to download.");
            return;
        }
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save file as");
        fileChooser.setInitialFileName(selected);
        File file = fileChooser.showSaveDialog(fileListView.getScene().getWindow());
        if (file != null) {
            try {
                client.get(currentDir + "/" + selected, file.getAbsolutePath());
                statusLabel.setText("Downloaded: " + selected);
                logger.info("Downloaded file: {}", selected);
            } catch (Exception e) {
                statusLabel.setText("Download failed: " + e.getMessage());
                logger.error("Download failed: {}", e.getMessage());
            }
        }
    }

    @FXML
    private void delete() {
        if (client == null) return;
        String selected = fileListView.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a file or folder to delete.");
            return;
        }
        try {
            if (selected.endsWith("/")) {
                client.rmdir(currentDir + "/" + selected.replace("/", ""));
                statusLabel.setText("Deleted folder: " + selected);
                logger.info("Deleted folder: {}", selected);
            } else {
                client.rm(currentDir + "/" + selected);
                statusLabel.setText("Deleted file: " + selected);
                logger.info("Deleted file: {}", selected);
            }
            refreshFileList();
        } catch (Exception e) {
            statusLabel.setText("Delete failed: " + e.getMessage());
            logger.error("Delete failed: {}", e.getMessage());
        }
    }

    @FXML
    private void logout() {
        logger.info("Logout button clicked.");
        if (client != null) {
            client.disconnect();
        }
        Stage stage = (Stage) fileListView.getScene().getWindow();
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/connection-form.fxml"));
            Parent root = loader.load();
            stage.setScene(new Scene(root));
            stage.setTitle("Skyport Client");
        } catch (Exception e) {
            statusLabel.setText("Failed to logout: " + e.getMessage());
            logger.error("Failed to logout: {}", e.getMessage());
        }
    }

    @FXML
    private void openShell() {
        logger.info("Open Shell button clicked.");
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/shell-view.fxml"));
            Parent shellRoot = loader.load();
            ShellController shellController = loader.getController();
            shellController.setClient(client);
            shellController.setConnectionInfo(client.session.getUserName(), client.session.getHost(), client.session.getPort());

            Stage shellStage = new Stage();
            shellStage.setScene(new Scene(shellRoot, 700, 400)); 
            shellStage.setTitle("Skyport Terminal");
            shellStage.show();
        } catch (Exception e) {
            statusLabel.setText("Failed to open shell: " + e.getMessage());
            logger.error("Failed to open shell: {}", e.getMessage());
        }
    }

    @FXML
    private void initialize() {
        fileListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                fileInfoLabel.setText("Select a file to view details");
                return;
            }
            try {
                Vector<ChannelSftp.LsEntry> entries = client.ls(currentDir);
                for (ChannelSftp.LsEntry entry : entries) {
                    String name = entry.getFilename() + (entry.getAttrs().isDir() ? "/" : "");
                    if (name.equals(newVal)) {
                        if (entry.getAttrs().isDir()) {
                            fileInfoLabel.setText("Folder");
                        } else {
                            long size = entry.getAttrs().getSize();
                            fileInfoLabel.setText("File | Size: " + formatSize(size));
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                fileInfoLabel.setText("Error loading file info");
            }
        });

        fileListView.setOnMouseClicked(event -> {
            String selected = fileListView.getSelectionModel().getSelectedItem();
            if (event.getClickCount() == 2 && selected != null && selected.endsWith("/")) {
                try {
                    dirHistory.push(currentDir);
                    client.cd(currentDir + "/" + selected.replace("/", ""));
                    currentDir = client.pwd();
                    refreshFileList();
                    logger.info("Changed directory to: {}", currentDir);
                } catch (Exception e) {
                    statusLabel.setText("Failed to change directory: " + e.getMessage());
                    logger.error("Failed to change directory: {}", e.getMessage());
                }
            }
        });
    }

    // Thêm hàm formatSize vào class
    private String formatSize(long size) {
        if (size < 1024) return size + " B";
        int exp = (int) (Math.log(size) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "B";
        return String.format("%.1f %s", size / Math.pow(1024, exp), pre);
    }

    @FXML
    private void mkdir() {
        if (client == null) return;
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Create New Folder");
        dialog.setHeaderText("Enter folder name:");
        dialog.setContentText("Name:");
        dialog.initOwner(fileListView.getScene().getWindow());
        dialog.showAndWait().ifPresent(name -> {
            if (name.trim().isEmpty()) {
                statusLabel.setText("Folder name cannot be empty.");
                return;
            }
            try {
                client.mkdir(currentDir + "/" + name.trim());
                statusLabel.setText("Created folder: " + name.trim());
                logger.info("Created folder: {}", name.trim());
                refreshFileList();
            } catch (Exception e) {
                statusLabel.setText("Create folder failed: " + e.getMessage());
                logger.error("Create folder failed: {}", e.getMessage());
            }
        });
    }

    @FXML
    private void goHome() {
        if (client == null || homeDir == null) return;
        try {
            dirHistory.push(currentDir);
            client.cd(homeDir);
            currentDir = client.pwd();
            refreshFileList();
            logger.info("Changed directory to home: {}", currentDir);
        } catch (Exception e) {
            statusLabel.setText("Failed to go home: " + e.getMessage());
            logger.error("Failed to go home: {}", e.getMessage());
        }
    }

    @FXML
    private void goBack() {
        if (client == null || dirHistory.isEmpty()) return;
        try {
            String prevDir = dirHistory.pop();
            client.cd(prevDir);
            currentDir = client.pwd();
            refreshFileList();
            logger.info("Changed directory to previous: {}", currentDir);
        } catch (Exception e) {
            statusLabel.setText("Failed to go back: " + e.getMessage());
            logger.error("Failed to go back: {}", e.getMessage());
        }
    }
}