package edu.mayo.dhs.ievaluate.core.plugins;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import edu.mayo.dhs.ievaluate.api.IEvaluate;
import edu.mayo.dhs.ievaluate.api.plugins.IEvaluatePlugin;
import edu.mayo.dhs.ievaluate.api.plugins.PluginDescriptor;
import edu.mayo.dhs.ievaluate.api.plugins.PluginManager;
import edu.mayo.dhs.ievaluate.core.plugins.classloader.PluginClassLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

/**
 * Heavily inspired by PL4J's implementation of plugin management, but modified to suit our use cases
 */
public class PluginManagerCore implements PluginManager {
    private Map<String, IEvaluatePlugin> registeredPlugins;
    private Map<String, PluginClassLoader> pluginClassLoaders;
    private Map<String, PluginDescriptor> descriptors;

    public void loadPlugins(File pluginJarDir, File pluginConfigDir) {
        loadDescriptorsAndClassloaders(pluginJarDir);
        registerPlugins(pluginConfigDir);
    }

    private void loadDescriptorsAndClassloaders(File pluginsDir) {
        this.pluginClassLoaders = new HashMap<>();
        this.descriptors = new HashMap<>();
        ObjectMapper om = new ObjectMapper();
        for (File f : Objects.requireNonNull(pluginsDir.listFiles())) {
            if (f.getName().endsWith(".jar")) { // Ignore non-jars
                try (JarFile jar = new JarFile(f)) {
                    ZipEntry entry = jar.getEntry("plugin.json");
                    if (entry == null) {
                        IEvaluate.getLogger().warn("Skipping plugin JAR with no plugin.json: " + f.getName());
                        continue;
                    }
                    PluginDescriptor descriptor = om.readValue(jar.getInputStream(entry), PluginDescriptor.class);
                    IEvaluate.getLogger().info("Loading plugin " + descriptor.getName());
                    PluginClassLoader classLoader = new PluginClassLoader(this, descriptor, f, getClass().getClassLoader());
                    pluginClassLoaders.put(descriptor.getName(), classLoader);
                    descriptors.put(descriptor.getName(), descriptor);
                    IEvaluate.getLogger().info("Successfully loaded plugin " + descriptor.getName());
                } catch (IOException e) {
                    IEvaluate.getLogger().warn("Failed to load plugin " + f.getName(), e);
                }
            }
        }
    }

    private void registerPlugins(File confDir) {
        this.registeredPlugins = new HashMap<>();
        ObjectMapper om = new ObjectMapper();
        descriptors.forEach((name, descriptor) -> {
            // Copy and load configuration
            File pluginConfDir = new File(confDir, name);
            if (!pluginConfDir.isDirectory() || (!pluginConfDir.exists() && !pluginConfDir.mkdirs())) {
                IEvaluate.getLogger().fatal("Could not create plugin configuration directory for " + name + "!");
                System.exit(-1); // Fatal error
            }
            JsonNode currConfig;
            File conf = new File(pluginConfDir, "config.json");
            if (!conf.exists()) {
                try (InputStream is = pluginClassLoaders.get(name).getResourceAsStream("/config.json")) {
                    currConfig = om.readTree(is);
                    om.writerWithDefaultPrettyPrinter().writeValue(conf, currConfig);
                } catch (IOException e) {
                    IEvaluate.getLogger().error("Could not copy plugin configuration file for " + name, e);
                }
            } else {
                try (InputStream is = pluginClassLoaders.get(name).getResourceAsStream("/config.json")) {
                    JsonNode defaultConfig = om.readTree(is);
                    currConfig = om.readTree(conf);
                    currConfig = mergeDefaultconfigIntoCurrent(defaultConfig, currConfig, name);
                    om.writerWithDefaultPrettyPrinter().writeValue(conf, currConfig);
                } catch (IOException e) {
                    IEvaluate.getLogger().error("Could not copy plugin configuration file for " + name, e);
                }
            }
            // Now try to load the plugin
            String mainClass = descriptor.getMainClass();
            try {
                Class<?> pluginClazz = Class.forName(mainClass, true, pluginClassLoaders.get(name));
                Constructor<?> ctor = pluginClazz.getConstructor();
                registeredPlugins.put(name, (IEvaluatePlugin) ctor.newInstance());
                registeredPlugins.get(name).loadConfig(pluginConfDir);
            } catch (Throwable t) {
                IEvaluate.getLogger().fatal("Could not load plugin " + name, t);
                System.exit(-1);
            }
        });
    }

