package bb_agent_demo;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.DescriptionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.InstallationListener;
import net.bytebuddy.agent.builder.AgentBuilder.Listener;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.ClassInjector.UsingInstrumentation.Target;
import net.bytebuddy.matcher.ElementMatcher;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION;
import static net.bytebuddy.asm.Advice.to;
import static net.bytebuddy.dynamic.ClassFileLocator.ForClassLoader.read;
import static net.bytebuddy.matcher.ElementMatchers.none;

final class DisallowedOperationConfigurer {

    public static void setup(Map<String, Set<String>> disallowedMethods) throws IOException {
        // install BB agent
        Instrumentation instrumentation = ByteBuddyAgent.install();

        // prepare native methods for instrumentation
        var prefixerTransformer = new NativePrefixerTransformer(disallowedMethods);
        instrumentation.addTransformer(prefixerTransformer, true);
        instrumentation.setNativeMethodPrefix(prefixerTransformer, NativePrefixerTransformer.PREFIX);

        // enrich BB agent with missing classes
        File temp = Files.createTempDirectory("tmp").toFile();
        Map<TypeDescription, byte[]> map = new HashMap<>();
        map.put(new TypeDescription.ForLoadedType(DisallowedOperation.class), read(DisallowedOperation.class));
        map.put(new TypeDescription.ForLoadedType(DisallowedOperationInterceptorSwitch.class), read(DisallowedOperationInterceptorSwitch.class));
        ClassInjector.UsingInstrumentation.of(temp, Target.BOOTSTRAP, instrumentation).inject(map);

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
