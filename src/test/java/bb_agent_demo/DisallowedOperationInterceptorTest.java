package bb_agent_demo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static org.apache.commons.lang3.reflect.MethodUtils.getMatchingMethod;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.fail;

class DisallowedOperationInterceptorTest {

    @Test
    void shouldNotDoInterceptionIfNotSetup() {
        assertThatNoException().isThrownBy(() -> Thread.sleep(1));
    }

    @Test
    void shouldDoInterceptionIfSetup() throws IOException {
        Map<Class<?>, Set<Method>> disallowedMethods = Map.of(
                Thread.class, Set.of(getMatchingMethod(Thread.class, "sleep", long.class))
        );

        DisallowedOperationConfigurer.setup(disallowedMethods);

        try {
            Thread.sleep(1);
            fail("This statement should be unreachable");
        }
        catch (Exception e) {
            assertThat(e.getMessage()).startsWith("DISALLOWED CALL: public static void java.lang.Thread.sleep");
        }
    }

}
