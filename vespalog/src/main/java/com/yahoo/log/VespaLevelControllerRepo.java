// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.log;

import com.yahoo.text.Utf8;

import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Enumeration;
import java.util.TimerTask;
import java.util.logging.LogManager;
import java.util.logging.Logger;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class VespaLevelControllerRepo implements LevelControllerRepo {

    private RandomAccessFile ctlFile;
    private MappedByteBuffer mapBuf;
    private MappedLevelControllerRepo levelControllerRepo;
    private final String logControlFilename;
    private final String appPrefix;

    private static final int maxPrefix = 64;
    private static final String CFHEADER = "Vespa log control file version 1\n";
    private static final String CFPREPRE = "Prefix: ";

    /**
     * length of fixed header content of a control file, constant:
     **/
    public static final int controlFileHeaderLength;
    /**
     * number of distinctly controlled levels (in logctl files),
     * must be compatible with C++ Vespa logging
     **/
    public static final int numLevels = 8;

    static {
        controlFileHeaderLength = CFHEADER.length()
                                  + CFPREPRE.length()
                                  + 1 // newline
                                  + maxPrefix
                                  + 1; // newline
    }
    /**
     * level controller for default levels (first-time logging
     * or logging without a logcontrol file)
     **/
    private LevelController defaultLevelCtrl;
    private String defaultLogLevel;

    public VespaLevelControllerRepo(String logCtlFn, String logLevel, String applicationPrefix) {
        this.logControlFilename = logCtlFn;
        this.defaultLogLevel = logLevel;
        this.appPrefix = applicationPrefix;
        defaultLevelCtrl = new DefaultLevelController(defaultLogLevel);
        openCtlFile();
    }

    private void resetCtlFile() {
        // System.err.println("reset limit to: "+defaultLevelCtrl.getLevelLimit());
        Logger.getLogger("").setLevel(defaultLevelCtrl.getLevelLimit());
        try {
            if (ctlFile != null) {
                ctlFile.close();
            }
        } catch (java.io.IOException ign) {}
        ctlFile = null;
        mapBuf = null;
        levelControllerRepo = null;
    }

    private void openCtlFile() {
        if (ctlFile != null) {
            // already done this
            return;
        }
        if (logControlFilename == null) {
            // System.err.println("initialize limit to: "+defaultLevelCtrl.getLevelLimit());
            Logger.getLogger("").setLevel(defaultLevelCtrl.getLevelLimit());
            // only default level controller, very little can be done
            return;
        }

        try {
            ctlFile = new RandomAccessFile(logControlFilename, "rw");
            ensureHeader();
            extendMapping();

            if (checkBackRunner == null) {
                checkBackRunner = new CheckBackRunner();
                LogSetup.getTaskRunner().schedule(checkBackRunner, 1000, 9999);
            }
        } catch (java.io.IOException e) {
            System.err.println("problem opening logcontrol file "
                               +logControlFilename+": "+e);
            resetCtlFile();
        }
    }

    private void ensureHeader() throws java.io.IOException {
        byte[] hbytes = Utf8.toBytes(CFHEADER);
        byte[] rbytes = new byte[hbytes.length];

        ctlFile.seek(0);
        int l = ctlFile.read(rbytes);
        if (l != hbytes.length
            || !java.util.Arrays.equals(hbytes, rbytes))
        {
            ctlFile.seek(0);
            ctlFile.write(hbytes);
            ctlFile.writeBytes(CFPREPRE);
            int appLen = 0;
            if (appPrefix != null) {
                appLen = appPrefix.length();
                ctlFile.writeBytes(appPrefix);
            }
            ctlFile.writeBytes("\n");
            for (int i = appLen; i < maxPrefix; i++) {
                byte space = ' ';
                ctlFile.write(space);
            }
            ctlFile.writeBytes("\n");
            ctlFile.setLength(ctlFile.getFilePointer());
            if (ctlFile.getFilePointer() != controlFileHeaderLength) {
                System.err.println("internal error, bad header length: "
                                   + ctlFile.getFilePointer()
                                   + " (should have been: "
                                   + controlFileHeaderLength
                                   + ")");
            }
        }
    }

    private void extendMapping() throws java.io.IOException {
        if (ctlFile == null) return;
        long pos = 0;
        long len = ctlFile.length();
        if (mapBuf == null || mapBuf.capacity() != len) {
            mapBuf = ctlFile.getChannel().map(FileChannel.MapMode.READ_ONLY, pos, len);
        }
        levelControllerRepo = new MappedLevelControllerRepo(mapBuf, controlFileHeaderLength, numLevels, logControlFilename);
    }

    public LevelController getLevelControl(String suffix) {
        LevelController ctrl = null;
        if (levelControllerRepo != null) {
            if (suffix == null || suffix.equals("default")) {
                suffix = "";
            }
            ctrl = levelControllerRepo.getLevelController(suffix);
            if (ctrl != null) {
                return ctrl;
            }
            synchronized(this) {
                if (ctlFile == null) {
                    return defaultLevelCtrl;
                }
                LevelController inherit = null;

                int lastdot = suffix.lastIndexOf('.');
                if (lastdot != -1) {
                    // inherit from level above
                    inherit = getLevelControl(suffix.substring(0, lastdot));
                } else if (suffix.equals("")) {
                    // the one and only toplevel inherits from other mechanism
                    inherit = defaultLevelCtrl;
                } else {
                    // everything else inherits from toplevel
                    inherit = getLevelControl("");
                }
                try {
                    long len = ctlFile.length();
                    String append;
                    if (suffix.equals("")) {
                        append = "default" + ": ";
                    } else {
                        append = "." + suffix + ": ";
                    }
                    while ((len + append.length()) % 4 != 0) {
                        append = append + " ";
                    }
                    append = append + inherit.getOnOffString() + "\n";
                    ctlFile.seek(ctlFile.length());
                    ctlFile.writeBytes(append);
                    extendMapping();
                    ctrl = levelControllerRepo.getLevelController(suffix);
                } catch(java.nio.channels.ClosedByInterruptException e) {
                    // happens during shutdown, ignore
                    // System.err.println("interrupt, reset logcontrol file: "+e);
                    resetCtlFile();
                } catch(java.io.IOException e) {
                    System.err.println("error extending logcontrol file: "+e);
                    e.printStackTrace();
                    resetCtlFile();
                }
            }
        }
        if (ctrl == null) {
            return defaultLevelCtrl;
        } else {
            return ctrl;
        }
    }


    private void checkBack() {
        if (levelControllerRepo != null) {
            Enumeration<String> e = LogManager.getLogManager().getLoggerNames();
            while (e.hasMoreElements()) {
                String name = e.nextElement();
                LevelController ctrl = getLevelControl(name);
                ctrl.checkBack();
            }
            levelControllerRepo.checkBack();
        }
    }

    @Override
    public LevelController getLevelController(String component) {
        return getLevelControl(component);
    }

    private class CheckBackRunner extends TimerTask {
        public void run() {
            checkBack();
        }
    }
    private CheckBackRunner checkBackRunner;

    @Override
    public void close() {
        if (checkBackRunner != null) {
            checkBackRunner.cancel();
        }
    }
}
