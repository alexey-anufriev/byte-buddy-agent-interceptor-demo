package bb_agent_demo;

public class DisallowedOperationInterceptorSwitch {

    public static final ThreadLocal<Boolean> ENABLED = ThreadLocal.withInitial(() -> true);

}
