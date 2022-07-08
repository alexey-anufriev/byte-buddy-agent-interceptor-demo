package bb_agent_demo;

import java.io.IOException;

public class Demo {

    public static void main(String[] args) throws InterruptedException, IOException {
        DisallowedOperationConfigurer.setup();
        Thread.sleep(1);
    }

}
