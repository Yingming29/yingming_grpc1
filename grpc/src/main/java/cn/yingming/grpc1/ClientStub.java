package cn.yingming.grpc1;

import io.grpc.jchannelRpc.ConnectRep;
import io.grpc.jchannelRpc.ConnectReq;
import io.grpc.jchannelRpc.JChannelsServiceGrpc;
import io.grpc.jchannelRpc.Response;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class ClientStub {
    private String uuid;
    private String name;
    private String jchannel_add;
    private String cluster;
    private BiStreamClient client;
    private JChannelsServiceGrpc.JChannelsServiceBlockingStub blockingStub;
    // private AtomicBoolean haveCluster;  // whether connect to the cluster
    private ReentrantLock stubLock;

    ClientStub(BiStreamClient client) {
        this.client = client;
        // this.haveCluster = new AtomicBoolean(false);
        this.stubLock = new ReentrantLock();
    }

    public void judgeMethod(String input) {

    }

    public void judgeResponse(Response response){
        if (){
            client.update(streamResponse.getAddresses());
        } else{
            System.out.println("[gRPC]:" + streamResponse.getTimestamp() + " [" + streamResponse.getName() + "]: " + streamResponse.getMessage());
        }
    }

}



