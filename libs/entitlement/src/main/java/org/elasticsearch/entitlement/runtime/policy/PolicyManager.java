/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.entitlement.runtime.policy;

import org.elasticsearch.core.Strings;
import org.elasticsearch.entitlement.runtime.api.NotEntitledException;
import org.elasticsearch.logging.LogManager;
import org.elasticsearch.logging.Logger;

import java.lang.StackWalker.StackFrame;
import java.lang.module.ModuleFinder;
import java.lang.module.ModuleReference;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;
import static java.util.Objects.requireNonNull;
import static java.util.function.Predicate.not;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toUnmodifiableMap;

public class PolicyManager {
    private static final Logger logger = LogManager.getLogger(PolicyManager.class);

    record ModuleEntitlements(Map<Class<? extends Entitlement>, List<Entitlement>> entitlementsByType) {
        public static final ModuleEntitlements NONE = new ModuleEntitlements(Map.of());

        ModuleEntitlements {
            entitlementsByType = Map.copyOf(entitlementsByType);
        }

        public static ModuleEntitlements from(List<Entitlement> entitlements) {
            return new ModuleEntitlements(entitlements.stream().collect(groupingBy(Entitlement::getClass)));
        }

        public boolean hasEntitlement(Class<? extends Entitlement> entitlementClass) {
            return entitlementsByType.containsKey(entitlementClass);
        }

        public <E extends Entitlement> Stream<E> getEntitlements(Class<E> entitlementClass) {
            return entitlementsByType.get(entitlementClass).stream().map(entitlementClass::cast);
        }
    }

    final Map<Module, ModuleEntitlements> moduleEntitlementsMap = new ConcurrentHashMap<>();

    protected final Map<String, List<Entitlement>> serverEntitlements;
    protected final List<Entitlement> agentEntitlements;
    protected final Map<String, Map<String, List<Entitlement>>> pluginsEntitlements;
    private final Function<Class<?>, String> pluginResolver;

    public static final String ALL_UNNAMED = "ALL-UNNAMED";

    private static final Set<Module> systemModules = findSystemModules();

