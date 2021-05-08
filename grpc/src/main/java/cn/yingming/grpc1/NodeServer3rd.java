package cn.yingming.grpc1;

import io.grpc.*;
import io.grpc.bistream.*;
import io.grpc.stub.StreamObserver;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

// The design v3.0.

public class NodeServer3rd {

    // 1. Port and server used for gRPC
    private int port;
    private Server server;
    // 2. JGroups part:
    String nodeName;
    String jClusterName;
    NodeJChannel jchannel;
    // 3.shared part
    // When JChannel receives message, it stores ths message to the sharedList, and broadcast the message to its clients.
    ArrayList<String> msgList;
    ConcurrentHashMap<String, StreamObserver<StreamResponse>> clients;
    CommunicateImpl gRPCservice;
    ReentrantLock lock;
    // <no, ip>, it stores all ip address for clients, who are connecting to this server.
    private ConcurrentHashMap<Integer, String> ips;
    public NodeServer3rd(int port, String nodeName, String jClusterName) throws Exception {
        // 1
        this.port = port;
        this.nodeName = nodeName;
        this.clients = new ConcurrentHashMap<>();
        //
        this.jClusterName = jClusterName;
        // not useful
        this.ips = new ConcurrentHashMap<>();

        // shared
        this.msgList = new ArrayList<>();
        this.jchannel = new NodeJChannel(nodeName, jClusterName, this.msgList);
        this.gRPCservice = new CommunicateImpl(this.jchannel, this.clients, this.msgList);
        this.server = ServerBuilder.forPort(port)
                .addService(this.gRPCservice)
                .intercept(new ClientAddInterceptor())
                .build();
        //
        this.lock =  new ReentrantLock();

    }
    private void start() throws Exception {
        this.server.start();
        System.out.println("---Server Starts.---");
        // The method will run before closing the server.
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                System.err.println("---shutting down gRPC server since JVM is shutting down---");
                NodeServer3rd.this.stop();
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

    // always to check the shared list.
    private void eventLoop(){
        while(true){
            if (this.msgList.size() != 0) {
                System.out.println("broadcast");
                // lock
                lock.lock();
                try {
                    // int num = msgList.size();
                    //System.out.printf("Found %d message from other JChannels, broadcast to clients. ", num);
                    // broadcast all messages

                    this.gRPCservice.broadcast(this.msgList.get(0));

                    // clear message list
                    msgList.remove(0);

                } finally {
                    lock.unlock();
                }
            }
            /*
            if (this.msgList. !=0){
                System.out.println("broadcast");
                // lock
                lock.lock();
                try{
                    int num = msgList.size();
                    System.out.printf("Found %d message from other JChannels, broadcast to clients. ", num);
                    // broadcast all messages
                    for (int i = 0; i < num; i++) {
                        // broadcast message
                        this.gRPCservice.broadcast(this.msgList.get(i));
                    }
                    // clear message list
                    msgList.clear();

                } finally {
                    lock.unlock();
                }
            }

             */
        }
    }

    public static void main(String[] args) throws Exception {
        // Port, NodeName, ClusterName
        final NodeServer3rd server = new NodeServer3rd(Integer.parseInt(args[0]), args[1], args[2]);
        // start gRPC service
        server.start();
        // server.eventLoop();
        server.giveEntry(server.gRPCservice);
        server.blockUntilShutdown();
    }

    //
    public void giveEntry(CommunicateImpl gRPCservice){
        this.jchannel.setService(gRPCservice);

    }
    // Service
    class CommunicateImpl extends CommunicateGrpc.CommunicateImplBase {
        // HashMap for storing the clients, includes uuid and StreamObserver.
        private final ConcurrentHashMap<String, StreamObserver<StreamResponse>> clients;
        protected final ReentrantLock lock = new ReentrantLock();
        protected final NodeJChannel jchannel;
        private ArrayList<String> msgList;

        private CommunicateImpl(NodeJChannel jchannel, ConcurrentHashMap<String, StreamObserver<StreamResponse>> sharedMap,
                                ArrayList<String> sharedList) throws Exception {
            this.jchannel = jchannel;
            this.clients = sharedMap;
            this.msgList = sharedList;
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