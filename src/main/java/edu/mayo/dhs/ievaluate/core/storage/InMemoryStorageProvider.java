package edu.mayo.dhs.ievaluate.core.storage;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.mayo.dhs.ievaluate.api.IEvaluate;
import edu.mayo.dhs.ievaluate.api.applications.ApplicationProvider;
import edu.mayo.dhs.ievaluate.api.applications.ProfiledApplication;
import edu.mayo.dhs.ievaluate.api.storage.StorageProvider;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * A default fallback storage provider that writes to JSON in the supplied working directory.
 * Its usage is not recommended for production use cases
 */
public class InMemoryStorageProvider implements StorageProvider {

    private Map<String, JsonNode> registeredApplications;
    @JsonIgnore
    private File saveBase;

    public InMemoryStorageProvider(File workingDirectory) throws IOException {
        this.registeredApplications = new HashMap<>();
        this.saveBase = new File(workingDirectory, "IEvaluateMemStore.json");
        if (saveBase.exists()) {
            InMemoryStorageProvider inMem = new ObjectMapper().readValue(saveBase, InMemoryStorageProvider.class);
            registeredApplications = inMem.registeredApplications;
        }
    }

    @Override
    public Map<String, JsonNode> loadRegisteredApplications() {
        return registeredApplications; // In-memory just returns what is ... in-memory
    }

    @Override
    public void saveRegisteredApplications() {
        registeredApplications.clear(); // Clear saved applications
        for (ProfiledApplication app : IEvaluate.getApplicationManager().getRegisteredApplications()) {
            try {
                ApplicationProvider<?> pertinentProvider = IEvaluate
                        .getApplicationManager()
                        .getApplicationProviders()
                        .get(app.getClass().getName());
                if (pertinentProvider == null) {
                    throw new IllegalArgumentException(
                            "Application is of type "
                            + app.getClass().getName()
                            + " but no suitable application provider was found"
                    );
                }
                registeredApplications.put(app.getClass().getName(), pertinentProvider.marshal(app));
            } catch (Throwable t) {
                IEvaluate.getLogger().error("Failure saving " + app.getName() + " with ID " + app.getId(), t);
                IEvaluate.getLogger().error("Data may be lost");
            }
        }
        saveInternal();
    }

    private synchronized void saveInternal() {
        try {
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(saveBase, this);
        } catch (Throwable t) {
            IEvaluate.getLogger().error("Failure writing to disk", t);
        }
    }
}