    public void initializePlugins() {
        Set<String> visiting = new HashSet<>();
        Set<String> initialized = new HashSet<>();
        registeredPlugins.forEach((name, plugin) -> initializePluginRecursive(visiting, initialized, name, plugin));
    }

    private void initializePluginRecursive(Set<String> visiting, Set<String> initialized, String name, IEvaluatePlugin plugin) {
        // Check and make sure this isn't already initialized successfully. If it is, we do not have to do anything further
        if (initialized.contains(name)) {
            return;
        }
        // Use visiting to prevent circular loading
        if (visiting.contains(name)) {
            IEvaluate.getLogger().fatal("Circular dependencies in plugin, dependency graph for " + name + " resulted in a cyclic load");
            System.exit(-1); // Fatal exit
        }
        visiting.add(name);
        for (String s : descriptors.get(name).getRequired()) {
            initializePluginRecursive(visiting, initialized, s, registeredPlugins.get(s));
        }
        visiting.remove(name); // We are no longer recursing, thus no longer at risk of circular dependencies
        IEvaluate.getLogger().info("Initializing plugin " + name);
        try {
            plugin.onInit();
            IEvaluate.getLogger().info("Successfully initialized plugin " + name);
        } catch (Throwable t) {
            IEvaluate.getLogger().fatal("Failure initializing " + name, t);
            System.exit(-1); // Fatal fail to prevent security leak (from, e.g., an authentication plugin)
        }
        initialized.add(name);
    }

    public void enablePlugins() {
        Set<String> visiting = new HashSet<>();
        Set<String> initialized = new HashSet<>();
        registeredPlugins.forEach((name, plugin) -> enablePluginRecursive(visiting, initialized, name, plugin));
    }

    private void enablePluginRecursive(Set<String> visiting, Set<String> enabled, String name, IEvaluatePlugin plugin) {
        // Check and make sure this isn't already enabled successfully. If it is, we do not have to do anything further
        if (enabled.contains(name)) {
            return;
        }
        // Use visiting to prevent circular loading
        if (visiting.contains(name)) {
            IEvaluate.getLogger().fatal("Circular dependencies in plugin, dependency graph for " + name + " resulted in a cyclic load");
            System.exit(-1); // Fatal exit
        }
        visiting.add(name);
        for (String s : descriptors.get(name).getRequired()) {
            enablePluginRecursive(visiting, enabled, s, registeredPlugins.get(s));
        }
        visiting.remove(name); // We are no longer recursing, thus no longer at risk of circular dependencies
        IEvaluate.getLogger().info("Enabling plugin " + name);
        try {
            plugin.onEnable();
            IEvaluate.getLogger().info("Successfully enabled plugin " + name);
        } catch (Throwable t) {
            IEvaluate.getLogger().fatal("Failure enabling " + name, t);
            System.exit(-1); // Fatal fail to prevent security leak (from, e.g., an authentication plugin)
        }
        enabled.add(name);
    }

    private JsonNode mergeDefaultconfigIntoCurrent(JsonNode defaultConfig, JsonNode currConfig, String currPlugin) {
        if (!(defaultConfig instanceof ObjectNode)) {
            return currConfig; // This is a value, not a mapping, so we just return the default value
        }
        Map<String, JsonNode> toSet = new HashMap<>();
        defaultConfig.fields().forEachRemaining(e -> {
            if (currConfig.has(e.getKey())) {
                JsonNode result = mergeDefaultconfigIntoCurrent(e.getValue(), currConfig.get(e.getKey()), currPlugin);
                toSet.put(e.getKey(), result);
            } else {
                toSet.put(e.getKey(), e.getValue());
            }
        });
        if (currConfig instanceof ObjectNode) {
            toSet.forEach(((ObjectNode) currConfig)::set);
        } else {
            if (toSet.size() > 0) {
                IEvaluate.getLogger().warn("[" + currPlugin + "] Default config has an object definition, whereas configuration value has a " + currConfig.getClass().getSimpleName());
            }
        }
        return currConfig;
    }

    @Override
    public <T extends IEvaluatePlugin> T getPlugin(String name, Class<T> clazz) {
        IEvaluatePlugin plugin = registeredPlugins.get(name);
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

    public Map<String, PluginClassLoader> getPluginClassLoaders() {
        return pluginClassLoaders;
    }

    public Map<String, PluginDescriptor> getPluginDescriptors() {
        return descriptors;
    }
}
