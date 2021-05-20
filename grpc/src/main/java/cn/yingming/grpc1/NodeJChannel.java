package cn.yingming.grpc1;

import org.apache.commons.collections.ListUtils;
import org.jgroups.*;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class NodeJChannel implements Receiver{
    JChannel channel;
    String user_name;
    String node_name;
    String cluster_name;
    ReentrantLock lock;
    NodeServer.CommunicateImpl service;
    String grpcAddress;
    ConcurrentHashMap nodesMap;

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
        // put itself into available nodes list
        this.nodesMap.put(this.channel.getAddress(), this.grpcAddress);
        System.out.println(this.nodesMap);
    }

    @Override
    public void receive(Message msg) {
        String msgStr = msg.getObject();
        String newMsg = null;
        // two types of broadcast message, update address or broadcast the common message
        synchronized (this.nodesMap){
            if (msgStr.startsWith("grpcAddress:")){
                String[] strs = msgStr.split(":", 2);
                boolean same = false;
                for (Object add : this.nodesMap.keySet()) {
                    if (add.toString().equals(msg.getSrc().toString())&&this.nodesMap.get(add).equals(strs[1])){
                        same = true;
                    }
                }
                if (same){
                    System.out.println("Receive a confirmation from a node, but no change.");
                } else{
                    this.nodesMap.put(msg.getSrc(), strs[1]);
                    System.out.println("Receive a confirmation from a node, update map.");
                    System.out.println("After receiving: " + this.nodesMap);
                    String str = generateAddMsg();
                    newMsg = str;
                    this.service.broadcast(newMsg);
                }
            } else {
                // Broadcast the common message
                String line = msg.getSrc() + ": " + msg.getObject();
                System.out.println("[JChannel] " + line);
                newMsg = msg.getObject().toString();
                this.service.broadcast(newMsg);
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

    public void setService(NodeServer.CommunicateImpl gRPCservice){
        this.service = gRPCservice;
    }

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

                this.service.broadcast(str);
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
}
