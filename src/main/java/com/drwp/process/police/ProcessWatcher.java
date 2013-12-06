package com.drwp.process.police;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.netgiro.config.PersistentConfiguration;

public class ProcessWatcher implements Runnable {

    private static final Logger cLogger = LoggerFactory.getLogger(ProcessWatcher.class);
    private final List<String> iPidDirs;
    private final ProblemReporter iProblemReporter = new ProblemReporter();
    private final int dontRestartMoreOftenThan;
    //This is actually a memory leak... but a very slow one
    private final Map<String,List<Long>> appRestartedMap= new HashMap<String, List<Long>>();
    private final List<String> iBlockedApplications = new ArrayList<String>();
    private final AtomicBoolean iRunning = new AtomicBoolean(true);

    public ProcessWatcher(PersistentConfiguration csConfig) {
        iPidDirs = getPidDirs(csConfig);
        dontRestartMoreOftenThan = csConfig.getInt("pp.restart.dead.apps.after.secs");
    }

    public void run() {
        while ( iRunning.get() ) {
            runImpl();
            safeSleep(10000L);
        }
    }

    private static void safeSleep(long l) {
        try {
            Thread.sleep(l);
        } catch (InterruptedException e) {
            cLogger.warn("interrupted",e);
        }
    }

    void runImpl() {
        List<Object[]> pidsAndNames = getPidsAndNames(iPidDirs);
        checkPids(pidsAndNames);
    }

    private void checkPids(List<Object[]> pidsAndNames) {
        for (Object[] pn : pidsAndNames) {
            String name = (String)pn[1];
            if ( applicationBlocked(name,iBlockedApplications) ) {
                cLogger.warn("Application {} marked as 'blocked' skipping check",name);
                continue;
            }
            int pid = (Integer)pn[0];
            cLogger.debug("checking pid '{}'",pid);
            try {
                if (isItDead(pid)) {
                    iProblemReporter.reportDeath(pid,name);
                    maybeRestart(name, dontRestartMoreOftenThan);
                } else {
                    cLogger.debug("Pid {} ok",pid);
                }
            } catch (IOException e) {
                cLogger.warn("Unable to check pid {}",pid,e);
            }
        }
        cLogger.debug("Done checking {} pids",pidsAndNames.size());
    }

    private static boolean applicationBlocked(String name, List<String> blockedApplications) {
        for (String blocked : blockedApplications) {
            if ( blocked.equals(name) ) {
                return true;
            }
        }
        return false;
    }

    private void maybeRestart(String name, int dontRestartMoreOften ) {
        List<Long> restartedTs  = appRestartedMap.get(name);
        removePidFile(name+".pid", iPidDirs);

        //never restarted
        if ( restartedTs == null ) {
            restart(name);
            List<Long> l = new ArrayList<Long>();
            l.add( System.currentTimeMillis() );
            appRestartedMap.put(name, l);
            return;
        }
        // restarted "too long ago"
        if ( restartedTs.get(restartedTs.size()-1) < (System.currentTimeMillis()-dontRestartMoreOften*1000L) ) {
            restart(name);
            restartedTs.add(System.currentTimeMillis());
            return;
        }
        cLogger.error("Application {} seems to be an 'early crasher' giving up on it.");
        iBlockedApplications .add(name);
    }

    private static void restart(String name) {
        cLogger.error("Restarting application '{}'",name);
        String[] startCmd = new String[] {"/opt/netgiro/"+name+"/scripts/server.sh","start"};
        cLogger.warn("Command: " + StringUtils.join(startCmd, " "));
        try {
            Process  p = Runtime.getRuntime().exec(startCmd);
            BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ( (line = input.readLine()) != null) {
                cLogger.warn(line);
            }
            cLogger.warn("Application {} restarted",name);
        } catch (IOException e) {
            cLogger.error("Unable to restart application "+name,e);
        }
    }

    private static void removePidFile(String pidFileName, List<String> pidDirs) {
        List<File> pidFiles = getPidFiles(pidDirs);
        for (File f : pidFiles ) {
            if ( f.getName().equals(pidFileName) ) {
                cLogger.info("removing pid file {}",f.getAbsoluteFile());
                f.delete();
                return;
            }
        }
    }

    //dirty hack assuming 'ps' is avalaible
    private static boolean isItDead(int pid) throws IOException {
        Process p = Runtime.getRuntime().exec(new String[] {"sh","-c", "ps aux | grep "+ pid});

        BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
        while (input.readLine() != null) {
            return false;
        }
        cLogger.error("pid {} is dead!",pid);
        return true;
    }

    private static List<File> getPidFiles( List<String> pidDirs ) {
        List<File> res = new ArrayList<File>();
        for (String dir : pidDirs) {
            File f = new File(dir);
            if ( !f.exists() || !f.isDirectory() ) {
                cLogger.warn("Directory: '{}' does not exist or is not a directory",f );
                continue;
            }
            File[] pidFiles = f.listFiles(new FilenameFilter() {
                public boolean accept(File d, String name) {
                    return StringUtils.endsWith(name, "pid");
                }
            });
            res.addAll(Arrays.asList( pidFiles ) );
        }
        return res;
    }

    private List<Object[]> getPidsAndNames(List<String> pidDirs) {
        List<File> pidFiles = getPidFiles(pidDirs);
        List<Object[]> res = new ArrayList<Object[]>();
        for (File file : pidFiles) {
            try {
                int pid = getPidFromFile(file);
                Object[] o = new Object[2];
                o[0] = pid;
                o[1] = getAppName(file);
                cLogger.debug("found pid '{}' for app '{}'",pid,o[1] );
                res.add(o);
            } catch (Exception e) {
                iProblemReporter.reportProblem("File "+file.getAbsolutePath()+ " does not contain a valid pid (nothing but a number) or is not readable",e);
            }
        }
        cLogger.debug("Found {} pidfiles with valid pids!",res.size());
        return res;
    }

    private static int getPidFromFile(File file) throws Exception {
        return Integer.parseInt( FileUtils.readFileToString(file) );
    }

    private static Object getAppName(File file) {
        return StringUtils.removeEnd(file.getName(),".pid");
    }

    private static List<String> getPidDirs(PersistentConfiguration csConfig) {
        List<Object> javaSucks = csConfig.getList("pp.pid.dirs");
        List<String> dirs = new ArrayList<String>();
        for (Object o : javaSucks) {
            dirs.add((String)o);
        }
        return dirs;
    }

    public void stop() {
        iRunning .set(false);
    }

    List<File> getPidFiles() {
        return getPidFiles(iPidDirs);
    }

    Map<String, List<Long>> getRestartedStats() {
        return appRestartedMap;
    }
}
