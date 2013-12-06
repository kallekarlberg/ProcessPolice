package com.drwp.process.police;

import org.apache.cxf.aegis.databinding.AegisDatabinding;
import org.apache.cxf.endpoint.Server;
import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.apache.cxf.jaxrs.provider.json.JSONProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import com.netgiro.config.PersistentConfiguration;
import com.netgiro.config.util.ConfigClientUtils;
import com.netgiro.config.watch.ConfigObserver;
import com.netgiro.utils.log.MdcUtil;

/**
 * Just a dummy main class to that Skeleton supports deployment.
 */
public class ProcessPoliceMain {

    private static Logger cLogger;

    public static final int APP_TYPE_ID = 202;
    public static final String APP_NAME = "ProcessPolice";

    public static void main(String[] args) throws Exception {
        SLF4JBridgeHandler.install();
        PersistentConfiguration csConfig = getCsConfig();
        cLogger = initLogger(csConfig);
        ProcessWatcher watcher = new ProcessWatcher(csConfig);
        Thread t = startProcessWatch(watcher);
        Server s = startRestServices(watcher, csConfig.getInt("pp.rest.port"));
        addGracefulShutdown(s,t,watcher);
    }

    private static Thread startProcessWatch(ProcessWatcher watcher) {
        Thread t = new Thread(watcher);
        t.start();
        return t;
    }

    public static Server startRestServices( ProcessWatcher watcher, int servPort ) {
        JAXRSServerFactoryBean sf = new JAXRSServerFactoryBean();
        sf.setResourceClasses(ProcessPoliceApi.class);
        sf.setResourceProvider(ProcessPoliceApi.class, new SingletonResourceProvider(new ProcessPoliceApi(watcher)));
        sf.setAddress("http://0.0.0.0:"+servPort+"/");
        sf.setProvider( new JSONProvider<RunningApplications>() );
        sf.getServiceFactory().setDataBinding(new AegisDatabinding());
        return sf.create();
    }

    private static void addGracefulShutdown(final Server s, final Thread t, final ProcessWatcher watcher) {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {

            @Override
            public void run() {
                watcher.stop();
                try {
                    t.join();
                } catch (InterruptedException e) {
                    cLogger.warn("interrupted",e);
                }
                s.stop();
                s.destroy();
            }
        }));
    }

    private static Logger initLogger(PersistentConfiguration csConfig) {
        new ConfigObserver(csConfig, 120000).start();
        MdcUtil.setLogTraceId("main");
        MdcUtil.setLogAppName(APP_NAME, csConfig.getInt("app.id"));
        return LoggerFactory.getLogger(ProcessPoliceMain.class);
    }

    private static PersistentConfiguration getCsConfig() throws Exception {
        return ConfigClientUtils.createDefaultConfiguration(APP_NAME, APP_TYPE_ID, "config.definition.xml");
    }
}
