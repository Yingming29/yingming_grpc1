package cn.yingming.grpc1;

import io.grpc.jchannelRpc.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class ClientStub {
    private String uuid;
    private String name;
    private String jchannel_add;
    private String cluster;
    private BiStreamClient client;
    // private AtomicBoolean haveCluster;  // whether connect to the cluster
    private ReentrantLock stubLock;

    ClientStub(BiStreamClient client) {
        this.client = client;
        // this.haveCluster = new AtomicBoolean(false);
        this.stubLock = new ReentrantLock();
    }

    public Request judgeRequest(String input) {
        Date d = new Date();
        SimpleDateFormat dft = new SimpleDateFormat("hh:mm:ss");
        // single send request
        if (input.startsWith("[TO]")){
            String[] strs = input.split(" ", 3);
            // set up time for msg, and build message
            MessageReq msgReq = MessageReq.newBuilder()
                    .setSource(uuid)
                    .setJchannelAddress(jchannel_add)
                    .setCluster(cluster)
                    .setContent(strs[2])
                    .setTimestamp(dft.format(d))
                    .setDestination(strs[1])
                    .build();
            Request req = Request.newBuilder().setMessageRequest(msgReq).build();
            return req;
        } else if (input.equals("quit")) {
            // disconnect request
            DisconnectReq msgReq = DisconnectReq.newBuilder()
                    .setSource(uuid)
                    .setJchannelAddress(jchannel_add)
                    .setCluster(cluster)
                    .setTimestamp(dft.format(d))
                    .build();
            Request req = Request.newBuilder()
                    .setDisconnectRequest(msgReq).build();
            return req;
        } else{
            // common message for broadcast to its cluster.
            MessageReq msgReq = MessageReq.newBuilder()
                    .setSource(uuid)
                    .setJchannelAddress(jchannel_add)
                    .setCluster(cluster)
                    .setContent(input)
                    .setTimestamp(dft.format(d))
                    .build();
            Request req = Request.newBuilder().setMessageRequest(msgReq).build();
            return req;
        }
    }

    public void judgeResponse(Response response){

        if (response.hasConnectResponse()){
            // delete?
            System.out.println("Get Connect() response.");
        } else if (response.hasMessageResponse()){
            // get message from server
            printMsg(response.getMessageResponse());
        } else if (response.hasUpdateResponse()){
            client.update(response.getUpdateResponse().getAddresses());
        } else if (response.hasDisconnectResponse()){
            // null
        }
    }

    public void printMsg(MessageRep response){
        System.out.println("[JChannel] Receive message from "
                + response.getJchannelAddress() + ":" + response.getContent());
    }

}



