package cn.yingming.grpc1;

import io.grpc.*;
import io.grpc.bistream.CommunicateGrpc;
import io.grpc.bistream.StreamRequest;
import io.grpc.bistream.StreamResponse;
import io.grpc.stub.StreamObserver;

import javax.swing.plaf.basic.BasicInternalFrameTitlePane;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class BiStreamServer {
    // define the port and server
    private final int port = 50051;
    private Server server;
    // <No, ip>
    private ConcurrentHashMap<Integer, String> ips = new ConcurrentHashMap<>();
    // Start the server and listen.
    private void start() throws IOException {
        server = ServerBuilder.forPort(port)
                .addService(new CommunicateImpl())
                .intercept(new ClientAddInterceptor())
                .build().start();
        System.out.println("---Server Starts.---");
        // The method will run before closing the server.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("---shutting down gRPC server since JVM is shutting down---");
                BiStreamServer.this.stop();
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
    public static void main(String[] args) throws IOException, InterruptedException {
        final BiStreamServer server = new BiStreamServer();
        server.start();
        server.blockUntilShutdown();
    }
    // Service
    private class CommunicateImpl extends CommunicateGrpc.CommunicateImplBase {
        // HashMap for storing the clients, includes address and StreamObserver
        protected final ConcurrentHashMap<String, StreamObserver<StreamResponse>> clients =
                new ConcurrentHashMap<>();
        protected final ReentrantLock lock = new ReentrantLock();

        public StreamObserver<StreamRequest> createConnection(StreamObserver<StreamResponse> responseObserver){
            return new StreamObserver<StreamRequest>() {
                @Override
                public void onNext(StreamRequest streamRequest) {
                    if (streamRequest.getJoin()){ // true
                        System.out.println(streamRequest.getSource() + " joins the chat.");
                        // responseObserver
                        join(streamRequest, responseObserver);
                    }
                    else{
                        System.out.println(streamRequest.getSource() + " sends message: " + streamRequest.getMessage()
                                + " at " + streamRequest.getTimestamp());
                        broadcast(streamRequest);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    System.out.println(throwable.getMessage());
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }

        protected void join(StreamRequest req, StreamObserver<StreamResponse> responseObserver){
            // 1. get lock
            lock.lock();
            // 2. critical section
            try{
                clients.put(req.getSource(), responseObserver);
            }
            // 3. run finally, confirm the lock will be unlock.
            finally {
                // remember unlock
                lock.unlock();
            }
        }
        // Broadcast messages.
        protected void broadcast(StreamRequest req){
            lock.lock();
            try{
                // set the message which is broadcast to all clients.
                String address = req.getSource();
                String msg = req.getMessage();
                String timeStr = req.getTimestamp();
                StreamResponse broMsg = StreamResponse.newBuilder()
                        .setSource(address)
                        .setMessage(msg)
                        .setTimestamp(timeStr)
                        .build();
                // Iteration of StreamObserver for broadcast message.
                for (String name : clients.keySet()){
                    clients.get(name).onNext(broMsg);
                }
                System.out.println("One broadcast for message.");
            }
            // run after return, confirm the lock will be unlock.
            finally {
                lock.unlock();
            }
        }
    }
    // Get ip address of client when receive the join request.
    private class ClientAddInterceptor implements ServerInterceptor {
        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
            String ip = call.getAttributes().get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR).toString();
            System.out.println("Joined Client IP address: " + ip);
            ips.put(ips.size(), ip);
            return next.startCall(call, headers);
        }
    }

}
