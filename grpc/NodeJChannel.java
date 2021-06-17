package cn.yingming.grpc1;

import io.grpc.jchannelRpc.*;
import org.apache.commons.collections.ListUtils;
import org.jgroups.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class NodeJChannel implements Receiver{
    JChannel channel;
    String user_name;
    String node_name;
    String cluster_name;
    ReentrantLock lock;
    NodeServer.JChannelsServiceImpl service;
    String grpcAddress;
    ConcurrentHashMap nodesMap;
    ConcurrentHashMap serviceMap;

    NodeJChannel(String node_name, String cluster_name, String grpcAddress) throws Exception {

        this.channel = new JChannel("grpc/protocols/udp.xml");
        this.user_name = System.getProperty("user.name", "n/a");
        this.node_name = node_name;
        this.cluster_name = cluster_name;
        this.grpcAddress = grpcAddress;
        this.nodesMap = new ConcurrentHashMap<>();
        this.channel.setReceiver(this).connect(cluster_name);
        this.lock = new ReentrantLock();
        this.service = null;
        this.serviceMap = new ConcurrentHashMap<String, ClusterMap>();
        // put itself into available nodes list
        this.nodesMap.put(this.channel.getAddress(), this.grpcAddress);
        System.out.println("[JChannel] The current nodes in node cluster: " + this.nodesMap);
    }

    @Override
    public void receive(Message msg) {
        if (msg.getObject() instanceof String ){
            // System.out.println("call receiveString");
            receiveString(msg);
        } else {
            // System.out.println("call receiveByte");
            receiveByte(msg);
        }
    }

    public void receiveByte(Message msg){
        Object obj =  Utils.unserializeClusterInf(msg.getPayload());

        if (obj instanceof Map){
            System.out.println("Receive the cluster information from node(coordinator) " + msg.getSrc());
            ConcurrentHashMap m = (ConcurrentHashMap) obj;
            lock.lock();
            try{
                this.serviceMap = m;
            } finally {
                lock.unlock();
            }
        } else if (obj == null){
            Request req = null;
            try{
                req = Request.parseFrom((byte[]) msg.getPayload());
            } catch (Exception e){
                e.printStackTrace();
            }
            if (req.hasConnectRequest()){
                ConnectReq conReq = req.getConnectRequest();
                System.out.println("[JChannel] Receive a shared connect() request for updating th cluster information.");
                connectCluster(conReq.getCluster(), conReq.getJchannelAddress(), conReq.getSource());
            } else if (req.hasDisconnectRequest()){
                DisconnectReq disReq = req.getDisconnectRequest();
                System.out.println("[JChannel] Receive a shared disconnect() request for updating th cluster information.");
                disconnectCluster(disReq.getCluster(), disReq.getJchannelAddress(), disReq.getSource());
            } else if (req.hasMessageRequest()){
                MessageReq msgReq = req.getMessageRequest();
                if (msgReq.getDestination().equals(null)||msgReq.getDestination().equals("")){
                    System.out.println("[JChannel] Receive a shared send() request for broadcast to JChannl-Clients.");
                    lock.lock();
                    try{
                        ClusterMap cm = (ClusterMap) serviceMap.get(msgReq.getCluster());
                        String line = "[" + msgReq.getJchannelAddress() + "]" + msgReq.getContent();
                        cm.addHistory(line);
                        this.service.broadcast(msgReq);
                    } finally {
                        lock.unlock();
                    }
                } else {
                    System.out.println("[JChannel] Receive a shared send() request for unicast to a JChannl-Client.");
                    this.service.unicast(msgReq);
                }
            }
        }
    }

    public void receiveString(Message msg){
        String msgStr = msg.getObject();
        String newMsg = null;
        // some types of broadcast messages, update address or broadcast the common message
        synchronized (this.nodesMap){
            // condition 1, update server messgage
            if (msgStr.startsWith("grpcAddress:")){
                String[] strs = msgStr.split(":", 2);
                boolean same = false;
                for (Object add : this.nodesMap.keySet()) {
                    if (add.toString().equals(msg.getSrc().toString())&&this.nodesMap.get(add).equals(strs[1])){
                        same = true;
                    }
                }
                // condition 1.1, no change
                if (same){
                    System.out.println("[JChannel] Receive a confirmation from a node, but no change.");
                } else{
                    // condition 1.2 changed server list, update list and broadcast update servers
                    this.nodesMap.put(msg.getSrc(), strs[1]);
                    System.out.println("[JChannel] Receive a confirmation from a node, update server map.");
                    System.out.println("[JChannel] After updating: " + this.nodesMap);
                    String str = generateAddMsg();
                    newMsg = str;
                    this.service.broadcastServers(newMsg);
                }
                // condition 2, connect() request
            } else if (msgStr.equals("ClusterInformation")){
                // send the current
                System.out.println("Receive a request for the current " +
                        "JChannel-client cluster information from a new node member: " + msg.getSrc());
                lock.lock();
                try{
                    byte[] b = Utils.serializeClusterInf(this.serviceMap);
                    System.out.println(b);
                    Message msg2 = new ObjectMessage(msg.getSrc(), b);
                    this.channel.send(msg2);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    this.lock.unlock();
                }
            } else if (msgStr.startsWith("[DisconnectNotGrace]")){
                String[] strs = msgStr.split(" ");
                System.out.println("[JChannel] Receive a shared not graceful disconnect() request for updating th cluster information.");
                disconnectClusterNoGraceful(strs[1]);
            }
        }
    }

    public String generateAddMsg(){
        StringBuilder sb = new StringBuilder();
        synchronized (this.nodesMap){
            for (Object s:this.nodesMap.keySet()) {
                sb.append(this.nodesMap.get(s)).append(" ");
            }
            String str = sb.toString().trim();
            return str;
        }
    }

    public void setService(NodeServer.JChannelsServiceImpl gRPCservice){
        this.service = gRPCservice;
    }


    // update the view of nodes
    @Override
    public void viewAccepted(View new_view) {
        System.out.println("** view: " + new_view);
        /* When the view is changed by any action, it will send its address to other jchannels
        and update its nodesList.
         */
        // compare keySet of nodesList with view list.
        List currentView = new_view.getMembers();
        List currentNodesList = new ArrayList<>(this.nodesMap.keySet());
        compareNodes(currentView, currentNodesList);
        checkClusterMap(new_view);
    }

    public void checkClusterMap(View view){
        // this first startup
        // whether is the coordinator
        if (this.serviceMap == null){
            if (view.getMembers().get(0).toString().equals(this.channel.getAddress().toString())){
                System.out.println("[JChannel] This is the coordinator of the node cluster.");
            } else {
                String msg = "ClusterInformation";
                try{
                    // send the request to get the current inf of client-jchannel cluster
                    this.channel.send(view.getMembers().get(0), msg);
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    public void compareNodes(List currentView, List currentNodesList){
        // add node
        synchronized (this.nodesMap) {
            if (currentView.size() > currentNodesList.size()) {
                System.out.println("[JChannel] Store new node inf.");
                List compare = ListUtils.subtract(currentView, currentNodesList);
                for (int i = 0; i < compare.size(); i++) {
                    this.nodesMap.put(compare.get(i), "unknown");
                }
                System.out.println("[JChannel] The current nodes in node cluster: " + this.nodesMap);
                sendMyself();
            } else if (currentView.size() < currentNodesList.size()) {
                System.out.println("[JChannel] Remove cancelled node inf.");
                List compare = ListUtils.subtract(currentNodesList, currentView);
                for (int i = 0; i < compare.size(); i++) {
                    this.nodesMap.remove(compare.get(i));
                }
                System.out.println("[JChannel] The current nodes in node cluster: " + this.nodesMap);
                String str = generateAddMsg();

                this.service.broadcastServers(str);
            } else {
                System.out.println("[JChannel] The current nodes does not change.");
            }
        }
    }

    public void sendMyself(){
        // send the address of its gRPC server address/port
        Message msg = new ObjectMessage(null, "grpcAddress:" + this.grpcAddress);
        // send messages exclude itself.
        msg.setFlagIfAbsent(Message.TransientFlag.DONT_LOOPBACK);
        try{
            System.out.println("[JChannel] Send the grpc address of my self.");
            this.channel.send(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    // add view action and forward
    public void connectCluster(String cluster, String JChannel_address, String uuid){
        lock.lock();
        try{
            // create the cluster or connect an existing cluster.
            if (serviceMap.containsKey(cluster)){
                System.out.println(JChannel_address + " connects to the existing cluster: " + cluster);
                ClusterMap clusterObj = (ClusterMap) serviceMap.get(cluster);
                clusterObj.getMap().put(uuid, JChannel_address);
                clusterObj.getList().add(JChannel_address);
            } else{
                System.out.println(JChannel_address + " connects to a new cluster: " + cluster);
                // create new cluster object and set it as the creator
                ClusterMap clusterObj = new ClusterMap(JChannel_address);
                clusterObj.getMap().put(uuid, JChannel_address);
                clusterObj.getList().add(JChannel_address);
                // put into serviceMap
                serviceMap.put(cluster, clusterObj);
            }
            ClusterMap clusterObj = (ClusterMap) serviceMap.get(cluster);
            ViewRep viewRep= clusterObj.generateView();
            this.service.broadcastView(viewRep, cluster);
        }catch (Exception e){
            e.printStackTrace();
        } finally {
            lock.unlock();
        }
    }
    // add view action and forward
    public void disconnectCluster(String cluster, String JChannel_address, String uuid){
        lock.lock();
        try{
            ClusterMap m = (ClusterMap) serviceMap.get(cluster);
            if (m.getMap().size() == 1 && m.getMap().get(uuid).equals(JChannel_address)){
                serviceMap.remove(cluster);
                System.out.println("The last JChannel-Client quits and deletes the cluster, " + cluster);
            } else if (m.getMap().size() > 1){
                m.removeClient(uuid);
                m.getMap().remove(uuid);
                System.out.println(JChannel_address + " quits " + cluster);
                ClusterMap clusterObj = (ClusterMap) serviceMap.get(cluster);
                ViewRep viewRep= clusterObj.generateView();
                this.service.broadcastView(viewRep, cluster);
            }

        } finally {
            lock.unlock();
        }
    }

    public void disconnectClusterNoGraceful(String uuid){
        this.lock.lock();
        try{
            for (Object cluster: serviceMap.keySet()) {
                String clusterName = cluster.toString();
                ClusterMap clusterMap = (ClusterMap) serviceMap.get(clusterName);
                for (Object eachUuid:clusterMap.getMap().keySet()) {
                    if (uuid.equals(eachUuid.toString())){
                        System.out.println("No grace, Remove the JChannel-client from its cluster.");
                        clusterMap.removeClient(uuid);
                        clusterMap.getMap().remove(uuid);

                        ClusterMap clusterObj = (ClusterMap) serviceMap.get(clusterName);
                        if (clusterObj.getMap().size() == 0){
                            serviceMap.remove(cluster);
                            System.out.println("The last JChannel-Client quits and deletes the cluster, " + cluster);
                        } else{
                            ViewRep viewRep= clusterObj.generateView();
                            this.service.broadcastView(viewRep, clusterName);
                        }
                    }

                }
            }
        } finally {
            this.lock.unlock();
        }

    }
}
