package com.drwp.process.police;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConanClient implements ConanIfc{

	private static final Logger cLogger = LoggerFactory.getLogger(ConanClient.class);

	public void reportRunStatus(AppRunningStatus status) {
		cLogger.info("Application: "+status.getAppName() +" reporting in...");
		cLogger.info("Version: {}",status.getVersion());
		cLogger.info("Link version: {}",status.getLinkVersion());
	}
}
