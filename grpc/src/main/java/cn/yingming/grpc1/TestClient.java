package cn.yingming.grpc1;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.test1.GreeterGrpc;
import io.grpc.test1.TestRequest;
import io.grpc.test1.TestResponse;

import java.util.concurrent.TimeUnit;

public class TestClient {
    private final ManagedChannel channel;
    private final GreeterGrpc.GreeterBlockingStub blockingStub;
    private static final String host = "127.0.0.1";
    private static final int port = 50051;

    public TestClient(String host, int port) {
        // Build Channel
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        // Generate Stub
        System.out.println(channel.toString());
        blockingStub = GreeterGrpc.newBlockingStub(channel);
    }

    public void shutdown() throws InterruptedException {
        channel.shutdown().awaitTermination(10, TimeUnit.SECONDS);
    }
    // Send request to server and print response
    public void testResult(String name) {
        TestRequest request = TestRequest.newBuilder().setName(name).build();
        TestResponse response = blockingStub.testSomeThing(request);
        System.out.println(response.getMessage());
        //System.out.print(response.getAllFields());
    }

    public static void main(String[] args) {
        TestClient client = new TestClient(host, port);
        for (int i = 0; i <= 5; i++) {
            client.testResult("Client Message " + i);
        }
    }
}
