package cn.yingming.grpc1;

import io.grpc.*;
import io.grpc.bistream.CommunicateGrpc;
import io.grpc.bistream.StreamRequest;
import io.grpc.bistream.StreamResponse;
import io.grpc.stub.StreamObserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.jgroups.*;

import cn.yingming.grpc1.Utils;

public class BiStreamServer implements Receiver {
    private int t;
    // The port of server.
    private int port;
    // The server of gRPC
    private Server server;
    // <no, ip>, it stores all ip address for clients, who are connecting to this server.
    private ConcurrentHashMap<Integer, String> ips;
    // 2. JGroups part:
    JChannel channel;
    String nodeName;
    String jClusterName;

    // Server node contains gRPC server and J channel.
    public BiStreamServer(int port, String nodeName, String jClusterName) throws Exception {
        // three args
        this.port = port;
        this.nodeName = nodeName;
        this.jClusterName = jClusterName;
        // gRPC server
        this.server = ServerBuilder.forPort(port)
                .addService(new CommunicateImpl())
                .intercept(new ClientAddInterceptor())
                .build();
        this.ips = new ConcurrentHashMap<>();
        this.channel = new JChannel();

    }
    // 1. Create files
    private void startFiles(String nodeName) throws Exception {
        boolean result = Utils.createTxtFile(nodeName);
        if (result){
            System.out.println("Node creates file successfully.");
        } else{
            throw new Exception("Node creates file unsuccessfully.");
        }
    }

    // 2. Start the gRPC server.
    private void startGrpc() throws IOException {
        this.server.start();
        System.out.println("---gRPC Server Starts.---");
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

    // 3. Start JChannel
    private void startJchannel() throws Exception {
        this.channel.setReceiver(this);
        this.channel.connect(this.jClusterName);
        // eventLoop();
        this.channel.close();
    }

    // loop for checking the shared file for the message.
    private void eventLoop() {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            try {
                System.out.println(">");
                // Before readLine(), clear the in stream.
                System.out.flush();
                String line = in.readLine().toLowerCase();
                if (line.startsWith("quit") || line.startsWith("exit")) {
                    break;
                }
                line = "[" + "Node1" + "]" + line;
                // destination address is null, send msg to everyone in the cluster.
                Message msg = new ObjectMessage(null, line);
                channel.send(msg);
            } catch (Exception e) {
                // TODO: handle exception.
            }
        }

    }
    public void viewAccepted(View new_view) {
        System.out.println("** view: " + new_view);
    }
    public void receive(org.jgroups.Message msg) {
        System.out.println(msg.getSrc() + ":" + msg.getObject());
        // Add this received message to .txt.

    }

    // Stop gRPC server
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

    // Service
    private class CommunicateImpl extends CommunicateGrpc.CommunicateImplBase {
        // HashMap for storing the clients, includes uuid and StreamObserver
        protected final ConcurrentHashMap<String, StreamObserver<StreamResponse>> clients =
                new ConcurrentHashMap<>();
        protected final ReentrantLock lock = new ReentrantLock();

        public StreamObserver<StreamRequest> createConnection(StreamObserver<StreamResponse> responseObserver){
            return new StreamObserver<StreamRequest>() {
                @Override
                public void onNext(StreamRequest streamRequest) {
                    if (streamRequest.getJoin()){ // true
                        System.out.println(streamRequest.getName() + "(" +
                                streamRequest.getSource() + ") joins the node chat.");
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

    private void print(){
        while(true){
            System.out.println("test");
        }
    }

    public static void main(String[] args) throws Exception {
        // args 0, 1, 2: int port, String nodeName, String jClusterName
        final BiStreamServer node = new BiStreamServer(Integer.parseInt(args[0]), args[1], args[2]);
        node.startFiles(args[1]);
        //node.print();
        node.startGrpc();
        node.print();
        node.blockUntilShutdown();
    }
}
