package com.drwp.process.police;

import java.util.ArrayList;
import java.util.List;

import javax.xml.bind.annotation.XmlRootElement;

@XmlRootElement
public class RunningApplications {

    private List<RunningApplication> runningApplications = new ArrayList<RunningApplication>();

    public List<RunningApplication> getRunningApplications() {
        return runningApplications;
    }

    public void setRunningApplications(List<RunningApplication> runningApplications) {
        this.runningApplications = runningApplications;
    }
}
