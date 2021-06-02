package cn.yingming.grpc1;

import io.grpc.jchannelRpc.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

public class ClientStub {

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
        if (input.startsWith("TO")){
            String[] strs = input.split(" ", 3);
            if (strs.length == 3){
                // set up time for msg, and build message
                MessageReq msgReq = MessageReq.newBuilder()
                        .setSource(this.client.uuid)
                        .setJchannelAddress(this.client.jchannel_address)
                        .setCluster(this.client.cluster)
                        .setContent(strs[2])
                        .setTimestamp(dft.format(d))
                        .setDestination(strs[1])
                        .build();
                Request req = Request.newBuilder().setMessageRequest(msgReq).build();
                return req;
            } else {
                MessageReq msgReq = MessageReq.newBuilder()
                        .setSource(this.client.uuid)
                        .setJchannelAddress(this.client.jchannel_address)
                        .setCluster(this.client.cluster)
                        .setContent(input)
                        .setTimestamp(dft.format(d))
                        .build();
                Request req = Request.newBuilder().setMessageRequest(msgReq).build();
                return req;
            }

        } else if (input.equals("disconnect")) {
            // disconnect request
            DisconnectReq msgReq = DisconnectReq.newBuilder()
                    .setSource(this.client.uuid)
                    .setJchannelAddress(this.client.jchannel_address)
                    .setCluster(this.client.cluster)
                    .setTimestamp(dft.format(d))
                    .build();
            Request req = Request.newBuilder()
                    .setDisconnectRequest(msgReq).build();
            return req;

        } else{
            // common message for broadcast to its cluster.
            MessageReq msgReq = MessageReq.newBuilder()
                    .setSource(this.client.uuid)
                    .setJchannelAddress(this.client.jchannel_address)
                    .setCluster(this.client.cluster)
                    .setContent(input)
                    .setTimestamp(dft.format(d))
                    .build();
            Request req = Request.newBuilder().setMessageRequest(msgReq).build();
            return req;
        }
    }

    public void judgeResponse(Response response){

        if (response.hasConnectResponse()){
            System.out.println("Get Connect() response.");
        } else if (response.hasMessageResponse()){
            // get message from server
            printMsg(response.getMessageResponse());
        } else if (response.hasUpdateResponse()){
            client.update(response.getUpdateResponse().getAddresses());
        } else if (response.hasDisconnectResponse()){
            stubLock.lock();
            try{
                client.down.set(false);
            } finally {
                stubLock.unlock();
            }

        } else if (response.hasViewResponse()){
            ViewRep view = response.getViewResponse();
            System.out.println("** View:[" + view.getCreator() + "|" + view.getViewNum() +
                    "] (" + view.getSize() + ")" + view.getJchannelAddresses());
        } else if (response.hasStateRep()){
            StateRep state = response.getStateRep();
            System.out.println(state.getSize() + " messages in the chat history.");
            if (state.getSize() != 0){
                LinkedList l = new LinkedList();
                l.addAll(state.getOneOfHistoryList());
                for (int i = 0; i < l.size(); i++) {
                    System.out.println(l.get(i));
                }
            }
        }
    }

    public void printMsg(MessageRep response){
        System.out.println("[JChannel] "
                + response.getJchannelAddress() + ":" + response.getContent());
    }

}



