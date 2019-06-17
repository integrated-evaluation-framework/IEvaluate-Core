package edu.mayo.dhs.ievaluate.core.plugins;

import edu.mayo.dhs.ievaluate.api.IEvaluate;
import edu.mayo.dhs.ievaluate.api.plugins.IEvaluatePlugin;
import edu.mayo.dhs.ievaluate.api.plugins.PluginDescriptor;
import edu.mayo.dhs.ievaluate.api.plugins.PluginManager;
import edu.mayo.dhs.ievaluate.core.plugins.classloader.PluginClassLoader;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Heavily inspired by PL4J's implementation of plugin management, but modified to suit our use cases
 */
public class PluginManagerCore implements PluginManager {
    private Map<String, IEvaluatePlugin> registeredPlugins;
    private Map<String, PluginClassLoader> pluginClassLoaders;


    public void loadPlugins(File pluginJarDir, File pluginConfigDir) {
        Map<PluginDescriptor, File> descriptors = loadDescriptorsForFiles(pluginJarDir);
        createClassLoaders(pluginJarDir);
    }

    private Map<PluginDescriptor, File> loadDescriptorsForFiles(File pluginJarDir) {
        for (File f : Objects.requireNonNull(pluginJarDir.listFiles())) {

        }
        return null;
    }

    private void createClassLoaders(File pluginsDir) {
        this.pluginClassLoaders = new HashMap<>();
        // TODO

    }


    public void initializePlugins() {
        registeredPlugins.forEach((name, plugin) -> {
            IEvaluate.getLogger().info("Initializing plugin " + name);
            try {
                plugin.onInit();
                IEvaluate.getLogger().info("Successfully initialized plugin " + name);
            } catch (Throwable t) {
                IEvaluate.getLogger().fatal("Failure initializing " + name, t);
                System.exit(-1); // Fatal fail to prevent security leak (from, e.g., an authentication plugin)
            }
        });
    }

    public void enablePlugins() {
        registeredPlugins.forEach((name, plugin) -> {
            IEvaluate.getLogger().info("Enabling plugin " + name);
            try {
                plugin.onEnable();
                IEvaluate.getLogger().info("Successfully enabled plugin " + name);
            } catch (Throwable t) {
                IEvaluate.getLogger().fatal("Failure enabling " + name, t);
                System.exit(-1); // Fatal fail to prevent security leak (from, e.g., an authentication plugin)
            }
        });
    }

    public PluginManagerCore() {
        this.registeredPlugins = new HashMap<>();
    }

    @Override
    public <T extends IEvaluatePlugin> T getPlugin(String name, Class<T> clazz) {
        IEvaluatePlugin plugin = registeredPlugins.get(name.toUpperCase());
        if (plugin == null) {
            return null;
        }
        if (clazz.isInstance(plugin)) {
            //noinspection unchecked
            return (T) plugin;
        } else {
            IEvaluate.getLogger().warn("Attempted to retrieve a plugin of instance " + plugin.getClass().getName() + " as an incompatible type " + clazz.getName());
            return null;
        }
    }

    @Override
    public Map<String, IEvaluatePlugin> getRegisteredPlugins() {
        return registeredPlugins;
    }
}
