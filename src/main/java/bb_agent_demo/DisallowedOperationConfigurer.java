package bb_agent_demo;

import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.agent.builder.AgentBuilder.InstallationListener;
import net.bytebuddy.agent.builder.AgentBuilder.Listener;
import net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy;
import net.bytebuddy.agent.builder.AgentBuilder.TypeStrategy;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassInjector;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;
import net.bytebuddy.utility.nullability.MaybeNull;

import java.io.File;
import java.io.IOException;
import java.lang.instrument.Instrumentation;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static net.bytebuddy.agent.builder.AgentBuilder.RedefinitionStrategy.RETRANSFORMATION;
import static net.bytebuddy.dynamic.ClassFileLocator.ForClassLoader.read;
import static net.bytebuddy.dynamic.loading.ClassInjector.UsingInstrumentation.Target.BOOTSTRAP;
import static net.bytebuddy.matcher.ElementMatchers.is;
import static net.bytebuddy.matcher.ElementMatchers.none;

public final class DisallowedOperationConfigurer {

    public static void setup() throws IOException {
        // install BB agent
        Instrumentation instrumentation = ByteBuddyAgent.install();

        // enrich BB agent with missing classes
        File temp = Files.createTempDirectory("tmp").toFile();
        Map<TypeDescription, byte[]> map = new HashMap<>();
        map.put(new TypeDescription.ForLoadedType(DisallowedOperation.class), read(DisallowedOperation.class));
        ClassInjector.UsingInstrumentation.of(temp, BOOTSTRAP, instrumentation).inject(map);

        // setup interception rules
        new AgentBuilder.Default()
                .disableClassFormatChanges()
                .with(RETRANSFORMATION)
                .with(RedefinitionStrategy.Listener.StreamWriting.toSystemError())
                .with(Listener.StreamWriting.toSystemError().withTransformationsOnly())
                .with(InstallationListener.StreamWriting.toSystemError())
                .with(TypeStrategy.Default.DECORATE)
                .ignore(none())
                .type(is(Thread.class))
                .transform(DisallowedOperationConfigurer::transformer)
                .installOn(instrumentation);
    }

    private static DynamicType.Builder<?> transformer(
            DynamicType.Builder<?> builder, TypeDescription typeDescription,
            @MaybeNull ClassLoader classLoader, @MaybeNull JavaModule module) {

        return builder.visit(
                Advice.to(DisallowedOperationInterceptor.class)
                        .on(ElementMatchers.named("sleep")));
    }

}
