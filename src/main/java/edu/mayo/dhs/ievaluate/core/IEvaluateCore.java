package edu.mayo.dhs.ievaluate.core;

import edu.mayo.dhs.ievaluate.api.IEvaluate;
import edu.mayo.dhs.ievaluate.api.IEvaluateServer;
import edu.mayo.dhs.ievaluate.api.applications.ApplicationManager;
import edu.mayo.dhs.ievaluate.api.plugins.PluginManager;
import edu.mayo.dhs.ievaluate.api.storage.StorageProvider;
import edu.mayo.dhs.ievaluate.core.applications.ApplicationManagerCore;
import edu.mayo.dhs.ievaluate.core.plugins.PluginManagerCore;
import edu.mayo.dhs.ievaluate.core.storage.InMemoryStorageProvider;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;

public class IEvaluateCore implements IEvaluateServer {

    private InitState state;
    private Logger logger;
    private File workingDir;

    private StorageProvider storage;

    private ApplicationManagerCore applicationManager;
    private PluginManagerCore pluginManager;


    public IEvaluateCore(File workingDir) {
        this.state = InitState.PRE_INIT;
        this.workingDir = workingDir;
        preInit();
        this.state = InitState.INIT;
        this.pluginManager.initializePlugins();
        this.state = InitState.PRE_ENABLE;
        preEnable();
        this.state = InitState.ENABLING;
        this.pluginManager.enablePlugins();
        this.state = InitState.POST_ENABLE;
        postEnable();
        this.state = InitState.COMPLETE;
    }

    private void preInit() {
        // Register with API entry point for all downstream items
        IEvaluate.setServer(this);
        // Instantiate logging system
        this.logger = LogManager.getLogger("Integrated Evaluation Framework");
        // Verify the working directory for this application
        if (!this.workingDir.exists() || !this.workingDir.isDirectory()) {
            IEvaluate.getLogger().fatal("The supplied working directory " + workingDir + " does not exist or is not a directory!");
            System.exit(-1); // Fatal exit
        }
        // Initialize the plugin and application manager
        this.pluginManager = new PluginManagerCore();
        this.applicationManager = new ApplicationManagerCore();
        // Load all plugins
        File pluginDir = new File(workingDir, "plugins");
        if (!pluginDir.isDirectory() || (!pluginDir.exists() && !pluginDir.mkdirs())) {
            IEvaluate.getLogger().fatal("Could not create plugin directory!");
            System.exit(-1); // Fatal error
        }
        File jarDir = new File(pluginDir, "jars");
        if (!jarDir.isDirectory() || (!jarDir.exists() && !jarDir.mkdirs())) {
            IEvaluate.getLogger().fatal("Could not create plugin JAR directory!");
            System.exit(-1); // Fatal error
        }
        File confDir = new File(pluginDir, "config");
        if (!confDir.isDirectory() || (!confDir.exists() && !confDir.mkdirs())) {
            IEvaluate.getLogger().fatal("Could not create plugin configuration directory!");
            System.exit(-1); // Fatal error
        }
        this.pluginManager.loadPlugins(jarDir, confDir);
    }

    // Mostly just verifies items are in correct state (loading defaults if not)
    // and prevents late-binding by activating protective locks
    private void preEnable() {
        if (this.storage == null) {
            IEvaluate.getLogger().warn("No Storage Provider Supplied in onInit(), defaulting to JSON/in memory storage");
            try {
                IEvaluate.getServer().registerStorageProvider(new InMemoryStorageProvider(workingDir));
            } catch (IOException e) {
                IEvaluate.getLogger().fatal("Could not instantiate in memory storage fallback, " +
                        "system will exit as no storage providers present", e);
                System.exit(-1);
            }
        }

    }

    private void postEnable() {
        this.applicationManager.loadApplicationsFromStorage();
    }

    @Override
    public ApplicationManager getApplicationManager() {
        return this.applicationManager;
    }

    @Override
    public PluginManager getPluginManager() {
        return this.pluginManager;
    }

    @Override
    public Logger getLogger() {
        return this.logger;
    }

    @Override
    public StorageProvider getStorage() {
        return this.storage;
    }

    @Override
    public void registerStorageProvider(StorageProvider provider) {
        if (this.storage == null) {
            this.storage = provider;
            this.logger.info("Registered Storage Provider: " + provider.getClass().getName());
        } else {
            String errMsg = "Attempted to re-register a storage provider after one is already registered!";
            this.logger.error(errMsg);
            throw new IllegalStateException(errMsg);
        }
    }

    @Override
    public InitState getInitializationState() {
        return this.state;
    }

    public static void main(String... args) {

    }
}
