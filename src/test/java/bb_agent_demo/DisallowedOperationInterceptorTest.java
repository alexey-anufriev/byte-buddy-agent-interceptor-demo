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
        assertThatNoException().isThrownBy(() -> Thread.sleep(1));
    }

    @Test
    void shouldDoInterceptionIfSetup() throws IOException {
        Map<String, Set<String>> disallowedMethods = Map.of(
                "java/lang/Thread", Set.of("sleep")
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
