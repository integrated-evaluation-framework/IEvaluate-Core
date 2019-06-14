package edu.mayo.dhs.ievaluate.core;

import edu.mayo.dhs.ievaluate.api.IEvaluate;
import edu.mayo.dhs.ievaluate.api.models.applications.ProfiledApplication;
import edu.mayo.dhs.ievaluate.api.plugins.IEvaluatePlugin;
import edu.mayo.dhs.ievaluate.api.util.StorageProvider;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public class IEvaluateCore implements IEvaluate {

    public IEvaluateCore(File workingDir) {
        init();
    }

    private void init() {

    }

    @Override
    public <T extends IEvaluatePlugin> T getPlugin(String name, Class<T> clazz) {
        return null;
    }

    @Override
    public Map<String, IEvaluatePlugin> getRegisteredPlugins() {
        return null;
    }

    @Override
    public Collection<ProfiledApplication> getRegisteredApplications() {
        return null;
    }

    @Override
    public ProfiledApplication getApplication(UUID uid) {
        return null;
    }

    @Override
    public void registerApplication(ProfiledApplication application) {

    }

    @Override
    public StorageProvider getStorage() {
        return null;
    }

    public static void main(String... args) {

    }
}
