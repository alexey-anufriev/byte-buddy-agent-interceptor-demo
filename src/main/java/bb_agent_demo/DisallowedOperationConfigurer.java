package bb_agent_demo;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.DescriptionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.InstallationListener;
import net.bytebuddy.agent.builder.AgentBuilder.Listener;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.dynamic.loading.ClassInjector.UsingInstrumentation.Target;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION;
import static net.bytebuddy.asm.Advice.to;
import static net.bytebuddy.dynamic.ClassFileLocator.ForClassLoader.read;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.none;

final class DisallowedOperationConfigurer {

    public static void setup(Map<Class<?>, Set<Method>> disallowedMethods) throws IOException {
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

        for (var clazz : disallowedMethods.keySet()) {
            for (Method disallowedMethod : disallowedMethods.get(clazz)) {
                agentBuilder = agentBuilder.type(is(clazz)).transform((builder, typeDescription, classLoader, module) ->
                        builder.visit(to(DisallowedOperationInterceptor.class).on(named(disallowedMethod.getName()))));
            }
        }

        agentBuilder.installOn(instrumentation);
    }

}
