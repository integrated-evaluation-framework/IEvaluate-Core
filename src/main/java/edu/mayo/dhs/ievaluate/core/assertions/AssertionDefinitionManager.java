package edu.mayo.dhs.ievaluate.core.assertions;

import edu.mayo.dhs.ievaluate.api.IEvaluate;
import edu.mayo.dhs.ievaluate.api.IEvaluateServer;
import edu.mayo.dhs.ievaluate.api.models.assertions.AssertionDefinition;
import edu.mayo.dhs.ievaluate.api.models.assertions.AssertionInput;
import edu.mayo.dhs.ievaluate.api.models.assertions.AssertionOutput;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

/**
 * Internal handling for {@link AssertionDefinition} management ensuring that definitions are all singletons
 */
public final class AssertionDefinitionManager {
    private final Map<String, AssertionDefinition> definitionMap = new HashMap<>();

    public final AssertionDefinition getDefinition(Class<? extends AssertionDefinition> clazz) {
        AssertionDefinition ret = definitionMap.get(clazz.getName());
        if (ret == null) {
            throw new IllegalArgumentException("Attempted to retrieve an unregistered assertion definition type" + clazz.getName());
        }
        return ret;
    }

    public final AssertionInput getInputDefinition(Class<? extends AssertionInput> clazz) {
        try {
            Constructor<?> ctor = clazz.getConstructor();
            return (AssertionInput) ctor.newInstance();
        } catch (Throwable t) {
            IEvaluate.getLogger().warn("Could not load assertion input definition " + clazz.getName(), t);
            return null;
        }

    }

    public final AssertionOutput getOutputDefinition(Class<? extends AssertionOutput> clazz) {
        try {
            Constructor<?> ctor = clazz.getConstructor();
            return (AssertionOutput) ctor.newInstance();
        } catch (Throwable t) {
            IEvaluate.getLogger().warn("Could not load assertion output definition " + clazz.getName(), t);
            return null;
        }
    }


    public final void registerDefinition(Class<? extends AssertionDefinition> clazz) {
        if (IEvaluate.getServer().getInitializationState().ordinal() >= IEvaluateServer.InitState.POST_ENABLE.ordinal()) {
            IEvaluate.getLogger().warn("Attempted to register an assertion definition type after onEnable(), ignoring");
            return;
        }
        if (definitionMap.containsKey(clazz.getName())) {
            IEvaluate.getLogger().warn("Ignoring duplicate assertion definition registration " + clazz.getName());
            return;
        }
        try {
            Constructor<?> ctor = clazz.getConstructor();
            AssertionDefinition def = (AssertionDefinition) ctor.newInstance();
            definitionMap.put(clazz.getName(), def);
        } catch (Throwable t) {
            IEvaluate.getLogger().warn("Could not load assertion definition " + clazz.getName(), t);
        }
    }

}
