package com.winlator.xconnector;

import com.winlator.core.FileUtils;

import java.io.File;

public class UnixSocketConfig {
    public static final String SYSVSHM_SERVER_PATH = "/tmp/.sysvshm/SM0";
    public static final String ALSA_SERVER_PATH = "/tmp/.sound/AS0";
    public static final String PULSE_SERVER_PATH = "/tmp/.sound/PS0";
    public static final String XSERVER_PATH = "/tmp/.X11-unix/X0";
    public static final String VIRGL_SERVER_PATH = "/tmp/.virgl/V0";
    public static final String STEAM_PIPE_PATH = "/tmp/.steam/steam_pipe";
    public static final String VORTEK_SERVER_PATH = "/tmp/.vortek/V0";

    /** Full absolute path of the filesystem socket (e.g. imagefs/tmp/.X11-unix/X0). */
    public final String path;

    /**
     * When non-null the connector will ALSO bind a Linux abstract-namespace socket
     * with this name (no leading null — the C layer adds it).  Xlib/XCB inside Wine
     * probes the abstract socket FIRST, so this lets winex11.drv connect without
     * needing a real /tmp directory on the Android host.  Only set for the X server.
     */
    public final String abstractSocketName;

    private UnixSocketConfig(String path, String abstractSocketName) {
        this.path = path;
        this.abstractSocketName = abstractSocketName;
    }

    /** Create a filesystem-only socket (used for ALSA, Pulse, SysVSHM, VirGL, etc.). */
    public static UnixSocketConfig createSocket(String rootPath, String relativePath) {
        return createSocket(rootPath, relativePath, false);
    }

    /**
     * Create a socket.  When {@code useAbstractNamespace} is true an additional
     * abstract-namespace socket is registered under the same relative path so that
     * clients that probe the abstract socket first (Wine's winex11.drv) can connect.
     */
    public static UnixSocketConfig createSocket(String rootPath, String relativePath,
                                                boolean useAbstractNamespace) {
        File socketFile = new File(rootPath, relativePath);

        String dirname = FileUtils.getDirname(relativePath);
        if (dirname.lastIndexOf("/") > 0) {
            File socketDir = new File(rootPath, FileUtils.getDirname(relativePath));
            FileUtils.delete(socketDir);
            socketDir.mkdirs();
        } else {
            socketFile.delete();
        }

        return new UnixSocketConfig(socketFile.getPath(),
                useAbstractNamespace ? relativePath : null);
    }
}
