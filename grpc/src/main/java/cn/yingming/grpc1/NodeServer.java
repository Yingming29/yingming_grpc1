package cn.yingming.grpc1;

import io.grpc.*;
import io.grpc.jchannelRpc.*;
import io.grpc.stub.StreamObserver;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;

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
        System.out.println("---gRPC Server Starts.---");
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
                    /* Condition1
                       Receive the connect() request.
                     */
                    if (req.hasConnectRequest()){
                        System.out.println("[gRPC] Receive the connect() request from a client.");
                        System.out.println(req.getConnectRequest().getJchannelAddress() + "(" +
                                req.getConnectRequest().getSource() + ") joins the cluster, " +
                                req.getConnectRequest().getCluster());
                        forwardMsg(req);
                        // Store the responseObserver of the client.
                        join(req.getConnectRequest(), responseObserver);


                    /*  Condition2
                        Receive the disconnect() request.
                     */
                    } else if (req.hasDisconnectRequest()){
                        System.out.println("[gRPC] Receive the disconnect() request from a client.");
                        System.out.println(req.getDisconnectRequest().getJchannelAddress() + "(" +
                                req.getDisconnectRequest().getSource() + ") quits the cluster, " +
                                req.getDisconnectRequest().getCluster());
                        // remove responseObserver for the disconnect()
                        lock.lock();
                        try {
                            // 1. remove the client responseObserver
                            for (String uuid : clients.keySet()) {
                                if (uuid.equals(req.getDisconnectRequest().getSource())){
                                    clients.remove(uuid);
                                }
                            }
                            // 2. remove the client from its cluster information
                            jchannel.disconnectCluster(req.getDisconnectRequest().getCluster(),
                                    req.getDisconnectRequest().getJchannelAddress(),
                                    req.getDisconnectRequest().getSource());
                        } finally {
                            lock.unlock();
                        }
                        // also notify other nodes to delete it
                        forwardMsg(req);
                        onCompleted();
                    } else if (req.hasStateReq()){
                        StateReq stateReq = req.getStateReq();
                        System.out.println("[gRPC] Receive the getState() request for message history from a client.");
                        System.out.println(stateReq.getJchannelAddress() + "(" +
                                stateReq.getSource() + ") calls getState() for cluster, " +
                                stateReq.getCluster());
                        lock.lock();
                        try {
                            // generate the history
                            ClusterMap cm = (ClusterMap) jchannel.serviceMap.get(stateReq.getCluster());
                            StateRep stateRep = cm.generateState();
                            Response rep = Response.newBuilder()
                                    .setStateRep(stateRep)
                                    .build();
                            // send to this client
                            for (String uuid : clients.keySet()) {
                                if (uuid.equals(req.getStateReq().getSource())){
                                    clients.get(uuid).onNext(rep);
                                }
                            }
                        } finally {
                            lock.unlock();
                        }
                    } else{
                        /* Condition3
                           Receive the common message, send() request.
                           Two types: broadcast ot unicast
                         */
                        MessageReq msgReq = req.getMessageRequest();
                        // send() messages for broadcast and unicast in the cluster for clients
                        System.out.println("[gRPC] " + msgReq.getJchannelAddress() + " sends message: " + msgReq.getContent()
                                + " at " + msgReq.getTimestamp());
                        // Type1, broadcast
                        if (msgReq.getDestination() == null || msgReq.getDestination().equals("")){
                            System.out.println("[gRPC] Broadcast in the cluster " + msgReq.getCluster());
                            lock.lock();
                            try{
                                // add to history
                                ClusterMap cm = (ClusterMap) jchannel.serviceMap.get(msgReq.getCluster());
                                String line = "[" + msgReq.getJchannelAddress() + "]" + msgReq.getContent();
                                cm.addHistory(line);

                                // forward msg to other nodes
                                forwardMsg(req);
                                // send msg to its gRPC clients
                                broadcast(msgReq);

                            }finally {
                                lock.unlock();
                            }
                        // Type2, unicast
                        } else{
                            System.out.println("[gRPC] Unicast in the cluster " + msgReq.getCluster() + " to " + msgReq.getDestination());
                            lock.lock();
                            try{

                                // forward msg to other JChannels
                                forwardMsg(req);
                                // send msg to its gRPC clients
                                unicast(msgReq);

                            }finally {
                                lock.unlock();
                            }
                        }
                    }
                }
                // can add an automatic deleting the cancelled client.
                @Override
                public void onError(Throwable throwable) {

                    System.out.println("[gRPC] onError:" + throwable.getMessage() + " Remove it from the node.");
                    System.out.println(responseObserver);
                    String uuid = removeClient(responseObserver);
                    String line = "[DisconnectNotGrace] " + uuid;
                    Message msg = new ObjectMessage(null, line);
                    msg.setFlagIfAbsent(Message.TransientFlag.DONT_LOOPBACK);
                    try {
                        jchannel.channel.send(msg);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onCompleted() {
                    responseObserver.onCompleted();
                }
            };
        }

        protected String removeClient(StreamObserver responseObserver){

            this.lock.lock();
            try{
                for (String uuid:clients.keySet()) {
                    if (clients.get(uuid) == responseObserver){
                        clients.remove(uuid);
                        System.out.println("[gRPC] Found the error client, remove it from clients Map.");
                        jchannel.disconnectClusterNoGraceful(uuid);
                        return uuid;
                    }

                }
            } finally {
                this.lock.unlock();
            }
            return null;
        }

        /* The unary rpc, the response of ask rpc call method for the try connection from clients.
         */
        public void ask(ReqAsk req, StreamObserver<RepAsk> responseObserver){
            System.out.println("[gRPC] Receive an ask request for reconnection from " + req.getSource());
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
                MessageRep msgRep = MessageRep.newBuilder()
                        .setJchannelAddress(req.getJchannelAddress())
                        .setContent(req.getContent())
                        .build();
                Response rep = Response.newBuilder()
                        .setMessageResponse(msgRep)
                        .build();
                ClusterMap clusterObj = (ClusterMap) jchannel.serviceMap.get(req.getCluster());
                for (String uuid : clients.keySet()){
                    if (clusterObj.getMap().containsKey(uuid)){
                        clients.get(uuid).onNext(rep);
                        System.out.println("[gRPC] Send message to a JChannel-Client, " + clusterObj.getMap().get(uuid));
                    }
                }
                System.out.println("One broadcast for message successfully.");
                System.out.println(msgRep.toString());

            } finally {
                lock.unlock();
            }
        }

        // Broadcast the messages for updating addresses of servers
        protected void broadcastServers(String message){

            UpdateRep updateMsg = UpdateRep.newBuilder()
                    .setAddresses(message)
                    .build();
            Response broMsg = Response.newBuilder()
                    .setUpdateResponse(updateMsg)
                    .build();
            lock.lock();
            try{
                // Iteration of StreamObserver for broadcast message.
                for (String u : clients.keySet()){
                    clients.get(u).onNext(broMsg);
                }

            } finally {
                lock.unlock();
            }
        }

        public void unicast(MessageReq req){
            String jchAdd = req.getJchannelAddress();
            String msgContent = req.getContent();
            String msgCluster = req.getCluster();
            String msgDest = req.getDestination();
            lock.lock();
            try{
                // build message
                MessageRep msgRep = MessageRep.newBuilder()
                        .setJchannelAddress(jchAdd)
                        .setContent(msgContent)
                        .build();
                Response rep = Response.newBuilder()
                        .setMessageResponse(msgRep)
                        .build();

                ClusterMap clusterObj = (ClusterMap) jchannel.serviceMap.get(msgCluster);
                System.out.println("----------");
                System.out.println(clusterObj.getMap());
                System.out.println(msgDest);
                System.out.println("----------");
                for (String uuid : clients.keySet()){
                    if (clusterObj.getMap().containsKey(uuid)){
                        if (clusterObj.getMap().get(uuid).equals(msgDest)){
                            clients.get(uuid).onNext(rep);
                            System.out.println("[gRPC] Send message to a JChannel-Client, " + clusterObj.getMap().get(uuid));
                        } else{
                            System.out.println("error");
                        }
                    }
                }
                System.out.println("One unicast for message successfully.");
                System.out.println(msgRep.toString());

            } finally {
                lock.unlock();
            }

        }

        public void broadcastView(ViewRep videRep, String cluster){
            lock.lock();
            try{
                Response rep = Response.newBuilder()
                        .setViewResponse(videRep)
                        .build();
                ClusterMap clusterObj = (ClusterMap) jchannel.serviceMap.get(cluster);
                for (String uuid : clients.keySet()){
                    if (clusterObj.getMap().containsKey(uuid)){
                        clients.get(uuid).onNext(rep);
                        System.out.println("Send view to a JChannel-Client, " + clusterObj.getMap().get(uuid));
                    }
                }
                System.out.println("One broadcast for view successfully.");
                System.out.println(rep.toString());

            } finally {
                lock.unlock();
            }
        }

        protected void forwardMsg(Request req){
            byte[] b =  req.toByteArray();
            Message msg = new ObjectMessage(null, b);
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