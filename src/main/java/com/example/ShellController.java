package com.example;

import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.util.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ShellController {
    private static final Logger logger = LoggerFactory.getLogger(ShellController.class);
    @FXML
    private TextArea outputArea;
    @FXML
    private TextField commandField;
    @FXML
    private Label statusIndicator;
    @FXML
    private Label userLabel;

    private Timeline blinkTimeline;
    private int commandCount = 0;
    private SFTPClient client;
    private boolean isConnected = false;

    private String username;
    private String ip;
    private int port;

    @FXML
    private void initialize() {
        commandField.requestFocus();

        displayWelcomeMessage();

        setupStatusIndicator();

        commandField
                .setOnMouseEntered(e -> commandField.setStyle(commandField.getStyle() + "-fx-border-color: #61dafb;"));
        commandField.setOnMouseExited(e -> commandField
                .setStyle(commandField.getStyle().replace("-fx-border-color: #61dafb;", "-fx-border-color: #555555;")));
    }

    public void setConnectionInfo(String username, String ip, int port) {

        this.username = username;
        this.ip = ip;
        this.port = port;
        userLabel.setText(username + "@" + ip + ":" + port);
        outputArea.appendText("Connected to: " + this.username + "@" + this.ip + ":" + this.port + "\n");
    }

    public void setClient(SFTPClient client) {
        this.client = client;
        this.isConnected = true;

        statusIndicator.setStyle("-fx-text-fill: #00ff41;");
        if (blinkTimeline != null) {
            blinkTimeline.stop();
        }

        String connectMsg = """
                ╔═══════════════════════════════════════════════════════════════╗
                ║                 SFTP Connection Established                   ║
                ║                                                               ║
                ║  You are now connected to the SFTP server.                    ║
                ║  Type 'help' to see available SFTP commands.                  ║
                ╚═══════════════════════════════════════════════════════════════╝
                """;
        outputArea.appendText(connectMsg);

    }

    private void displayWelcomeMessage() {
        String welcomeMsg = """
                ╔═══════════════════════════════════════════════════════════════╗
                ║                 Welcome to Skyport Terminal                   ║
                ║                                                               ║
                ║  Type 'help' to see available commands                        ║
                ║  Type 'clear' to clear the screen                             ║
                ║  Type 'exit' to quit                                          ║
                ╚═══════════════════════════════════════════════════════════════╝
                """;
        outputArea.appendText(welcomeMsg);
    }

    private void setupStatusIndicator() {
        blinkTimeline = new Timeline(
                new KeyFrame(Duration.seconds(0.5), e -> {
                    statusIndicator.setStyle(statusIndicator.getStyle() + "-fx-text-fill: #404040;");
                }),
                new KeyFrame(Duration.seconds(1), e -> {
                    statusIndicator.setStyle(
                            statusIndicator.getStyle().replace("-fx-text-fill: #404040;", "-fx-text-fill: #00ff41;"));
                }));
        blinkTimeline.setCycleCount(Timeline.INDEFINITE);
        blinkTimeline.play();
    }

    @FXML
    private void executeCommand() {
        String command = commandField.getText().trim();
        if (!command.isEmpty()) {
            commandCount++;
            // Hiển thị command với styling
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            outputArea.appendText(String.format("┌─ [%s] Command #%d\n", timestamp, commandCount));
            outputArea.appendText(String.format("│  %s@terminal > %s\n", username, command));
            outputArea.appendText("└─ Output:\n");
            // Xử lý command
            processCommand(command);
            // Thêm separator
            outputArea.appendText("\n" + "─".repeat(60) + "\n\n");
            // Xóa command field
            commandField.clear();
        }
    }

    private void processCommand(String command) {
        String[] parts = command.split("\\s+");
        String mainCommand = parts[0].toLowerCase();

        try {
            // SFTP Commands (if connected)
            if (isConnected && client != null) {
                switch (mainCommand) {
                    case "pwd":
                        logger.info("Executing pwd command");
                        String pwd = client.pwd();
                        outputArea.appendText("    Current directory: " + pwd + "\n");
                        logger.info("Current directory: {}", pwd);
                        return;
                    case "ls":
                        logger.info("Executing ls command");
                        String path = parts.length > 1 ? parts[1] : ".";
                        outputArea.appendText("    Directory listing for: " + path + "\n");
                        client.ls(path).forEach(entry -> outputArea.appendText("      " + entry.getFilename() + "\n"));
                        logger.info("Directory listing for: {}", path);
                        return;
                    case "cd":
                        logger.info("Executing cd command");
                        if (parts.length < 2) {
                            outputArea.appendText("    Usage: cd <path>\n");
                            logger.warn("cd command usage error: path not specified");
                        } else {
                            client.cd(parts[1]);
                            outputArea.appendText("    Changed directory to: " + parts[1] + "\n");
                            logger.info("Changed directory to: {}", parts[1]);
                        }
                        return;
                    case "mkdir":
                        logger.info("Executing mkdir command");
                        if (parts.length < 2) {
                            outputArea.appendText("    Usage: mkdir <directory>\n");
                            logger.warn("mkdir command usage error: directory not specified");
                        } else {
                            client.mkdir(parts[1]);
                            outputArea.appendText("    Directory created: " + parts[1] + "\n");
                            logger.info("Directory created: {}", parts[1]);
                        }
                        return;
                    case "rm":
                        logger.info("Executing rm command");
                        if (parts.length < 2) {
                            outputArea.appendText("    Usage: rm <file>\n");
                            logger.warn("rm command usage error: file not specified");
                        } else {
                            client.rm(parts[1]);
                            outputArea.appendText("    File deleted: " + parts[1] + "\n");
                            logger.info("File deleted: {}", parts[1]);
                        }
                        return;
                    case "get":
                        logger.info("Executing get command");
                        if (parts.length < 3) {
                            outputArea.appendText("    Usage: get <remote_file> <local_file>\n");
                            logger.warn("get command usage error: remote or local file not specified");
                        } else {
                            client.get(parts[1], parts[2]);
                            outputArea.appendText("    File downloaded: " + parts[1] + " → " + parts[2] + "\n");
                            logger.info("File downloaded: {} → {}", parts[1], parts[2]);
                        }
                        return;
                    case "put":
                        logger.info("Executing put command");
                        if (parts.length < 3) {
                            outputArea.appendText("    Usage: put <local_file> <remote_file>\n");
                            logger.warn("put command usage error: local or remote file not specified");
                        } else {
                            client.put(parts[1], parts[2]);
                            outputArea.appendText("    File uploaded: " + parts[1] + " → " + parts[2] + "\n");
                            logger.info("File uploaded: {} → {}", parts[1], parts[2]);
                        }
                        return;
                    case "disconnect":
                        logger.info("Executing disconnect command");
                        client.disconnect();
                        isConnected = false;
                        outputArea.appendText("    Disconnected from SFTP server\n");
                        userLabel.setText(username + "@terminal");
                        setupStatusIndicator(); // Restart blinking
                        logger.info("Disconnected from SFTP server");
                        return;
                }
            }

            // Standard Terminal Commands
            switch (mainCommand) {
                case "clear":
                    logger.info("Executing clear command");
                    outputArea.clear();
                    displayWelcomeMessage();
                    break;
                case "help":
                    logger.info("Executing help command");
                    displayHelpMessage();
                    break;
                case "exit":
                    logger.info("Executing exit command");
                    if (isConnected && client != null) {
                        client.disconnect();
                        outputArea.appendText("    Disconnected from SFTP server\n");
                        logger.info("Disconnected from SFTP server");
                    }
                    outputArea.appendText("    Goodbye! Terminal session ended.\n");

                    Timeline exitTimeline = new Timeline(
                            new KeyFrame(Duration.seconds(1), e -> System.exit(0)));
                    exitTimeline.play();
                    break;
                case "date":
                    logger.info("Executing date command");
                    outputArea.appendText("    Current date and time: " +
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
                    break;
                case "whoami":
                    logger.info("Executing whoami command");
                    outputArea.appendText("    " + username + "\n");
                    break;
                case "echo":
                    logger.info("Executing echo command");
                    if (parts.length > 1) {
                        String message = command.substring(5); // Remove "echo "
                        outputArea.appendText("    " + message + "\n");
                        logger.info("Echoed message: {}", message);
                    } else {
                        outputArea.appendText("   \n");
                        logger.warn("echo command usage error: no message provided");
                    }
                    break;
                case "version":
                    logger.info("Executing version command");
                    outputArea.appendText("    Modern SFTP Terminal v1.0\n");
                    outputArea.appendText("    Built with JavaFX\n");
                    if (isConnected) {
                        outputArea.appendText("    SFTP Status: Connected\n");
                        
                    } else {
                        outputArea.appendText("    SFTP Status: Disconnected\n");
                    }
                    break;
                case "status":
                    logger.info("Executing status command");
                    outputArea.appendText("Terminal Status:\n");
                    outputArea.appendText("   │  Commands executed: " + commandCount + "\n");
                    outputArea.appendText(
                            "   │  SFTP Connection: " + (isConnected ? "Connected" : "Disconnected") + "\n");
                    outputArea.appendText("   │  User: " + username + "\n");
                    outputArea.appendText("   │  Working Directory: " + client.pwd() + "\n");
                    break;
                default:
                    logger.warn("Unknown command: {}", command);
                    outputArea.appendText("Unknown command: '" + command + "'\n");
                    outputArea.appendText("Type 'help' for available commands\n");
            }
        } catch (Exception e) {
            outputArea.appendText("Error: " + e.getMessage() + "\n");
            logger.error("Error executing command '{}': {}", command, e.getMessage(), e);
        }
        // Auto scroll to bottom
        outputArea.setScrollTop(Double.MAX_VALUE);
    }

    private void displayHelpMessage() {
        String helpMsg;
        if (isConnected) {
            helpMsg = """
                    ╭─────────────────────────────────────────────────────────────╮
                    │                   SFTP Commands Available                   │
                    ├─────────────────────────────────────────────────────────────┤
                    │  pwd             - Show current remote directory            │
                    │  ls [path]       - List directory contents                  │
                    │  cd <path>       - Change remote directory                  │
                    │  mkdir <dir>     - Create remote directory                  │
                    │  rm <file>       - Delete remote file                       │
                    │  get <remote> <local> - Download file from server           │
                    │  put <local> <remote> - Upload file to server               │
                    │  disconnect      - Disconnect from SFTP server              │
                    ├─────────────────────────────────────────────────────────────┤
                    │                     Terminal Commands                       │
                    ├─────────────────────────────────────────────────────────────┤
                    │  clear           - Clear the terminal screen                │
                    │  help            - Show this help message                   │
                    │  exit            - Exit the terminal application            │
                    │  date            - Show current date and time               │
                    │  whoami          - Show current user name                   │
                    │  echo <message>  - Display a message                        │
                    │  version         - Show terminal version information        │
                    │  status          - Show terminal and connection status      │
                    ╰─────────────────────────────────────────────────────────────╯
                    """;
        } else {
            helpMsg = """
                    ╭─────────────────────────────────────────────────────────────╮
                    │                      Terminal Commands                      │
                    ├─────────────────────────────────────────────────────────────┤
                    │  clear           - Clear the terminal screen                │
                    │  help            - Show this help message                   │
                    │  exit            - Exit the terminal application            │
                    │  date            - Show current date and time               │
                    │  whoami          - Show current user name                   │
                    │  echo <message>  - Display a message                        │
                    │  version         - Show terminal version information        │
                    │  status          - Show terminal status                     │
                    ├─────────────────────────────────────────────────────────────┤
                    │                     SFTP Not Connected!                     │
                    ├─────────────────────────────────────────────────────────────┤
                    │  Connect to SFTP server to access file transfer commands    │
                    ╰─────────────────────────────────────────────────────────────╯
                    """;
        }
        outputArea.appendText(helpMsg);
    }
}