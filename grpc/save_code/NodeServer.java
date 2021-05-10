package cn.yingming.grpc1;


import io.grpc.*;
import io.grpc.bistream.*;
import io.grpc.stub.StreamObserver;
import org.jgroups.*;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
// The design v2.0. It meets the requirement of the task 2.
public class NodeServer {
    private int t;
    // The port of server.
    private int port;
    // The server of gRPC
    private Server server;
    // <no, ip>, it stores all ip address for clients, who are connecting to this server.
    private ConcurrentHashMap<Integer, String> ips;
    // 2. JGroups part:
    String nodeName;
    String jClusterName;
    NodeJChannel jchannel;
    ArrayList<String> msgList;
    public NodeServer(int port, String nodeName, String jClusterName) throws Exception {
        //
        this.port = port;
        this.nodeName = nodeName;
        this.jClusterName = jClusterName;
        // not useful
        this.ips = new ConcurrentHashMap<>();

        // jChannel and gRPC server of this node
        this.msgList = new ArrayList<>();
        // this.jchannel = new NodeJChannel(nodeName, jClusterName, this.msgList);
        this.server = ServerBuilder.forPort(port)
                .addService(new CommunicateImpl(jchannel))
                .intercept(new ClientAddInterceptor())
                .build();

        CommunicateImpl test = new CommunicateImpl(jchannel);


    }
    private void start() throws Exception {
        this.server.start();
        System.out.println("---Server Starts.---");
        // The method will run before closing the server.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("---shutting down gRPC server since JVM is shutting down---");
                NodeServer.this.stop();
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
        // Port, NodeName, ClusterName
        final NodeServer server = new NodeServer(Integer.parseInt(args[0]), args[1], args[2]);
        // start gRPC service
        server.start();
        server.blockUntilShutdown();
    }
    // Service
    private class CommunicateImpl extends CommunicateGrpc.CommunicateImplBase {
        // HashMap for storing the clients, includes uuid and StreamObserver.
        protected final ConcurrentHashMap<String, StreamObserver<StreamResponse>> clients =
                new ConcurrentHashMap<>();
        protected final ReentrantLock lock = new ReentrantLock();
        protected final NodeJChannel jchannel;
        //protected final ArrayList<String> msgList;

        private CommunicateImpl(NodeJChannel jchannel) throws Exception {
            this.jchannel = jchannel;
            //this.msgList = jchannel.msgList;
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
                        // broadcast msg to gRPC clients
                        broadcast(streamRequest);
                        // forward msg to other JChannels
                        forward(streamRequest);
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

        public StreamObserver<StreamReqAsk> ask(StreamObserver<StreamRepAsk> responseObserver){
            return new StreamObserver<StreamReqAsk>() {
                @Override
                public void onNext(StreamReqAsk streamReqAsk) {
                    if (msgList.size() == 0){
                        // response, null
                        StreamRepAsk repAsk = StreamRepAsk.newBuilder()
                                .setSurvival(true)
                                .build();
                        responseObserver.onNext(repAsk);
                    } else{

                        lock.lock();
                        try{
                            // broadcast message from other JChannels
                            for (int i = 0; i < msgList.size(); i++) {
                                broadcast(msgList.get(i));
                            }
                            msgList.clear();
                            System.out.println("Broadcast messages and clear message list.");
                        }
                        // run after return, confirm the lock will be unlock.
                        finally {
                            lock.unlock();
                        }
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

        protected void broadcast(String message){
            lock.lock();
            try{
                // set the message (from other nodes) which is broadcast to all clients.
                String[] msg = message.split("\t");
                StreamResponse broMsg = StreamResponse.newBuilder()
                        .setName(msg[0])
                        .setMessage(msg[1])
                        .setTimestamp(msg[2])
                        .build();
                // Iteration of StreamObserver for broadcast message.
                for (String u : clients.keySet()){
                    clients.get(u).onNext(broMsg);
                    //
                }
                System.out.println("One broadcast for message from other nodes.");
                System.out.println(broMsg.toString());
            }
            // run after return, confirm the lock will be unlock.
            finally {
                lock.unlock();
            }
        }
        // Send the message to other JChannel
        protected void forward(StreamRequest req){
            String strMsg = Utils.streamToStrMsg(req);
            Message msg = new ObjectMessage(null, strMsg);
            try {
                this.jchannel.channel.send(msg);
            } catch (Exception e) {
                e.printStackTrace();
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