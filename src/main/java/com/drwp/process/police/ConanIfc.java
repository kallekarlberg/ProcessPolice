package com.drwp.process.police;

import javax.ws.rs.PUT;
import javax.ws.rs.Path;

public interface ConanIfc {

	@PUT
	@Path("/applications/running")
	void reportRunStatus(AppRunningStatus status);
}
