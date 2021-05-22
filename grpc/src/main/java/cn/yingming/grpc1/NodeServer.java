package cn.yingming.grpc1;

import io.grpc.*;
import io.grpc.jchannelRpc.*;
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
    JChannelsServiceImpl gRPCservice;
    // <no, ip>, it stores all ip address for clients, who are connecting to this server. not useful.
    private ConcurrentHashMap<Integer, String> ips;
    public NodeServer(int port, String nodeName, String jClusterName) throws Exception {
        // port, name, and cluster name of this node
        this.port = port;
        this.nodeName = nodeName;
        this.jClusterName = jClusterName;
        // not useful, store clients address.
        this.ips = new ConcurrentHashMap<>();
        // create JChannel given node name and cluster name
        this.jchannel = new NodeJChannel(nodeName, jClusterName, "127.0.0.1:" + port);
        // create grpc server, and its service is given the jchannel for calling send() method on jchannel.
        this.gRPCservice = new JChannelsServiceImpl(this.jchannel);
        this.server = ServerBuilder.forPort(port)
                .addService(this.gRPCservice)  // service for bidirectional streaming
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
    public void giveEntry(JChannelsServiceImpl gRPCservice){
        // set service method of JChannel.
        this.jchannel.setService(gRPCservice);
    }
    // gRPC service
    class JChannelsServiceImpl extends JChannelsServiceGrpc.JChannelsServiceImplBase {
        // HashMap for storing the clients, includes uuid and StreamObserver.
        private final ConcurrentHashMap<String, StreamObserver<Response>> clients = new ConcurrentHashMap<String, StreamObserver<Response>>();
        protected final ReentrantLock lock = new ReentrantLock();
        NodeJChannel jchannel;

        // Constructor with JChannel for calling send() method.
        private JChannelsServiceImpl(NodeJChannel jchannel) throws Exception {
            this.jchannel = jchannel;
        }

        // service 1, bi-directional streaming rpc
        public StreamObserver<Request> connect(StreamObserver<Response> responseObserver){
            return new StreamObserver<Request>() {
                @Override
                public void onNext(Request req) {
                    if (req.hasConnectRequest()){
                        // connect()
                        System.out.println(req.getConnectRequest().getJchannelAddress() + "(" +
                                req.getConnectRequest().getSource() + ") joins the cluster, " +
                                req.getConnectRequest().getCluster());
                        // Store the responseObserver of joining client.
                        join(req.getConnectRequest(), responseObserver);
                        share(req);
                    } else if (req.hasDisconnectRequest()){
                        // disconnect()
                        System.out.println("The client sends a disconnect() request. "
                                + req.getDisconnectRequest().getJchannelAddress() + "("
                                + req.getDisconnectRequest().getCluster() + ")");
                        // remove responseObserver for the disconnect()
                        lock.lock();
                        try {
                            // remove the client responseObserver
                            for (String uuid : clients.keySet()) {
                                if (uuid.equals(req.getDisconnectRequest().getSource())){
                                    clients.remove(uuid);
                                }
                            }
                            // remove the client from its cluster
                            jchannel.disconnectCluster(req.getDisconnectRequest().getCluster(),
                                    req.getDisconnectRequest().getJchannelAddress(),
                                    req.getDisconnectRequest().getSource());
                        } finally {
                            lock.unlock();
                        }
                        share(req);

                    } else{
                        // treat the common message
                        MessageReq msgReq = req.getMessageRequest();
                        // send() messages for broadcast and unicast in the cluster for clients
                        System.out.println("[gRPC] " + msgReq.getJchannelAddress() + " sends message: " + msgReq.getContent()
                                + " at " + msgReq.getTimestamp());
                        if (msgReq.getDestination().equals(null)||msgReq.getDestination().equals("")){
                            System.out.println("Broadcast in the cluster " + msgReq.getCluster());
                        } else{
                            System.out.println("Unicast in the cluster " + msgReq.getCluster() + " to " + msgReq.getDestination());
                        }
                        lock.lock();
                        try{
                            // send msg to its gRPC clients
                            broadcast(msgReq);
                            // forward msg to other JChannels
                            forward(msgReq);
                        }finally {
                            lock.unlock();
                        }
                    }
                }
                // can add an automatic deleting the cancelled client.
                @Override
                public void onError(Throwable throwable) {

                    System.out.println("onError:" + throwable.getMessage());
                    System.out.println(responseObserver);
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }

        /* The unary rpc, the response of ask rpc call method for the try connection from clients.
         */
        public void ask(ReqAsk req, StreamObserver<RepAsk> responseObserver){
            System.out.println("Receive an ask request for reconnection from " + req.getSource());
            RepAsk askMsg = RepAsk.newBuilder().setSurvival(true).build();
            responseObserver.onNext(askMsg);
            responseObserver.onCompleted();
        }
        // add view
        protected void join(ConnectReq req, StreamObserver<Response> responseObserver){
            // 1. get lock
            lock.lock();
            // 2. critical section,
            try{
                /* add a new client to Map<uuid, responseObserver>
                 return a connect response
                 */
                clients.put(req.getSource(), responseObserver);
                // String cluster, String JChannel_address, String uuid
                // <cluster, <uuid, JChanner_address>>
                jchannel.connectCluster(req.getCluster(), req.getJchannelAddress(), req.getSource());
                // return response for result.
                ConnectRep joinResponse = ConnectRep.newBuilder()
                        .setResult(true)
                        .build();
                Response rep = Response.newBuilder()
                        .setConnectResponse(joinResponse)
                        .build();
                responseObserver.onNext(rep);
                // return response for updating the available servers
                UpdateRep updateRep = UpdateRep.newBuilder()
                        .setAddresses(this.jchannel.generateAddMsg())
                        .build();
                Response rep2 = Response.newBuilder()
                        .setUpdateResponse(updateRep)
                        .build();
                responseObserver.onNext(rep2);
            }
            // 3. run finally, confirm the lock will be unlock.
            finally {
                // remember unlock
                lock.unlock();
            }
        }

        // Broadcast messages from its clients.
        public void broadcast(MessageReq req){
            lock.lock();
            try{
                ArrayList deleteList = new ArrayList();
                MessageRep msgRep = MessageRep.newBuilder()
                        .setJchannelAddress(req.getJchannelAddress())
                        .setContent(req.getContent())
                        .build();
                Response rep = Response.newBuilder()
                        .setMessageResponse(msgRep)
                        .build();
                NodeJChannel.ClusterMap clusterObj = (NodeJChannel.ClusterMap) jchannel.serviceMap.get(req.getCluster());
                for (String uuid : clients.keySet()){
                    if (clusterObj.getMap().containsKey(uuid)){
                        try{
                            // send message to the client in the same cluster, which is connecting
                            // to this node.
                            clients.get(uuid).onNext(rep);
                            System.out.println("Send message to a JChannel-Client, " + clusterObj.getMap().get(uuid));
                        } catch (Exception e){
                            e.printStackTrace();
                            deleteList.add(uuid);
                            System.out.println("Found a client not working. Delete it from .");
                        }
                    }
                }
                if(deleteList.size() != 0){
                    for (int i = 0; i < deleteList.size(); i++) {
                        clients.remove(deleteList.get(i));
                        // add, remove the uuid and JChannel_address key-value from the cluster map
                        clusterObj.getMap().remove(deleteList.get(i));
                        System.out.println("Delete a client.");
                    }
                }
                System.out.println("One broadcast for message.");
                System.out.println(msgRep.toString());

            } finally {
                lock.unlock();
            }
        }
        // Broadcast the messages from other nodes or update addresses of servers
        protected void broadcast(String message){
            lock.lock();
            try{
                Response broMsg = null;
                ArrayList deleteList = new ArrayList();
                // set the message (from other nodes) which is broadcast to all clients.
                String[] msg = message.split("\t");

                if (!message.contains("\t")){
                    broMsg = ConnectRep.newBuilder()
                            .setName(nodeName)
                            .setAddresses(message)
                            .build();
                    System.out.println("One broadcast for updated addresses:");
                    System.out.println(broMsg.toString());
                } else{
                    broMsg = ConnectRep.newBuilder()
                            .setName(msg[0])
                            .setMessage(msg[1])
                            .setTimestamp(msg[2])
                            .build();
                    System.out.println("One broadcast for message from other nodes.");
                    System.out.println(broMsg.toString());
                }

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

            } finally {
                lock.unlock();
            }
        }

        // Send the message to other JChannels
        // common message
        protected void forward(MessageReq req){
            String strMsg = Utils.msgReqToMsgStr(req);
            Message msg = new ObjectMessage(null, strMsg);
            // send messages exclude itself.
            msg.setFlagIfAbsent(Message.TransientFlag.DONT_LOOPBACK);
            try {
                this.jchannel.channel.send(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

        // share the cluster information to other nodes.
        protected void share(Request req){
            String msgStr = Utils.conDisReqToStrMsg(req);
            Message msg = new ObjectMessage(null, msgStr);
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