package com.example;

import com.jcraft.jsch.*;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Properties;
import java.util.Set;
import java.util.Vector;

public class SFTPClient {

    private final JSch jsch;
    private Session session;
    private ChannelSftp sftpChannel;
    private String knownHostsPath;

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
    }

    public void connect(String username, String host, int port, String password) throws JSchException {
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
        System.out.println("Connected to " + host + ":" + port);
        // Save host key after successful connection
        try {
            saveHostKey();
        } catch (Exception e) {
            System.out.println("Failed to save host key: " + e.getMessage());
        }
        // Open SFTP channel
        sftpChannel = (ChannelSftp) session.openChannel("sftp");
        sftpChannel.connect();
        System.out.println("SFTP channel opened.");
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
        System.out.println("Host key saved to " + knownHostsPath);
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
            System.out.println("SFTP channel disconnected.");
        }
        if (session != null && session.isConnected()) {
            session.disconnect();
            System.out.println("Session disconnected.");
        }
    }

    // SFTP operations
    @SuppressWarnings("unchecked")
    public Vector<ChannelSftp.LsEntry> ls(String path) throws SftpException {
        return sftpChannel.ls(path);
    }

    public void cd(String path) throws SftpException {
        sftpChannel.cd(path);
    }

    public String pwd() throws SftpException {
        return sftpChannel.pwd();
    }

    public void mkdir(String path) throws SftpException {
        sftpChannel.mkdir(path);
    }

    public void rm(String path) throws SftpException {
        sftpChannel.rm(path);
    }

    public void get(String remote, String local) throws SftpException {
        sftpChannel.get(remote, local);
    }

    public void put(String local, String remote) throws SftpException {
        sftpChannel.put(local, remote);
    }

    public void forceDisconnect() {
        if (session != null && session.isConnected()) {
            session.disconnect();
            System.out.println("Force disconnected.");
        }
    }

}
