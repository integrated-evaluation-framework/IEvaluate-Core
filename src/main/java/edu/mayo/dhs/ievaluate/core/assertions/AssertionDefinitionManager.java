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
    private final Map<String, AssertionInput> inputDefinitionMap = new HashMap<>();
    private final Map<String, AssertionOutput> outputDefinitionMap = new HashMap<>();

    public final AssertionDefinition getDefinition(Class<? extends AssertionDefinition> clazz) {
        AssertionDefinition ret = definitionMap.get(clazz.getName());
        if (ret == null) {
            throw new IllegalArgumentException("Attempted to retrieve an unregistered assertion definition type" + clazz.getName());
        }
        return ret;
    }

    public final AssertionInput getInputDefinition(Class<? extends AssertionInput> clazz) {
        AssertionInput ret = inputDefinitionMap.get(clazz.getName());
        if (ret == null) {
            throw new IllegalArgumentException("Attempted to retrieve an unregistered assertion input definition type" + clazz.getName());
        }
        return ret;
    }

    public final AssertionOutput getOutputDefinition(Class<? extends AssertionOutput> clazz) {
        AssertionOutput ret = outputDefinitionMap.get(clazz.getName());
        if (ret == null) {
            throw new IllegalArgumentException("Attempted to retrieve an unregistered assertion output definition type" + clazz.getName());
        }
        return ret;
    }


    public final void registerDefinition(Class<? extends AssertionDefinition> clazz) {
        if (IEvaluate.getServer().getInitializationState().ordinal() > IEvaluateServer.InitState.POST_ENABLE.ordinal()) {
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
            registerInputDef(def.getInputType());
            registerOutputDef(def.getOutputType());
        } catch (Throwable t) {
            IEvaluate.getLogger().warn("Could not load assertion definition " + clazz.getName(), t);
        }
    }


    private void registerInputDef(Class<? extends AssertionInput> clazz) {
        if (IEvaluate.getServer().getInitializationState().ordinal() > IEvaluateServer.InitState.POST_ENABLE.ordinal()) {
            IEvaluate.getLogger().warn("Attempted to register an assertion input definition type after onEnable(), ignoring");
            return;
        }
        if (inputDefinitionMap.containsKey(clazz.getName())) {
            IEvaluate.getLogger().warn("Ignoring duplicate assertion input definition registration " + clazz.getName());
            return;
        }
        try {
            Constructor<?> ctor = clazz.getConstructor();
            AssertionInput def = (AssertionInput) ctor.newInstance();
            inputDefinitionMap.put(clazz.getName(), def);
        } catch (Throwable t) {
            IEvaluate.getLogger().warn("Could not load assertion input definition " + clazz.getName(), t);
        }
    }


    private void registerOutputDef(Class<? extends AssertionOutput> clazz) {
        if (IEvaluate.getServer().getInitializationState().ordinal() > IEvaluateServer.InitState.POST_ENABLE.ordinal()) {
            IEvaluate.getLogger().warn("Attempted to register an assertion output definition type after onEnable(), ignoring");
            return;
        }
        if (outputDefinitionMap.containsKey(clazz.getName())) {
            IEvaluate.getLogger().warn("Ignoring duplicate assertion output definition registration " + clazz.getName());
            return;
        }
        try {
            Constructor<?> ctor = clazz.getConstructor();
            AssertionOutput def = (AssertionOutput) ctor.newInstance();
            outputDefinitionMap.put(clazz.getName(), def);
        } catch (Throwable t) {
            IEvaluate.getLogger().warn("Could not load assertion output definition " + clazz.getName(), t);
        }
    }
}
