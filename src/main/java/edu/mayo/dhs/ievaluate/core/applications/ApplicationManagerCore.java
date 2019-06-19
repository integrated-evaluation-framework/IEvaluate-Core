package edu.mayo.dhs.ievaluate.core.applications;

import com.fasterxml.jackson.databind.JsonNode;
import edu.mayo.dhs.ievaluate.api.IEvaluate;
import edu.mayo.dhs.ievaluate.api.IEvaluateServer;
import edu.mayo.dhs.ievaluate.api.applications.ApplicationManager;
import edu.mayo.dhs.ievaluate.api.applications.ApplicationProvider;
import edu.mayo.dhs.ievaluate.api.applications.ProfiledApplication;
import edu.mayo.dhs.ievaluate.api.storage.StorageProvider;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ApplicationManagerCore implements ApplicationManager {
    private Map<String, ApplicationProvider<?>> registeredProviders;
    private Map<UUID, ProfiledApplication> applications;

    public ApplicationManagerCore() {
        registeredProviders = new HashMap<>();
        applications = new HashMap<>();
    }

    @Override
    public Collection<? extends ProfiledApplication> getRegisteredApplications() {
        return applications.values();
    }

    @Override
    public ProfiledApplication getApplication(UUID uid) {
        return applications.get(uid);
    }

    @Override
    public void registerApplication(ProfiledApplication application) {
        this.applications.put(application.getId(), application);
        IEvaluate.getStorage().saveRegisteredApplications();
    }

    @Override
    public void registerApplicationProvider(ApplicationProvider<?> provider) {
        if (registeredProviders.containsKey(provider.applicationClass().getName())) {
            IEvaluate.getLogger().warn("Ignoring duplicate application provider registration for type " + provider.applicationClass().getName());
        } else {
            registeredProviders.put(provider.applicationClass().getName(), provider);
        }
    }

    @Override
    public Map<String, ApplicationProvider<?>> getApplicationProviders() {
        return this.registeredProviders;
    }

    public void loadApplicationsFromStorage() {
        if (IEvaluate.getServer().getInitializationState().ordinal() < IEvaluateServer.InitState.ENABLING.ordinal()) {
            // State is before plugin enabling, thus no guarantee there *is* a storage provider that is accessible
            IEvaluate.getLogger().warn("Attempted to load applications from storage prior to onEnable(). " +
                    "This is not possible and ignored");
            return;
        }
        StorageProvider provider = IEvaluate.getStorage();
        Map<String, JsonNode> applicationMappings = provider.loadRegisteredApplications();
        applicationMappings.forEach((clazz, marshaled) -> {
            ApplicationProvider<?> appProvider = registeredProviders.get(clazz);
            if (appProvider == null) {
                IEvaluate.getLogger().warn("No suitable provider found for application of type " + clazz +", skipping");
                return;
            }
            try {
                ProfiledApplication app = appProvider.unmarshal(marshaled);
                applications.put(app.getId(), app); // Don't save apps for every loaded app, wait until end;
            } catch (Throwable t) {
                IEvaluate.getLogger().warn("Failed to successfully deserialize application", t);
            }
        });
        IEvaluate.getStorage().saveRegisteredApplications();
    }
}
