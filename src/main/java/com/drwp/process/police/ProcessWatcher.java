package com.drwp.process.police;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessWatcher implements Runnable {

	enum ProcessStatus {
		OK, SICK, DEAD, UNKNOWN
	}

	private static final Logger cLogger = LoggerFactory.getLogger(ProcessWatcher.class);
	private final List<String> iPidDirs;
	private final List<String> iDeployDirs;
	private final ProblemReporter iProblemReporter = new ProblemReporter();
	private final int dontRestartMoreOftenThan;
	//This is actually a memory leak... but a very slow one
	private final Map<String,List<Long>> appRestartedMap= new HashMap<String, List<Long>>();
	private final List<String> iBrokenApplications = new ArrayList<String>();
	private final AtomicBoolean iRunning = new AtomicBoolean(true);
	private final ConanClient iConan;

	public ProcessWatcher(Configuration csConfig) {
		iPidDirs = getDirs(csConfig.getList("procpol.pid.dirs"));
		iDeployDirs = getDirs(csConfig.getList("procpol.app.deploy.dirs"));
		iConan = createConanClient(csConfig.getString("procpol.conan.url"));
		//FIXME bad conf param name
		dontRestartMoreOftenThan = csConfig.getInt("procpol.dead.if.restarts.after.secs");
	}

	//FIXME
	private ConanClient createConanClient(String string) {
		return new ConanClient();
	}

	public void run() {
		while ( iRunning.get() ) {
			runImpl();
			safeSleep(10000L);
		}
	}

	void runImpl() {
		List<Object[]> pidsAndNames = getPidsAndNames(iPidDirs);
		checkPids(pidsAndNames);
	}

	private void checkPids(List<Object[]> pidsAndNames) {
		for (Object[] pn : pidsAndNames) {
			ProcessStatus status = checkPid(pn);
			handlePidStatus(status,pn);
		}
		cLogger.debug("Done checking {} pids",pidsAndNames.size());
	}

	private void handlePidStatus(ProcessStatus status, Object[] pn) {
		String appVersion = getAppVersion((String) pn[1],iDeployDirs);
		AppRunningStatus stat = new AppRunningStatus((String)pn[1],appVersion,(Integer)pn[0]);
		switch (status) {
		case DEAD:
			stat.setStatus(ProcessStatus.DEAD.name());
			break;
		case OK:
			stat.setStatus(ProcessStatus.OK.name());
			break;
		case SICK:
			stat.setStatus(ProcessStatus.SICK.name());
			break;
		case UNKNOWN:
			stat.setStatus(ProcessStatus.UNKNOWN.name());
			break;
		}
		iConan.reportRunningStatus(stat);
	}

	private static String getAppVersion(String appName, List<String> deployDirs) {

		try {
			String deployDir = findDeployDir(appName,deployDirs);
			Path link = FileSystems.getDefault().getPath(deployDir, appName);
			Path target = Files.readSymbolicLink(link);
			String fName = target.toFile().getName();
			int index = StringUtils.lastIndexOf(fName, "-");
			if ( index != -1 )
				return fName.substring(index+1);
		} catch (IOException e) {
			cLogger.warn("Problems determining app version for app "+appName+" maybe it is not linked properly",e);
		}
		return "UNKNOWN";
	}

	private static String findDeployDir(String appName, List<String> deployDirs) throws IOException {
		for (String d : deployDirs) {
			if ( new File(d+appName).exists() )
				return d;
			if ( new File(d+"/"+appName).exists() )
				return d+"/";
		}
		throw new IOException("Unable to find link "+appName+" in any of configured deploy dirs");
	}

	private ProcessStatus checkPid(Object[] pn) {
		String name = (String)pn[1];
		if ( applicationBroken(name,iBrokenApplications) ) {
			cLogger.warn("Application {} marked as 'BROKEN' skipping check",name);
			return ProcessStatus.DEAD;
		}
		int pid = (Integer)pn[0];
		cLogger.debug("checking pid '{}'",pid);
		try {
			if (isItDead(pid)) {
				iProblemReporter.reportDeath(pid,name);
				return maybeRestart(name, dontRestartMoreOftenThan);
			}
			cLogger.debug("Pid {} ok",pid);
			return ProcessStatus.OK;
		} catch (IOException e) {
			cLogger.warn("Unable to check pid {}",pid,e);
			return ProcessStatus.UNKNOWN;
		}
	}

	private static boolean applicationBroken(String name, List<String> blockedApplications) {
		for (String blocked : blockedApplications) {
			if ( blocked.equals(name) ) {
				return true;
			}
		}
		return false;
	}

	private ProcessStatus maybeRestart(String name, int dontRestartMoreOften ) {
		List<Long> restartedTs  = appRestartedMap.get(name);
		removePidFile(name+".pid", iPidDirs);

		//never restarted
		if ( restartedTs == null ) {
			restart(name);
			List<Long> l = new ArrayList<Long>();
			l.add( System.currentTimeMillis() );
			appRestartedMap.put(name, l);
			return ProcessStatus.SICK;
		}
		// restarted "long ago" = ok to restart
		if ( restartedTs.get(restartedTs.size()-1) < (System.currentTimeMillis()-dontRestartMoreOften*1000L) ) {
			restart(name);
			restartedTs.add(System.currentTimeMillis());
			return ProcessStatus.SICK;
		}
		cLogger.error("Application {} seems to be an 'early crasher' giving up on it.");
		iBrokenApplications.add(name);
		return ProcessStatus.DEAD;
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
				iProblemReporter.reportProblem("File "+file.getAbsolutePath()+ " does not contain a valid pid (aka 'just a number') or is not readable",e);
			}
		}
		cLogger.debug("Found {} pidfiles with valid pids!",res.size());
		return res;
	}

	private static int getPidFromFile(File file) throws Exception {
		return Integer.parseInt( FileUtils.readFileToString(file).trim() );
	}

	private static Object getAppName(File file) {
		return StringUtils.removeEnd(file.getName(),".pid");
	}

	private static List<String> getDirs(List<Object> javaSucks) {
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

	private static void safeSleep(long l) {
		try {
			Thread.sleep(l);
		} catch (InterruptedException e) {
			cLogger.warn("interrupted",e);
		}
	}
}
