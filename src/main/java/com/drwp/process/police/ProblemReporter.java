package com.drwp.process.police;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;

import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProblemReporter {

	private static final Logger cLogger = LoggerFactory.getLogger(ProblemReporter.class);
	private static final String DELIM_LINE = "******************************\n";

	void reportProblem(String string, Exception e) {
		cLogger.error(string,e);
	}

	void reportDeath(int pid, String name) {
		cLogger.error("OH MY GOD, HE'S DEAD!");
		cLogger.error("Pid {} found in file {} is DEAD",pid,(name+".pid"));
		String stdOutLog = tailStdOutLog(name);

		report(name,pid,stdOutLog);
	}

	private static void report(String name, int pid, String string) {
		StringBuffer sb = new StringBuffer();
		sb.append("\n"+DELIM_LINE);
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		sb.append(sdf.format(System.currentTimeMillis())).append("\n");
		sb.append("Application '"+name+"' with pid '"+pid+"' has stopped for some reason...").append("\n");
		sb.append(DELIM_LINE);
		sb.append("\nExtracted from /var/opt/netgiro/"+name+"stdout.log\n");
		sb.append(DELIM_LINE);
		sb.append(string).append("\n").append(DELIM_LINE);
		cLogger.error(sb.toString());
		mail(sb.toString());
	}

	private static void mail(String string) {
		// TODO Auto-generated method stub

	}

	//dirty hack assuming 'tail' exist and a properly named std-outfile
	private static String tailStdOutLog(String name) {
		try {
			File log = new File("/var/opt/netgiro/"+name+".stdout.log");
			if (log.exists()) {
				String [] grepCmd = new String[]{"tail", "-200", log.getAbsolutePath() };
				cLogger.info("Trying to grep using: {}",StringUtils.join(grepCmd, " "));
				Process p = Runtime.getRuntime().exec(grepCmd);

				BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
				StringBuffer sb = new StringBuffer();
				String line;
				while ((line = input.readLine()) != null) {
					sb.append(line).append("\n");
				}
				return sb.toString();
			}
			return "Logfile "+log.getAbsolutePath() +" cant be found";

		} catch (Exception e){
			cLogger.error("Unable to tail log",e);
			return "Unable to tail log. Dammit! "+e.getMessage();
		}
	}
}
