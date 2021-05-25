package cn.yingming.grpc1;

import io.grpc.jchannelRpc.ViewRep;
import org.apache.commons.collections.ListUtils;
import org.jgroups.*;
import org.jgroups.util.ByteArray;

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
        System.out.println(this.nodesMap);
    }

    @Override
    public void receive(Message msg) {
        System.out.println("Receive!!!");
        System.out.println(msg);
        System.out.println(msg.getObject().toString());
        System.out.println(msg.getPayload().toString());
        if (msg.getObject() instanceof String ){
            System.out.println("call receiveString");
            receiveString(msg);
        } else {
            System.out.println("call receiveByte");
            System.out.println("call receiveByte");
            System.out.println("call receiveByte");
            System.out.println("call receiveByte");
            receiveByte(msg);
        }
    }

    public void receiveByte(Message msg){
        Object obj =  Utils.unserializeClusterInf(msg.getPayload());
        System.out.println("receivebyte");
        if (obj instanceof Map){
            System.out.println("Receive the cluster information from node " + msg.getSrc());
            ConcurrentHashMap m = (ConcurrentHashMap) obj;
            lock.lock();
            try{
                this.serviceMap = m;
            } finally {
                lock.unlock();
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
                    System.out.println("Receive a confirmation from a node, but no change.");
                } else{
                    // condition 1.2 changed server list, update list and broadcast update servers
                    this.nodesMap.put(msg.getSrc(), strs[1]);
                    System.out.println("Receive a confirmation from a node, update map.");
                    System.out.println("After receiving: " + this.nodesMap);
                    String str = generateAddMsg();
                    newMsg = str;
                    this.service.broadcastServers(newMsg);
                }
                // condition 2, connect() request
            } else if (msgStr.startsWith("[Connect]")){
                // Treat the shared connect() request from other nodes for cluster information.
                // a. Add the client to its cluster map
                String[] strs = msgStr.split(" ");
                System.out.println("[JChannel] Receive a shared connect() request for updating th cluster information.");
                connectCluster(strs[3], strs[2], strs[1]);

                // condition 3, disconnect()
            } else if (msgStr.startsWith("[Disconnect]")){
                // Treat the shared connect() request from other nodes for cluster information.
                String[] strs = msgStr.split(" ");
                System.out.println("[JChannel] Receive a shared disconnect() request for updating th cluster information.");
                disconnectCluster(strs[3], strs[2], strs[1]);

            } else if (msgStr.startsWith("[Broadcast]")){
                System.out.println("[JChannel] Receive a shared send() request for broadcast to JChannl-Clients.");
                lock.lock();
                try{
                    this.service.broadcast(msgStr);
                } finally {
                    lock.unlock();
                }
            } else if (msgStr.startsWith("[Unicast]")){
                // When it receive an unicast message from other nodes, it will find the
                // correct client for it, and send the message.
                System.out.println("[JChannel] Receive a shared send() request for unicast to a JChannl-Client.");
                this.service.unicast(msgStr);
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
                System.out.println("This is the coordinator");
            } else {
                String msg = "ClusterInformation";
                try{
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
                System.out.println("Add new node inf.");
                List compare = ListUtils.subtract(currentView, currentNodesList);
                for (int i = 0; i < compare.size(); i++) {
                    this.nodesMap.put(compare.get(i), "unknown");
                }
                System.out.println("The current nodes map: " + this.nodesMap);
                sendMyself();
            } else if (currentView.size() < currentNodesList.size()) {
                System.out.println("Remove node inf.");
                List compare = ListUtils.subtract(currentNodesList, currentView);
                for (int i = 0; i < compare.size(); i++) {
                    this.nodesMap.remove(compare.get(i));
                }
                System.out.println("The current nodes map: " + this.nodesMap);
                String str = generateAddMsg();

                this.service.broadcastServers(str);
            } else {
                System.out.println("No change of node inf.");
            }
        }
    }

    public void sendMyself(){
        // send the address of its gRPC server address/port
        Message msg = new ObjectMessage(null, "grpcAddress:" + this.grpcAddress);
        // send messages exclude itself.
        msg.setFlagIfAbsent(Message.TransientFlag.DONT_LOOPBACK);
        try{
            System.out.println("Send itself.");
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
            } else{
                System.out.println(JChannel_address + " connects to a new cluster: " + cluster);
                // create new cluster object and set it as the creator
                ClusterMap clusterObj = new ClusterMap(JChannel_address);
                clusterObj.getMap().put(uuid, JChannel_address);
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
                m.getMap().remove(uuid);
                System.out.println(JChannel_address + " quits " + cluster);
            }
            ClusterMap clusterObj = (ClusterMap) serviceMap.get(cluster);
            ViewRep viewRep= clusterObj.generateView();
            this.service.broadcastView(viewRep, cluster);
        } finally {
            lock.unlock();
        }
    }

}
