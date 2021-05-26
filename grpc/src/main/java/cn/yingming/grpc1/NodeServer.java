package cn.yingming.grpc1;

import io.grpc.*;
import io.grpc.jchannelRpc.*;
import io.grpc.stub.StreamObserver;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;

import java.util.ArrayList;
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
                    /* Condition1
                       Receive the connect() request.
                     */
                    if (req.hasConnectRequest()){
                        System.out.println(req.getConnectRequest().getJchannelAddress() + "(" +
                                req.getConnectRequest().getSource() + ") joins the cluster, " +
                                req.getConnectRequest().getCluster());
                        // Store the responseObserver of joining client.
                        join(req.getConnectRequest(), responseObserver);
                        share(req);
                    /*  Condition2
                        Receive the disconnect() request.
                     */
                    } else if (req.hasDisconnectRequest()){
                        System.out.println("The client sends a disconnect() request. "
                                + req.getDisconnectRequest().getJchannelAddress() + "("
                                + req.getDisconnectRequest().getCluster() + ")");
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
                        share(req);

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
                        if (msgReq.getDestination().equals(null)||msgReq.getDestination().equals("")){
                            System.out.println("Broadcast in the cluster " + msgReq.getCluster());
                            lock.lock();
                            try{
                                // send msg to its gRPC clients
                                broadcast(msgReq);
                                // forward msg to other nodes
                                forward(msgReq);
                            }finally {
                                lock.unlock();
                            }
                        // Type2, unicast
                        } else{
                            System.out.println("Unicast in the cluster " + msgReq.getCluster() + " to " + msgReq.getDestination());
                            lock.lock();
                            try{
                                // send msg to its gRPC clients
                                unicast(msgReq);
                                // forward msg to other JChannels
                                forward(msgReq);
                            }finally {
                                lock.unlock();
                            }
                        }
                    }
                }
                // can add an automatic deleting the cancelled client.
                @Override
                public void onError(Throwable throwable) {

                    System.out.println("onError:" + throwable.getMessage() + " Remove it from the node.");
                    System.out.println(responseObserver);
                    String uuid = removeClient(responseObserver);
                    String line = "[DisconnectNotGrace] " + uuid;
                    Message msg = new ObjectMessage(null, line);
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
                        System.out.println("Found the error client, remove it from clients Map.");
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
                ClusterMap clusterObj = (ClusterMap) jchannel.serviceMap.get(req.getCluster());
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

        // Broadcast message from other nodes.
        public void broadcast(String message){
            lock.lock();
            try{
                ArrayList deleteList = new ArrayList();
                String[] strs = message.split(" ", 5);
                String clusterStr = strs[3];
                String contentStr = strs[4];
                String senderStr = strs[2];
                MessageRep msgRep = MessageRep.newBuilder()
                        .setJchannelAddress(senderStr)
                        .setContent(contentStr)
                        .build();
                Response rep = Response.newBuilder()
                        .setMessageResponse(msgRep)
                        .build();

                ClusterMap clusterObj = (ClusterMap) jchannel.serviceMap.get(clusterStr);
                for (String uuid : clients.keySet()){
                    System.out.println(uuid);
                    if (clusterObj.getMap().containsKey(uuid)){
                        try{
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
                System.out.println(rep.toString());

            } finally {
                lock.unlock();
            }
        }

        // Broadcast the messages for updating addresses of servers
        protected void broadcastServers(String message){
            ArrayList deleteList = new ArrayList();
            // set the message (from other nodes) which is broadcast to all clients.

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
                    try{
                        clients.get(u).onNext(broMsg);
                    } catch (Exception e){
                        // add
                        // remove the error detection part. move to onError()
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

        public void unicast(MessageReq req){
            String jchAdd = req.getJchannelAddress();
            String msgContent = req.getContent();
            String msgCluster = req.getCluster();
            String msgDest = req.getDestination();
            lock.lock();
            try{
                ArrayList deleteList = new ArrayList();
                // build message
                MessageRep msgRep = MessageRep.newBuilder()
                        .setJchannelAddress(jchAdd)
                        .setContent(msgContent)
                        .build();
                Response rep = Response.newBuilder()
                        .setMessageResponse(msgRep)
                        .build();
                ClusterMap clusterObj = (ClusterMap) jchannel.serviceMap.get(msgCluster);
                for (String uuid : clients.keySet()){
                    if (clusterObj.getMap().get(uuid).equals(msgDest)){
                        try{
                            // send message to the client in the same cluster, which is connecting
                            // to this node.
                            clients.get(uuid).onNext(rep);
                            System.out.println("Unicast, send message to a JChannel-Client, " + clusterObj.getMap().get(uuid));
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
                System.out.println("One unicast for message.");
                System.out.println(msgRep.toString());

            } finally {
                lock.unlock();
            }

        }

        public void unicast(String message){
            String[] msgStrs = message.split(" ", 6);
            String jchAdd = msgStrs[2];
            String msgContent = msgStrs[5];
            String msgCluster = msgStrs[3];
            String msgDest =msgStrs[4];
            lock.lock();
            try{
                ArrayList deleteList = new ArrayList();
                // build message
                MessageRep msgRep = MessageRep.newBuilder()
                        .setJchannelAddress(jchAdd)
                        .setContent(msgContent)
                        .build();
                Response rep = Response.newBuilder()
                        .setMessageResponse(msgRep)
                        .build();
                ClusterMap clusterObj = (ClusterMap) jchannel.serviceMap.get(msgCluster);
                for (String uuid : clients.keySet()){
                    if (clusterObj.getMap().get(uuid).equals(msgDest)){
                        try{
                            // send message to the client in the same cluster, which is connecting
                            // to this node.
                            clients.get(uuid).onNext(rep);
                            System.out.println("Unicast, send message to a JChannel-Client, " + clusterObj.getMap().get(uuid));
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
                System.out.println("One unicast for message.");
                System.out.println(msgRep.toString());

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

        public void broadcastView(ViewRep videRep, String cluster){
            lock.lock();
            try{
                ArrayList deleteList = new ArrayList();

                Response rep = Response.newBuilder()
                        .setViewResponse(videRep)
                        .build();
                ClusterMap clusterObj = (ClusterMap) jchannel.serviceMap.get(cluster);
                for (String uuid : clients.keySet()){
                    if (clusterObj.getMap().containsKey(uuid)){
                        try{
                            // send message to the client in the same cluster, which is connecting
                            // to this node.
                            clients.get(uuid).onNext(rep);
                            System.out.println("Send view to a JChannel-Client, " + clusterObj.getMap().get(uuid));
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
                System.out.println("One broadcast for view.");
                System.out.println(rep.toString());

            } finally {
                lock.unlock();
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