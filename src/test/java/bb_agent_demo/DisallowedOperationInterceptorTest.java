package bb_agent_demo;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.fail;

class DisallowedOperationInterceptorTest {

    @Test
    void shouldNotDoInterceptionIfNotSetup() {
        DisallowedOperationInterceptorSwitch.ENABLED.set(true);

        assertThatNoException().isThrownBy(() -> Thread.sleep(1));
    }

    @Test
    void shouldDoInterceptionIfSetup() throws IOException {
        DisallowedOperationInterceptorSwitch.ENABLED.set(true);

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
    void shouldNotDoInterceptionIfDisabled() throws IOException {
        DisallowedOperationInterceptorSwitch.ENABLED.set(false);

        Map<String, Set<String>> disallowedMethods = Map.of(
                "java.lang.Thread", Set.of(
                        "void sleep(long)",
                        "java.lang.StackTraceElement[][] dumpThreads(java.lang.Thread[])")
        );

        DisallowedOperationConfigurer.setup(disallowedMethods);

        try {
            Thread.getAllStackTraces(); // calls `dumpThreads` inside
        }
        catch (Exception e) {
            fail("No exceptions are expected");
        }
    }

}
