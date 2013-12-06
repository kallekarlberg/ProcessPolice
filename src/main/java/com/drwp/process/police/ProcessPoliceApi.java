package com.drwp.process.police;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/applications/processes/")
public class ProcessPoliceApi {

    private static final Logger cLogger = LoggerFactory.getLogger(ProcessPoliceApi.class);
    private final ProcessWatcher iWatcher;
    private static final SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");


    public ProcessPoliceApi(ProcessWatcher watcher) {
        iWatcher = watcher;
    }

    @GET
    @Path("running")
    public RunningApplications getRunningApplications(@QueryParam(value="showStats")boolean showStats) {
        List<File> pidFiles = iWatcher.getPidFiles();
        RunningApplications res = new RunningApplications();
        for (File file : pidFiles) {
            RunningApplication ra = new RunningApplication();
            String appName = StringUtils.removeEnd(file.getName(),".pid");
            ra.setName(appName);
            ra.setPidFile(file.getAbsolutePath());
            if ( showStats ) {
                addStats(ra,iWatcher.getRestartedStats(),appName);
            }
            res.getRunningApplications().add(ra);
        }
        return res;
    }

    private static void addStats(RunningApplication ra, Map<String, List<Long>> restartedStats, Object appName) {
        List<Long> restartedTs = restartedStats.get(appName);
        if (restartedTs!=null ) {
            ra.setNbrRestarts(restartedTs.size());
            for (Long ts : restartedTs) {
                ra.getRestartedAt().add(sdf.format(new Date(ts)));
            }
        }
    }
}
