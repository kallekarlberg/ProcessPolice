package com.drwp.process.police;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.cxf.aegis.databinding.AegisDatabinding;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.jaxrs.provider.json.JSONProvider;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.bridge.SLF4JBridgeHandler;

public class ProcessPoliceMain {

	private static Logger cLogger;

	public static final int APP_TYPE_ID = 202;
	public static final String APP_NAME = "ProcessPolice";

	public static void main(String[] args) throws Exception {
		SLF4JBridgeHandler.install();
		Configuration csConfig = getConfig();
		cLogger = initLogger();
		int port = csConfig.getInt("procpol.rest.port");
		Server s2 = startDummyConan(port+1);
		ConanIfc conanClient = createConanClient(port+1);
		ProcessWatcher watcher = new ProcessWatcher(csConfig);
		cLogger.info("Starting process watchdog");
		Thread t = startProcessWatch(watcher);
		cLogger.info("Starting rest service on {}",port);
		Server s = startRestServices(watcher, port);
		addGracefulShutdown(s,t,watcher);
	}

	private static ConanIfc createConanClient(int i) {
		return JAXRSClientFactory.create("http://localhost:"+i, ConanIfc.class);
	}

	@Deprecated
	private static Server startDummyConan(int servPort) {
		JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
		sf.setResourceClasses(ConanIfc.class);
		sf.setResourceProvider(ConanIfc.class, new SingletonResourceProvider(new ConanClient()));
		sf.setAddress("http://0.0.0.0:"+servPort+"/");
		return sf.create();
	}

	private static Thread startProcessWatch(ProcessWatcher watcher) {
		Thread t = new Thread(watcher);
		t.setDaemon(true);
		t.setName("ProcessWatchdog");
		t.start();
		return t;
	}

	public static Server startRestServices( ProcessWatcher watcher, int servPort ) {
		JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
		sf.setResourceClasses(ProcessPoliceApi.class);
		sf.setResourceProvider(ProcessPoliceApi.class, new SingletonResourceProvider(new ProcessPoliceApi(watcher)));
		sf.setAddress("http://0.0.0.0:"+servPort+"/");
		sf.setProvider( new JSONProvider<RunningApplications>());
		//sf.setProvider( new JSONProvider<RunningApplications>() );
		sf.getServiceFactory().setDataBinding(new AegisDatabinding());
		return sf.create();
	}

	private static void addGracefulShutdown(final Server s, final Thread t, final ProcessWatcher watcher) {
		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

			@Override
			public void run() {
				cLogger.info("Shutting down...");
				System.err.println("Shutting down");
				watcher.stop();
				try {
					t.join();
				} catch (InterruptedException e) {
					cLogger.warn("interrupted",e);
				}
				s.stop();
				s.destroy();
				cLogger.info("Goodbye");
				System.err.println("Goodbye");
			}
		}));
	}

	private static Logger initLogger() {
		PropertyConfigurator.configureAndWatch("/log4j.properties");
		MDC.put("logTraceId","main");
		MDC.put("logAppName",APP_NAME);
		return LoggerFactory.getLogger(ProcessPoliceMain.class);
	}

	private static Configuration getConfig() throws Exception {
		return new PropertiesConfiguration("application.properties");
	}
}
