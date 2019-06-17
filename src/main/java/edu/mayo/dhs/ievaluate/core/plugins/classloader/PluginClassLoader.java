package edu.mayo.dhs.ievaluate.core.plugins.classloader;

import edu.mayo.dhs.ievaluate.api.plugins.PluginDescriptor;
import edu.mayo.dhs.ievaluate.core.plugins.PluginManagerCore;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Special class-loader for plugins that prioritizes children first so as to reduce dependency overwrite issues
 * One class-loader per plugin.
 */
public class PluginClassLoader extends URLClassLoader {
    private PluginManagerCore pluginManager;
    private PluginDescriptor descriptor;

    public PluginClassLoader(PluginManagerCore pluginManager, File pluginFile, URL[] urls, ClassLoader parent) {
        super(urls, parent);
        this.pluginManager = pluginManager;

    }
}
