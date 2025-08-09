package com.example;

import com.jcraft.jsch.*;
import com.jcraft.jsch.SftpProgressMonitor;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SFTPClient {
    private final JSch jsch;
    Session session;
    private ChannelSftp sftpChannel;
    private String knownHostsPath;
    private static final Logger logger = LoggerFactory.getLogger(SFTPClient.class);

    public SFTPClient(String knownHostsPath) throws JSchException {
        this.jsch = new JSch();

        if (knownHostsPath == null || knownHostsPath.isEmpty()) {
            String projectDir = System.getProperty("user.dir");
            File keysDir = new File(projectDir, "keys");

            if (!keysDir.exists()) {
                keysDir.mkdirs();
            }

            File f = new File(keysDir, "known_hosts");
            this.knownHostsPath = f.getPath();

            if (!f.exists()) {
                try {
                    f.createNewFile();
                    System.out.println("Created known_hosts file: " + f.getPath());
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create known_hosts file: " + f.getPath(), e);
                }
            }
        } else {
            File f = new File(knownHostsPath);
            if (f.isDirectory()) {
                f = new File(f, "known_hosts");
            }
            this.knownHostsPath = f.getPath();

            if (!f.exists()) {
                try {
                    f.createNewFile();
                    System.out.println("Created known_hosts file: " + f.getPath());
                } catch (IOException e) {
                    throw new RuntimeException("Failed to create known_hosts file: " + f.getPath(), e);
                }
            }
        }

        jsch.setKnownHosts(this.knownHostsPath);
        logger.info("SFTPClient initialized with knownHostsPath: {}", this.knownHostsPath);
    }

    public void connect(String username, String host, int port, String password) throws JSchException {
        logger.info("Connecting to {}:{} as {}", host, port, username);
        session = jsch.getSession(username, host, port);
        session.setPassword(password);

        // Configure session
        Properties config = new Properties();
        config.put("StrictHostKeyChecking", "ask");
        session.setConfig(config);

        // UserInfo with auto-accept for GUI usage
        session.setUserInfo(new UserInfo() {
            @Override
            public String getPassphrase() {
                return null;
            }

            @Override
            public String getPassword() {
                return null;
            }

            @Override
            public boolean promptPassword(String message) {
                return false;
            }

            @Override
            public boolean promptPassphrase(String message) {
                return false;
            }

            @Override
            public boolean promptYesNo(String message) {
                System.out.println(message + " (auto accept)");
                return true;
            }

            @Override
            public void showMessage(String message) {
                System.out.println(message);
            }
        });

        session.connect();
        logger.info("Connected to {}:{}", host, port);
        // Save host key after successful connection
        try {
            saveHostKey();
        } catch (Exception e) {
            logger.error("Failed to save host key: {}", e.getMessage());
        }
        // Open SFTP channel
        sftpChannel = (ChannelSftp) session.openChannel("sftp");
        sftpChannel.connect();
        logger.info("SFTP channel opened.");
    }

    private void saveHostKey() throws Exception {
        HostKey hk = session.getHostKey();
        if (hk == null || knownHostsPath == null || knownHostsPath.isEmpty()) {
            return;
        }

        String entry = hk.getHost() + " " + hk.getType() + " " + hk.getKey() + "\n";
        try (FileWriter fw = new FileWriter(knownHostsPath, true)) {
            fw.write(entry);
        }
        setFilePermission600(knownHostsPath);
        logger.info("Host key saved to {}", knownHostsPath);
    }

    private void setFilePermission600(String path) throws IOException {
        Path p = Paths.get(path);
        try {
            Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
            Files.setPosixFilePermissions(p, perms);
        } catch (UnsupportedOperationException e) {
            // Windows or non-POSIX FS ignore
        }
    }

    public void disconnect() {
        if (sftpChannel != null && sftpChannel.isConnected()) {
            sftpChannel.disconnect();
            logger.info("SFTP channel disconnected.");
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
            logger.info("Session disconnected.");
        }
    }

    // SFTP operations
    @SuppressWarnings("unchecked")
    public Vector<ChannelSftp.LsEntry> ls(String path) throws SftpException {
        return sftpChannel.ls(path);
    }

    public String pwd() throws SftpException {
        return sftpChannel.pwd();
    }

    public void cd(String path) throws SftpException {
        sftpChannel.cd(path);
    }

    public void put(String local, String remote) throws SftpException {
        sftpChannel.put(local, remote);
    }

    public void get(String remote, String local) throws SftpException {
        sftpChannel.get(remote, local);
    }

    public void rm(String remote) throws SftpException {
        sftpChannel.rm(remote);
    }

    public void rmdir(String remote) throws SftpException {
        sftpChannel.rmdir(remote);
    }

    public void forceDisconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
            logger.warn("Force disconnected.");
        }
    }

    public void mkdir(String remote) throws SftpException {
        sftpChannel.mkdir(remote);
    }

    public void put(String local, String remote, SftpProgressMonitor monitor) throws SftpException {
        sftpChannel.put(local, remote, monitor, ChannelSftp.OVERWRITE);
    }

    public void get(String remote, String local, SftpProgressMonitor monitor) throws SftpException {
        sftpChannel.get(remote, local, monitor, ChannelSftp.OVERWRITE);
    }

}
