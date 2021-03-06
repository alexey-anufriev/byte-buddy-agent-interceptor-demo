package bb_agent_demo;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.OnMethodEnter;

final class DisallowedOperationInterceptor {

    @OnMethodEnter
    public static void intercept(@Advice.Origin String method) {
        if (DisallowedOperationInterceptorSwitch.ENABLED.get()) {
            throw new DisallowedOperation("DISALLOWED CALL: " + method);
        }
    }

}
