package cn.yingming.grpc1;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.test1.GreeterGrpc;
import io.grpc.test1.TestRequest;
import io.grpc.test1.TestResponse;

import java.io.IOException;
public class TestServer {
    // define the port and server
    private final int port = 50051;
    private Server server;
    // Start the server and listen.
    private void start() throws IOException {
        server = ServerBuilder.forPort(port).addService(new GreeterImpl()).build().start();

        System.out.println(server.getListenSockets());
        //System.out.println("---Server Starts.---");
        // The method will run before closing the server.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("---shutting down gRPC server since JVM is shutting down---");
                TestServer.this.stop();
                System.err.println("---server shut down---");
            }
        });

    }

    // Stop server.
    private void stop() {
        if (server != null) {
            server.shutdown();
        }
    }
    // Server blocks until it closes.
    private void blockUntilShutdown() throws InterruptedException {
        if (server!=null){
            server.awaitTermination();
        }
    }

    // Implementation of service
    private class GreeterImpl extends GreeterGrpc.GreeterImplBase {
        public void testSomeThing(TestRequest request, StreamObserver<TestResponse> responseObserver) {
            TestResponse build = TestResponse.newBuilder().setMessage(request.getName()+ " :Success.").build();
            System.out.println("Receive request from " + request.getName());
            // onNext() returns response to client.
            responseObserver.onNext(build);
            // it tells client that the message is done.
            responseObserver.onCompleted();
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        final  TestServer server=new TestServer();
        server.start();
        server.blockUntilShutdown();
    }
}

