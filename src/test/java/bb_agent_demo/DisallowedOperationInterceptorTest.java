package bb_agent_demo;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.fail;

@TestMethodOrder(OrderAnnotation.class)
class DisallowedOperationInterceptorTest {

    @Test
    @Order(1)
    void shouldNotDoInterceptionIfNotSetup() {
        assertThatNoException().isThrownBy(() -> Thread.sleep(1));
    }

    @Test
    @Order(2)
    void shouldDoInterceptionIfSetup() throws IOException {
        Map<String, Set<String>> disallowedMethods = Map.of(
                "java.lang.Thread", Set.of(
                        "void sleep(long)",
                        "java.lang.StackTraceElement[][] dumpThreads(java.lang.Thread[])")
        );

        DisallowedOperationConfigurer.setup(disallowedMethods);

        try {
            Thread.getAllStackTraces(); // calls `dumpThreads` inside
            fail("This statement should be unreachable");
        }
        catch (Exception e) {
            assertThat(e.getMessage()).startsWith("DISALLOWED CALL: private static java.lang.StackTraceElement[][] java.lang.Thread.dumpThreads(java.lang.Thread[])");
        }
    }

    @Test
    @Order(3)
    void shouldNotDoInterceptionIfDisabled() throws IOException {
        Map<String, Set<String>> disallowedMethods = Map.of(
                "java.lang.Thread", Set.of(
                        "void sleep(long)",
                        "java.lang.StackTraceElement[][] dumpThreads(java.lang.Thread[])")
        );

        DisallowedOperationConfigurer.setup(disallowedMethods);

        // must be referenced after BB agent is set, otherwise this class will be loaded twice
        DisallowedOperationInterceptorSwitch.ENABLED.set(false);

        try {
            Thread.getAllStackTraces(); // calls `dumpThreads` inside
        }
        catch (Exception e) {
            fail("No exceptions are expected");
        }
    }

}
