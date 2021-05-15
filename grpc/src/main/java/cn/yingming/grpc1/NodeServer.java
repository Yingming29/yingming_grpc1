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

// The node is given with a gRPC server and a JChannel.
public class NodeServer {
    //  Port and server object of grpc server
    private int port;
    private Server server;
    //  Node name, cluster name, JChannel of node
    String nodeName;
    String jClusterName;
    NodeJChannel jchannel;

    CommunicateImpl gRPCservice;
    // <no, ip>, it stores all ip address for clients, who are connecting to this server. not useful.
    private ConcurrentHashMap<Integer, String> ips;
    public NodeServer(int port, String nodeName, String jClusterName) throws Exception {
        // port, name, and cluster name of this node
        this.port = port;
        this.nodeName = nodeName;
        this.jClusterName = jClusterName;
        // not useful, store clients address.
        this.ips = new ConcurrentHashMap<>();
        // shared
        // create JChannel given node name and cluster name
        this.jchannel = new NodeJChannel(nodeName, jClusterName);
        // create grpc server, and its service is given the jchannel for calling send() method on jchannel.
        this.gRPCservice = new CommunicateImpl(this.jchannel);
        this.server = ServerBuilder.forPort(port)
                .addService(this.gRPCservice)
                .intercept(new ClientAddInterceptor())
                .build();
    }

    // Start grpc
    private void start() throws Exception {
        // Give the entry of gRPC for calling broadcast().
        this.giveEntry(this.gRPCservice);
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

    // After creation of gRPC, give the service object to Jchannel for calling broadcast().
    public void giveEntry(CommunicateImpl gRPCservice){
        // set service method of JChannel.
        this.jchannel.setService(gRPCservice);
    }
    // gRPC service
    class CommunicateImpl extends CommunicateGrpc.CommunicateImplBase {
        // HashMap for storing the clients, includes uuid and StreamObserver.
        private final ConcurrentHashMap<String, StreamObserver<StreamResponse>> clients = new ConcurrentHashMap<>();
        protected final ReentrantLock lock = new ReentrantLock();
        protected final NodeJChannel jchannel;

        // Constructor with JChannel for calling send() method.
        private CommunicateImpl(NodeJChannel jchannel) throws Exception {
            this.jchannel = jchannel;
        }

        public StreamObserver<StreamRequest> createConnection(StreamObserver<StreamResponse> responseObserver){
            return new StreamObserver<StreamRequest>() {
                @Override
                public void onNext(StreamRequest streamRequest) {
                    if (streamRequest.getJoin()){ // true, get join request
                        System.out.println(streamRequest.getName() + "(" +
                                streamRequest.getSource() + ") joins the chat.");
                        // Treat the responseObserver of joining client.
                        join(streamRequest, responseObserver);
                    } else if (streamRequest.getQuit()){
                        System.out.println("The client sends a quit request. " + streamRequest.getName() + "(" +
                                        streamRequest.getSource() + ")");
                        // quit request.
                        lock.lock();
                        try {
                            for (String uuid : clients.keySet()) {
                                if (uuid.equals(streamRequest.getSource())){
                                    clients.remove(uuid);
                                }
                            }
                        } finally {
                            lock.unlock();
                        }
                    } else{
                        // common message for broadcast
                        System.out.println("[gRPC] " + streamRequest.getName() + " sends message: " + streamRequest.getMessage()
                                + " at " + streamRequest.getTimestamp());
                        lock.lock();
                        try{
                            // broadcast msg to gRPC clients
                            broadcast(streamRequest);
                            // forward msg to other JChannels
                            forward(streamRequest);
                        }finally {
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

        // the response of ask rpc call method for try connection from clients.
        public void ask(ReqAsk req, StreamObserver<RepAsk> responseObserver){
            System.out.println("Receive an ask request for reconnection from " + req.getSource());
            RepAsk askMsg = RepAsk.newBuilder().setSurvival(true).build();
            responseObserver.onNext(askMsg);
            responseObserver.onCompleted();
        }

        protected void join(StreamRequest req, StreamObserver<StreamResponse> responseObserver){
            // 1. get lock
            lock.lock();
            // 2. critical section, for Map<> clients.
            try{
                // add a new client to Map<uuid, responseObserver>
                clients.put(req.getSource(), responseObserver);
                Date d = new Date();
                SimpleDateFormat dft = new SimpleDateFormat("hh:mm:ss");
                StreamResponse joinResponse = StreamResponse.newBuilder()
                        .setName(nodeName)
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

        // Broadcast messages from its clients.
        protected void broadcast(StreamRequest req){
            ArrayList deleteList = new ArrayList();
            String name = req.getName();
            String msg = req.getMessage();
            String timeStr = req.getTimestamp();
            StreamResponse broMsg = StreamResponse.newBuilder()
                    .setName(name)
                    .setMessage(msg)
                    .setTimestamp(timeStr)
                    .build();
            for (String u : clients.keySet()){
                try{
                    clients.get(u).onNext(broMsg);
                } catch (Exception e){
                    e.printStackTrace();
                    deleteList.add(u);
                    System.out.println("Found a client not working. Delete it.");
                }
            }
            if(deleteList.size() != 0){
                for (int i = 0; i < deleteList.size(); i++) {
                    clients.remove(deleteList.get(i));
                    System.out.println("Delete a client responseObserver from Map.");
                }
            } else{
                System.out.println("All clients are available.");
            }
            System.out.println("One broadcast for message.");
            System.out.println(broMsg.toString());

        }
        // Broadcast the messages from other nodes.
        protected void broadcast(String message){
            //
            ArrayList deleteList = new ArrayList();
            // set the message (from other nodes) which is broadcast to all clients.
            String[] msg = message.split("\t");
            StreamResponse broMsg = StreamResponse.newBuilder()
                    .setName(msg[0])
                    .setMessage(msg[1])
                    .setTimestamp(msg[2])
                    .build();
            // Iteration of StreamObserver for broadcast message.
            for (String u : clients.keySet()){
                try{
                    clients.get(u).onNext(broMsg);
                } catch (Exception e){
                    e.printStackTrace();
                    deleteList.add(u);
                    System.out.println("Found a client not working. Delete it.");
                }
            }
            if(deleteList.size() != 0){
                for (int i = 0; i < deleteList.size(); i++) {
                    clients.remove(deleteList.get(i));
                    System.out.println("Delete a client responseObserver from Map.");
                }
            } else{
                System.out.println("All clients are available.");
            }
            System.out.println("One broadcast for message from other nodes.");
            System.out.println(broMsg.toString());
        }
        // Send the message to other JChannels
        protected void forward(StreamRequest req){
            String strMsg = Utils.streamToStrMsg(req);
            Message msg = new ObjectMessage(null, strMsg);
            // send messages exclude itself.
            msg.setFlagIfAbsent(Message.TransientFlag.DONT_LOOPBACK);
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
            System.out.println("Found request from IP address: " + ip);
            ips.put(ips.size(), ip);
            return next.startCall(call, headers);
        }
    }

    public static void main(String[] args) throws Exception {
        // Port, NodeName, ClusterName
        final NodeServer server = new NodeServer(Integer.parseInt(args[0]), args[1], args[2]);
        System.out.printf("Inf: %s %s %s \n",args[0], args[1], args[2]);
        // start gRPC service
        server.start();
        server.blockUntilShutdown();
    }
}