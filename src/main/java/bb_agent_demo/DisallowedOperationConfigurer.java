package bb_agent_demo;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.DescriptionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.InstallationListener;
import net.bytebuddy.agent.builder.AgentBuilder.Listener;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.matcher.ElementMatcher;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.instrument.Instrumentation;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION;
import static net.bytebuddy.asm.Advice.to;
import static net.bytebuddy.matcher.ElementMatchers.none;

final class DisallowedOperationConfigurer {

    public static void setup(Map<String, Set<String>> disallowedMethods) throws IOException {
        // install BB agent
        Instrumentation instrumentation = ByteBuddyAgent.install();

        // prepare native methods for instrumentation
        var prefixerTransformer = new NativePrefixerTransformer(disallowedMethods);
        instrumentation.addTransformer(prefixerTransformer, true);
        instrumentation.setNativeMethodPrefix(prefixerTransformer, NativePrefixerTransformer.PREFIX);

        // enrich boostrap classloader with missing classes
        extendBootstrapWithClasses(instrumentation,
                "bb_agent_demo.DisallowedOperation",
                "bb_agent_demo.DisallowedOperationInterceptorSwitch");

        // setup interception rules
        AgentBuilder agentBuilder = new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(RETRANSFORMATION)
                .with(RedefinitionStrategy.Listener.StreamWriting.toSystemError())
                .with(Listener.StreamWriting.toSystemError().withTransformationsOnly())
                .with(InstallationListener.StreamWriting.toSystemError())
                .with(TypeStrategy.Default.DECORATE)
                .with(DescriptionStrategy.Default.POOL_FIRST)
                .ignore(none());

        for (String clazz : disallowedMethods.keySet()) {
            for (String disallowedMethod : disallowedMethods.get(clazz)) {
                agentBuilder = agentBuilder
                        .type(target -> clazz.equals(target.getCanonicalName()))
                        .transform(transformer(disallowedMethod));
            }
        }

        agentBuilder.installOn(instrumentation);
    }

    private static void extendBootstrapWithClasses(Instrumentation instrumentation, String ... classNames) throws IOException {
        File tempJarBundle = File.createTempFile("DisallowedOperationBundle", ".jar");
        tempJarBundle.deleteOnExit();

        ClassLoader classLoader = DisallowedOperationConfigurer.class.getClassLoader();
        try (ZipOutputStream jarBundleStream = new ZipOutputStream(new FileOutputStream(tempJarBundle))) {
            for (String className : classNames) {
                String classFile = className.replace(".", "/") + ".class";
                try (InputStream classFileStream = classLoader.getResourceAsStream(classFile)) {
                    if (classFileStream == null) {
                        throw new IllegalArgumentException(className + " cannot be loaded for injection");
                    }

                    ZipEntry classFileEntry = new ZipEntry(classFile);
                    jarBundleStream.putNextEntry(classFileEntry);
                    jarBundleStream.write(classFileStream.readAllBytes());
                }

                jarBundleStream.closeEntry();
            }
        }
        instrumentation.appendToBootstrapClassLoaderSearch(new JarFile(tempJarBundle));
    }


    private static AgentBuilder.Transformer transformer(String disallowedMethod) {
        return (builder, typeDescription, classLoader, module) ->
                builder.visit(to(DisallowedOperationInterceptor.class).on(match(disallowedMethod)));
    }

    private static ElementMatcher<MethodDescription> match(String disallowedMethod) {
        return target -> {
            String returnType = target.getReturnType().getActualName();

            String arguments = target.getParameters().stream()
                    .map(parameter -> parameter.getType().getActualName())
                    .collect(Collectors.joining(","));

            String methodName = String.format("%s %s(%s)", returnType, target.getName(), arguments);
            return disallowedMethod.equals(methodName);
        };
    }

}
