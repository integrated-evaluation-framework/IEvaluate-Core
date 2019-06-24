package edu.mayo.dhs.ievaluate.core.plugins.classloader;

import edu.mayo.dhs.ievaluate.api.IEvaluate;
import edu.mayo.dhs.ievaluate.api.plugins.PluginDescriptor;
import edu.mayo.dhs.ievaluate.core.plugins.PluginManagerCore;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.List;
import java.util.WeakHashMap;
import java.util.jar.JarFile;

/**
 * Special class-loader for plugins that prioritizes children first so as to reduce dependency overwrite issues
 * One class-loader per plugin.
 */
public class PluginClassLoader extends URLClassLoader {
    private PluginManagerCore pluginManager;
    private PluginDescriptor descriptor;

    public PluginClassLoader(PluginManagerCore pluginManager, PluginDescriptor descriptor, File pluginFile, ClassLoader parent) {
        super(new URL[0], parent); // Start off with empty URL classpath (we will add plugin JAR) then delegate back ot parent
        this.pluginManager = pluginManager;
        this.descriptor = descriptor;
        this.addJar(pluginFile);
    }

    /* Mirrored from URLClassLoader
     * A map (used as a set) to keep track of closeable local resources
     * (either JarFiles or FileInputStreams). We don't care about
     * Http resources since they don't need to be closed.
     *
     * If the resource is coming from a jar file
     * we keep a (weak) reference to the JarFile object which can
     * be closed if URLClassLoader.close() called. Due to jar file
     * caching there will typically be only one JarFile object
     * per underlying jar file.
     *
     * For file resources, which is probably a less common situation
     * we have to keep a weak reference to each stream.
     */

    private final WeakHashMap<Closeable,Void> openFiles = new WeakHashMap<>();

    @Override
    public void addURL(URL url) { // Public access to protected method
        super.addURL(url);
    }

    private void addJar(File jar) {
        try {
            addURL(jar.getCanonicalFile().toURI().toURL()); // Use canonical for consistency
        } catch (IOException e) {
            IEvaluate.getLogger().error(e.getMessage(), e);
        }
    }

    @Override
    public Class<?> loadClass(String className) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(className)) {
            if (className.startsWith("java.")) {
                return findSystemClass(className); // Shortcut for system classes
            }

            // Check if already loaded
            Class<?> loadedClass = findLoadedClass(className);
            if (loadedClass != null) {
                return loadedClass;
            }

            // Not loaded, so first try to find in direct plugin
            try {
                loadedClass = findClass(className);
                return loadedClass;
            } catch (ClassNotFoundException e) {
                // try next step
            }

            // Look in Dependencies
            loadedClass = loadDependencyClass(className);
            if (loadedClass != null) {
                return loadedClass;
            }

            // Not found within plugin (or dependency) classes, return control to standard classloader
            return super.loadClass(className);

        }
    }

    private Class<?> loadDependencyClass(String className) {
        List<String> dependencies = descriptor.getRequired();
        for (String dependency : dependencies) {
            ClassLoader classLoader = pluginManager.getPluginClassLoaders().get(dependency.toLowerCase());

            // If not found, well there is an issue but let's not die here
            if (classLoader == null) {
                IEvaluate.getLogger().error(String.format("%s has dependency on %s but no corresponding classloader was found", descriptor.getName(), dependency));
                continue;
            }

            try {
                return classLoader.loadClass(className);
            } catch (ClassNotFoundException ignored) {
                // Cannot prevent class not found exception, so we catch it here silently and move on to the next
            }
        }

        return null;
    }

    @Override
    public URL getResource(String name) {
        // Check locally
        URL url = findResource(name);
        if (url != null) {
            return url;
        }
        // Don't check dependencies as they will also define (as an example)
        // Fallback to default behaviour
        return super.getResource(name);
    }

    @Override
    public InputStream getResourceAsStream(String name) {
        // Check locally
        URL url = findResource(name);
        if (url != null) {
            try {
                URLConnection urlc = url.openConnection();
                InputStream is = urlc.getInputStream();
                if (urlc instanceof JarURLConnection) {
                    JarURLConnection juc = (JarURLConnection) urlc;
                    JarFile jar = juc.getJarFile();
                    synchronized (openFiles) {
                        if (!openFiles.containsKey(jar)) {
                            openFiles.put(jar, null);
                        }
                    }
                } else if (urlc instanceof sun.net.www.protocol.file.FileURLConnection) {
                    synchronized (openFiles) {
                        openFiles.put(is, null);
                    }
                }
                return is;
            } catch (IOException ignored) {
                // Usually, this would be return null, but we fall back on parent instead
            }
        }
        // Don't check dependencies as they will also define (as an example)
        // Fallback to default behaviour
        return super.getResourceAsStream(name);
    }
}
