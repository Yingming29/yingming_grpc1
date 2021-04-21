package cn.yingming.grpc1;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.bistream.CommunicateGrpc;
import io.grpc.bistream.StreamRequest;
import io.grpc.bistream.StreamResponse;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class BiStreamServer {
    // define the port and server
    private final int port = 50051;
    private Server server;
    // Start the server and listen.
    private void start() throws IOException {
        server = ServerBuilder.forPort(port).addService(new CommunicateImpl()).build().start();

        System.out.println(server.getListenSockets());
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
    private void  blockUntilShutdown() throws InterruptedException {
        if (server!=null){
            server.awaitTermination();
        }
    }
    public static void main(String[] args) throws IOException, InterruptedException {
        final BiStreamServer server = new BiStreamServer();
        server.start();
        server.blockUntilShutdown();
    }

    private class CommunicateImpl extends CommunicateGrpc.CommunicateImplBase {
        // HashMap for storing the clients, includes address and StreamObserver
        protected final ConcurrentHashMap<String, StreamObserver<StreamResponse>> clients =
                new ConcurrentHashMap<String, StreamObserver<StreamResponse>>();
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
            lock.lock();
            try{
                clients.put(req.getSource(), responseObserver);
            }
            // run after return, confirm the lock will be unlock.
            finally {
                // remember unlock
                lock.unlock();
            }
        }

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
                // Iteration of StreamObserver for broadcast.
                for (String add : clients.keySet()){
                    clients.get(add).onNext(broMsg);
                }
                System.out.println("One broadcast for message.");
            }
            // run after return, confirm the lock will be unlock.
            finally {
                lock.unlock();
            }
        }
    }
}
