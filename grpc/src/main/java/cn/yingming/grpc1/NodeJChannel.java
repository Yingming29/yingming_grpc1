package cn.yingming.grpc1;

import org.jgroups.*;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class NodeJChannel extends NodeReceiver{
    JChannel channel;
    String user_name;
    String node_name;
    String cluster_name;
    ArrayList<String> msgList;
    ReentrantLock lock;
    NodeJChannel(String node_name, String cluster_name, ArrayList<String> msgList) throws Exception {

        this.channel = new JChannel();
        this.user_name = System.getProperty("user.name", "n/a");
        this.node_name = node_name;
        this.cluster_name = cluster_name;
        this.msgList = msgList;
        this.channel.setReceiver(this).connect(cluster_name);
        this.lock = new ReentrantLock();
    }
    @Override
    public void receive(Message msg) {
        String line = msg.getSrc() + ": " + msg.getObject();
        System.out.println(line);
        this.lock.lock();;
        try{
            this.msgList.add(line);
        } finally {
            this.lock.unlock();
        }

        System.out.println("Receive successfully.");
        System.out.println(this.msgList);
    }

}
