package cn.yingming.grpc1;

import io.grpc.bistream.CommunicateGrpc;
import org.jgroups.*;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantLock;

public class NodeJChannel extends NodeReceiver{
    JChannel channel;
    String user_name;
    String node_name;
    String cluster_name;
    ReentrantLock lock;
    NodeServer3rd.CommunicateImpl service;
    NodeJChannel(String node_name, String cluster_name) throws Exception {

        this.channel = new JChannel();
        this.user_name = System.getProperty("user.name", "n/a");
        this.node_name = node_name;
        this.cluster_name = cluster_name;
        this.channel.setReceiver(this).connect(cluster_name);
        this.lock = new ReentrantLock();
        this.service = null;
    }


    @Override
    public void receive(Message msg) {
        String line = msg.getSrc() + ": " + msg.getObject();
        System.out.println("[JChannel] " + line);

        this.lock.lock();;
        try{
            this.service.broadcast(msg.getObject().toString());
        } finally {
            this.lock.unlock();
        }
    }

    public void setService(NodeServer3rd.CommunicateImpl gRPCservice){
        this.service = gRPCservice;
    }

}
