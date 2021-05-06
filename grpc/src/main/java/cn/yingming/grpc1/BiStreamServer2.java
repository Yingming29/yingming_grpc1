package cn.yingming.grpc1;


import io.grpc.*;
import io.grpc.bistream.CommunicateGrpc;
import io.grpc.bistream.StreamRequest;
import io.grpc.bistream.StreamResponse;
import io.grpc.stub.StreamObserver;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class BiStreamServer2 {
    // define the port and server
    private final int port = 50051;
    private Server server;
    // <no, ip>.
    private ConcurrentHashMap<Integer, String> ips = new ConcurrentHashMap<>();
    // Start the server and listen.
    private void start() throws Exception {
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
                BiStreamServer2.this.stop();
                System.err.println("---server shut down---");
            }
        });
    }

    // Stop server
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
    public static void main(String[] args) throws Exception {
        final BiStreamServer2 server = new BiStreamServer2();
        server.start();
        server.blockUntilShutdown();
    }
    // Service
    private class CommunicateImpl extends CommunicateGrpc.CommunicateImplBase {
        // HashMap for storing the clients, includes uuid and StreamObserver.
        protected final ConcurrentHashMap<String, StreamObserver<StreamResponse>> clients =
                new ConcurrentHashMap<>();
        protected final ReentrantLock lock = new ReentrantLock();
        protected final SimpleChat jchannel;

        private CommunicateImpl() throws Exception {
            this.jchannel = new SimpleChat();
        }

        public StreamObserver<StreamRequest> createConnection(StreamObserver<StreamResponse> responseObserver){
            return new StreamObserver<StreamRequest>() {
                @Override
                public void onNext(StreamRequest streamRequest) {
                    if (streamRequest.getJoin()){ // true
                        System.out.println(streamRequest.getName() + "(" +
                                streamRequest.getSource() + ") joins the chat.");
                        // responseObserver
                        join(streamRequest, responseObserver);
                    }
                    else{
                        System.out.println(streamRequest.getName() + " sends message: " + streamRequest.getMessage()
                                + " at " + streamRequest.getTimestamp());
                        broadcast(streamRequest);
                    }
                }

                @Override
                public void onError(Throwable throwable) { System.out.println(throwable.getMessage());
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
                Date d = new Date();
                SimpleDateFormat dft = new SimpleDateFormat("hh:mm:ss");
                StreamResponse joinResponse = StreamResponse.newBuilder()
                        .setName("Server")
                        .setMessage("You join successfully.")
                        .setTimestamp(dft.format(d))
                        .build();
                responseObserver.onNext(joinResponse);

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
                String name = req.getName();
                String msg = req.getMessage();
                String timeStr = req.getTimestamp();
                StreamResponse broMsg = StreamResponse.newBuilder()
                        .setName(name)
                        .setMessage(msg)
                        .setTimestamp(timeStr)
                        .build();
                // Iteration of StreamObserver for broadcast message.
                for (String u : clients.keySet()){
                    clients.get(u).onNext(broMsg);
                    //
                }
                System.out.println("One broadcast for message.");
                System.out.println(broMsg.toString());
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