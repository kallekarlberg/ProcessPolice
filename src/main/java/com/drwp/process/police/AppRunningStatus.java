package com.drwp.process.police;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class AppRunningStatus {
	private String status;
	private String appName;
	private String version;
	private int pid;
	private String linkVersion;

	public AppRunningStatus() {} //for cxf
	public AppRunningStatus(String name, String ver, String lVer, int aPid) {
		appName = name;
		version = ver;
		linkVersion = lVer;
		pid = aPid;
	}
	public int getPid() {
		return pid;
	}
	public void setPid(int pid) {
		this.pid = pid;
	}
	public String getLinkVersion() {
		return linkVersion;
	}
	public void setLinkVersion(String linkVersion) {
		this.linkVersion = linkVersion;
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
