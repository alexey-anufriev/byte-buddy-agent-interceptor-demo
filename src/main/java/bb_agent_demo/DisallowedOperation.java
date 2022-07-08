package bb_agent_demo;

public final class DisallowedOperation extends RuntimeException {

    public DisallowedOperation(String message) {
        super(message);
    }

}
