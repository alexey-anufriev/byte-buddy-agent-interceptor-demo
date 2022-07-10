package bb_agent_demo;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.lang3.reflect.MethodUtils.getMatchingMethod;

public class Demo {

    public static void main(String[] args) throws InterruptedException, IOException {
        Map<Class<?>, Set<Method>> disallowedMethods = Map.of(
                Thread.class, Set.of(getMatchingMethod(Thread.class, "sleep", long.class))
        );

        DisallowedOperationConfigurer.setup(disallowedMethods);

        Thread.sleep(1);
    }

}
