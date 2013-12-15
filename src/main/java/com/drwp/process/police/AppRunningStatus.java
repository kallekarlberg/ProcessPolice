package com.drwp.process.police;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class AppRunningStatus {
	private String status;
	private String appName;
	private String version;
	private int pid;

	public AppRunningStatus() {} //for cxf
	public AppRunningStatus(String name, String ver, int aPid) {
		appName = name;
		version = ver;
		pid = aPid;
	}
	public String getStatus() {
		return status;
	}
	public void setStatus(String status) {
		this.status = status;
	}
	public String getAppName() {
		return appName;
	}
	public void setAppName(String appName) {
		this.appName = appName;
	}
	public String getVersion() {
		return version;
	}
	public void setVersion(String version) {
		this.version = version;
	}
}
