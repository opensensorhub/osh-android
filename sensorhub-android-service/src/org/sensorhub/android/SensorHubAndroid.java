package org.sensorhub.android;

import org.sensorhub.api.ISensorHubConfig;
import org.sensorhub.impl.SensorHub;
import org.sensorhub.impl.comm.NetworkManagerImpl;
import org.sensorhub.impl.database.registry.DefaultDatabaseRegistry;
import org.sensorhub.impl.datastore.mem.InMemorySystemStateDbConfig;
import org.sensorhub.impl.event.EventBus;
import org.sensorhub.impl.module.InMemoryConfigDb;
import org.sensorhub.impl.module.ModuleClassFinder;
import org.sensorhub.impl.module.ModuleConfigJsonFile;
import org.sensorhub.impl.module.ModuleRegistry;
import org.sensorhub.impl.processing.ProcessingManagerImpl;
import org.sensorhub.impl.security.ClientAuth;
import org.sensorhub.impl.security.SecurityManagerImpl;
import org.sensorhub.impl.system.DefaultSystemRegistry;
import org.sensorhub.utils.ModuleUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ForkJoinPool;

public class SensorHubAndroid extends SensorHub {
    private static final Logger log = LoggerFactory.getLogger(SensorHub.class);
    private static final String ERROR_MSG = "Fatal error during sensorhub execution";

    SensorHubAndroid(ISensorHubConfig config){
        super(config);
    }

    public synchronized void start(ModuleRegistry reg){
        if (!started)
        {
            log.info("*****************************************");
            log.info("Starting SensorHub for Android...");

            // Excluded to prevent error caused by a call to ProtectionDomain
            // Class<?> sensorhubClass = SensorHub.class;
            // log.info("Version : {}", ModuleUtils.getModuleInfo(sensorhubClass).getModuleVersion());

            log.info("CPU cores: {}", Runtime.getRuntime().availableProcessors());
            log.info("CommonPool Parallelism: {}", ForkJoinPool.commonPool().getParallelism());

            // init hub core components
            var classFinder = new ModuleClassFinder(osgiContext);
            var configDB = config.getModuleConfigPath() != null ?
                    new ModuleConfigJsonFile(config.getModuleConfigPath(), true, classFinder) :
                    new InMemoryConfigDb(classFinder);
//            this.moduleRegistry = new ModuleRegistry(this, configDB);
            this.moduleRegistry = reg;
            this.eventBus = new EventBus();
            this.databaseRegistry = new DefaultDatabaseRegistry(this);
            this.driverRegistry = new DefaultSystemRegistry(this, new InMemorySystemStateDbConfig());

            // init service managers
            this.securityManager = new SecurityManagerImpl(this);
            this.networkManager = new NetworkManagerImpl(this);
            this.processingManager = new ProcessingManagerImpl(this);

            // prepare client authenticator (e.g. for HTTP connections, etc...)
            ClientAuth.createInstance("keystore");

            // load all modules in the order implied by dependency constraints
            moduleRegistry.loadAllModules();
            started = true;
        }
    }

}