    private static Set<Module> findSystemModules() {
        var systemModulesDescriptors = ModuleFinder.ofSystem()
            .findAll()
            .stream()
            .map(ModuleReference::descriptor)
            .collect(Collectors.toUnmodifiableSet());
        return ModuleLayer.boot()
            .modules()
            .stream()
            .filter(m -> systemModulesDescriptors.contains(m.getDescriptor()))
            .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Frames originating from this module are ignored in the permission logic.
     */
    private final Module entitlementsModule;

    public PolicyManager(
        Policy serverPolicy,
        List<Entitlement> agentEntitlements,
        Map<String, Policy> pluginPolicies,
        Function<Class<?>, String> pluginResolver,
        Module entitlementsModule
    ) {
        this.serverEntitlements = buildScopeEntitlementsMap(requireNonNull(serverPolicy));
        this.agentEntitlements = agentEntitlements;
        this.pluginsEntitlements = requireNonNull(pluginPolicies).entrySet()
            .stream()
            .collect(toUnmodifiableMap(Map.Entry::getKey, e -> buildScopeEntitlementsMap(e.getValue())));
        this.pluginResolver = pluginResolver;
        this.entitlementsModule = entitlementsModule;
    }

    private static Map<String, List<Entitlement>> buildScopeEntitlementsMap(Policy policy) {
        return policy.scopes().stream().collect(toUnmodifiableMap(Scope::moduleName, Scope::entitlements));
    }

    public void checkStartProcess(Class<?> callerClass) {
        neverEntitled(callerClass, "start process");
    }

    private void neverEntitled(Class<?> callerClass, String operationDescription) {
        var requestingModule = requestingClass(callerClass);
        if (isTriviallyAllowed(requestingModule)) {
            return;
        }

        throw new NotEntitledException(
            Strings.format(
                "Not entitled: caller [%s], module [%s], operation [%s]",
                callerClass,
                requestingModule.getName(),
                operationDescription
            )
        );
    }

    /**
     * @param operationDescription is only called when the operation is not trivially allowed, meaning the check is about to fail;
     *                            therefore, its performance is not a major concern.
     */
    private void neverEntitled(Class<?> callerClass, Supplier<String> operationDescription) {
        var requestingModule = requestingClass(callerClass);
        if (isTriviallyAllowed(requestingModule)) {
            return;
        }

        throw new NotEntitledException(
            Strings.format(
                "Not entitled: caller [%s], module [%s], operation [%s]",
                callerClass,
                requestingModule.getName(),
                operationDescription.get()
            )
        );
    }

    public void checkExitVM(Class<?> callerClass) {
        checkEntitlementPresent(callerClass, ExitVMEntitlement.class);
    }

    public void checkCreateClassLoader(Class<?> callerClass) {
        checkEntitlementPresent(callerClass, CreateClassLoaderEntitlement.class);
    }

    public void checkSetHttpsConnectionProperties(Class<?> callerClass) {
        checkEntitlementPresent(callerClass, SetHttpsConnectionPropertiesEntitlement.class);
    }

    public void checkChangeJVMGlobalState(Class<?> callerClass) {
        neverEntitled(callerClass, () -> {
            // Look up the check$ method to compose an informative error message.
            // This way, we don't need to painstakingly describe every individual global-state change.
            Optional<String> checkMethodName = StackWalker.getInstance()
                .walk(
                    frames -> frames.map(StackFrame::getMethodName)
                        .dropWhile(not(methodName -> methodName.startsWith("check$")))
                        .findFirst()
                );
            return checkMethodName.map(this::operationDescription).orElse("change JVM global state");
        });
    }

    private String operationDescription(String methodName) {
        // TODO: Use a more human-readable description. Perhaps share code with InstrumentationServiceImpl.parseCheckerMethodName
        return methodName.substring(methodName.indexOf('$'));
    }

    private void checkEntitlementPresent(Class<?> callerClass, Class<? extends Entitlement> entitlementClass) {
        var requestingClass = requestingClass(callerClass);
        if (isTriviallyAllowed(requestingClass)) {
            return;
        }

        ModuleEntitlements entitlements = getEntitlements(requestingClass);
        if (entitlements.hasEntitlement(entitlementClass)) {
            logger.debug(
                () -> Strings.format(
                    "Entitled: class [%s], module [%s], entitlement [%s]",
                    requestingClass,
                    requestingClass.getModule().getName(),
                    entitlementClass.getSimpleName()
                )
            );
            return;
        }
        throw new NotEntitledException(
            Strings.format(
                "Missing entitlement: class [%s], module [%s], entitlement [%s]",
                requestingClass,
                requestingClass.getModule().getName(),
                entitlementClass.getSimpleName()
            )
        );
    }

    ModuleEntitlements getEntitlements(Class<?> requestingClass) {
        return moduleEntitlementsMap.computeIfAbsent(requestingClass.getModule(), m -> computeEntitlements(requestingClass));
    }

    private ModuleEntitlements computeEntitlements(Class<?> requestingClass) {
        Module requestingModule = requestingClass.getModule();
        if (isServerModule(requestingModule)) {
            return getModuleScopeEntitlements(requestingClass, serverEntitlements, requestingModule.getName());
        }

        // plugins
        var pluginName = pluginResolver.apply(requestingClass);
        if (pluginName != null) {
            var pluginEntitlements = pluginsEntitlements.get(pluginName);
            if (pluginEntitlements != null) {
                final String scopeName;
                if (requestingModule.isNamed() == false) {
                    scopeName = ALL_UNNAMED;
                } else {
                    scopeName = requestingModule.getName();
                }
                return getModuleScopeEntitlements(requestingClass, pluginEntitlements, scopeName);
            }
        }

        if (requestingModule.isNamed() == false) {
            // agents are the only thing running non-modular
            return ModuleEntitlements.from(agentEntitlements);
        }

        logger.warn("No applicable entitlement policy for class [{}]", requestingClass.getName());
        return ModuleEntitlements.NONE;
    }

    private ModuleEntitlements getModuleScopeEntitlements(
        Class<?> callerClass,
        Map<String, List<Entitlement>> scopeEntitlements,
        String moduleName
    ) {
        var entitlements = scopeEntitlements.get(moduleName);
        if (entitlements == null) {
            logger.warn("No applicable entitlement policy for module [{}], class [{}]", moduleName, callerClass);
            return ModuleEntitlements.NONE;
        }
        return ModuleEntitlements.from(entitlements);
    }

    private static boolean isServerModule(Module requestingModule) {
        return requestingModule.isNamed() && requestingModule.getLayer() == ModuleLayer.boot();
    }

    /**
     * Walks the stack to determine which class should be checked for entitlements.
     *
     * @param callerClass when non-null will be returned;
     *                    this is a fast-path check that can avoid the stack walk
     *                    in cases where the caller class is available.
     * @return the requesting class, or {@code null} if the entire call stack
     * comes from the entitlement library itself.
     */
    Class<?> requestingClass(Class<?> callerClass) {
        if (callerClass != null) {
            // fast path
            return callerClass;
        }
        Optional<Class<?>> result = StackWalker.getInstance(RETAIN_CLASS_REFERENCE)
            .walk(frames -> findRequestingClass(frames.map(StackFrame::getDeclaringClass)));
        return result.orElse(null);
    }

    /**
     * Given a stream of classes corresponding to the frames from a {@link StackWalker},
     * returns the module whose entitlements should be checked.
     *
     * @throws NullPointerException if the requesting module is {@code null}
     */
    Optional<Class<?>> findRequestingClass(Stream<Class<?>> classes) {
        return classes.filter(c -> c.getModule() != entitlementsModule)  // Ignore the entitlements library
            .skip(1)                                           // Skip the sensitive caller method
            .findFirst();
    }

    /**
     * @return true if permission is granted regardless of the entitlement
     */
    private static boolean isTriviallyAllowed(Class<?> requestingClass) {
        if (logger.isTraceEnabled()) {
            logger.trace("Stack trace for upcoming trivially-allowed check", new Exception());
        }
        if (requestingClass == null) {
            logger.debug("Entitlement trivially allowed: no caller frames outside the entitlement library");
            return true;
        }
        if (systemModules.contains(requestingClass.getModule())) {
            logger.debug("Entitlement trivially allowed from system module [{}]", requestingClass.getModule().getName());
            return true;
        }
        logger.trace("Entitlement not trivially allowed");
        return false;
    }

    @Override
    public String toString() {
        return "PolicyManager{" + "serverEntitlements=" + serverEntitlements + ", pluginsEntitlements=" + pluginsEntitlements + '}';
    }
}
