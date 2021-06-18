package cn.yingming.grpc1;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.jchannelRpc.*;
import io.grpc.stub.StreamObserver;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class RemoteJChannelStub{

    private RemoteJChannel client;

    private ReentrantLock stubLock;
    public ArrayList serverList;
    public ManagedChannel channel;
    private JChannelsServiceGrpc.JChannelsServiceBlockingStub blockingStub;
    private JChannelsServiceGrpc.JChannelsServiceStub asynStub;

    RemoteJChannelStub(RemoteJChannel client) {
        this.client = client;
        this.stubLock = new ReentrantLock();
        this.serverList = new ArrayList<String>();
        this.channel = ManagedChannelBuilder.forTarget(client.address).usePlaintext().build();
        this.asynStub = JChannelsServiceGrpc.newStub(this.channel);
        this.blockingStub = JChannelsServiceGrpc.newBlockingStub(this.channel);
    }

    public Request judgeRequest(Object obj) {
        Date d = new Date();
        SimpleDateFormat dft = new SimpleDateFormat("hh:mm:ss");
        if (obj instanceof String){
            String input = (String) obj;
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
        else if(obj instanceof MessageRJ){
            MessageRJ msg = (MessageRJ) obj;
            boolean checkResult = msg.check();
            if (!checkResult){
                try{
                    throw new IllegalArgumentException("The MessageRJ has both of buf and msg property.");
                } catch (Exception e){
                    e.printStackTrace();
                }
                if (msg.getDst() != null){
                    MessageReq msgReq = MessageReq.newBuilder()
                            .setSource(this.client.uuid)
                            .setJchannelAddress(this.client.jchannel_address)
                            .setCluster(this.client.cluster)
                            .setContent(msg.getMsg())
                            .setTimestamp(dft.format(d))
                            .setDestination(msg.getDst())
                            .build();
                    Request req = Request.newBuilder().setMessageRequest(msgReq).build();
                    return req;
                } else{
                    MessageReq msgReq = MessageReq.newBuilder()
                            .setSource(this.client.uuid)
                            .setJchannelAddress(this.client.jchannel_address)
                            .setCluster(this.client.cluster)
                            .setContent(msg.getMsg())
                            .setTimestamp(dft.format(d))
                            .build();
                    Request req = Request.newBuilder().setMessageRequest(msgReq).build();
                    return req;
                }

            } else{
                if (msg.getDst() != null){
                    MessageReq msgReq = MessageReq.newBuilder()
                            .setSource(this.client.uuid)
                            .setJchannelAddress(this.client.jchannel_address)
                            .setCluster(this.client.cluster)
                            .setContent(msg.getMsg())
                            .setTimestamp(dft.format(d))
                            .setDestination(msg.getDst())
                            .build();
                    Request req = Request.newBuilder().setMessageRequest(msgReq).build();
                    return req;
                } else{
                    MessageReq msgReq = MessageReq.newBuilder()
                            .setSource(this.client.uuid)
                            .setJchannelAddress(this.client.jchannel_address)
                            .setCluster(this.client.cluster)
                            .setContent(msg.getMsg())
                            .setTimestamp(dft.format(d))
                            .build();
                    Request req = Request.newBuilder().setMessageRequest(msgReq).build();
                    return req;
                }

            }
        }

    }

    public void judgeResponse(Response response){

        if (response.hasConnectResponse()){
             System.out.println("Get Connect() response.");
        } else if (response.hasMessageResponse()){
            // get message from server
            printMsg(response.getMessageResponse());
        } else if (response.hasUpdateResponse()){
            update(response.getUpdateResponse().getAddresses());
        } else if (response.hasDisconnectResponse()){
            stubLock.lock();
            try{
                client.down.set(false);
            } finally {
                stubLock.unlock();
            }

        } else if (response.hasViewResponse()){
            ViewRep view = response.getViewResponse();
            // System.out.println("** View:[" + view.getCreator() + "|" + view.getViewNum() +
            //        "] (" + view.getSize() + ")" + view.getJchannelAddresses());
            // changed
            this.client.view.updateView(view);

            // change:  call receiver of remote jchannel

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


    private StreamObserver startGrpc(AtomicBoolean isWork) {

        ReentrantLock lock = new ReentrantLock();
        // Service 1
        StreamObserver<Request> requestStreamObserver = asynStub.connect(new StreamObserver<Response>() {

            @Override
            public void onNext(Response response) {
                judgeResponse(response);
            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println(throwable.getMessage());
                System.out.println("[gRPC]: onError() of gRPC connection, the client needs to reconnect to the next server.");
                // change the state of the bi-directional streaming.
                lock.lock();
                try {
                    isWork.set(false);
                } finally {
                    lock.unlock();
                }
                channel.shutdown();
                onCompleted();
            }

            @Override
            public void onCompleted() {
                System.out.println("[gRPC]: onCompleted of the current channel.");
            }
        });
        return requestStreamObserver;

    }

    public void update(String addresses){
        String[] add = addresses.split(" ");
        List<String> newList = Arrays.asList(add);
        stubLock.lock();
        try {
            serverList.clear();
            serverList.addAll(newList);
            System.out.println("Update addresses of servers: " + serverList);
        } finally {
            stubLock.unlock();
        }
    }

    private boolean tryOneConnect() {
        try {
            Thread.sleep(5000);
        } catch (Exception e){
            e.printStackTrace();
        }

        ReqAsk req = ReqAsk.newBuilder().setSource(client.uuid).build();
        try {
            RepAsk rep = this.blockingStub.withDeadlineAfter(5000, TimeUnit.MILLISECONDS).ask(req);
            if (rep.getSurvival()) {
                return true;
            } else {
                System.out.println("[Reconnection]: One server refuses, next server.");
            }
        } catch (Exception e) {
            System.out.println("[Reconnection]: The new try connection is also not available.");
            // e.printStackTrace();
        }
        return false;
    }

    private void connectCluster(StreamObserver requestStreamObserver) {
        // Generated time
        Date d = new Date();
        SimpleDateFormat dft = new SimpleDateFormat("hh:mm:ss");
        // connect() request
        ConnectReq joinReq = ConnectReq.newBuilder()
                .setSource(client.uuid)
                .setJchannelAddress(client.jchannel_address)
                .setCluster(client.cluster)
                .setTimestamp(dft.format(d))
                .build();
        Request req = Request.newBuilder()
                .setConnectRequest(joinReq)
                .build();
        // System.out.println(client.name + " calls connect() request to Jgroups cluster: " + client.cluster);
        requestStreamObserver.onNext(req);
        // change the state of client
        stubLock.lock();
        try {
            client.isWork.set(true);
            client.down.set(true);
        } finally {
            stubLock.unlock();
        }
    }
    // Do a reconnection loop with given times. e.g. 10 times.
    private boolean reconnect() {
        int count = 0;
        if (this.serverList.size() == 0){
            System.out.println("The available server list is null. Cannot select new address of node.");
            this.stubLock.lock();
            try{
                this.client.down.set(false);
            } finally {
                this.stubLock.unlock();
            }
            return false;
        }
        while (true) {
            count++;
            Random r = new Random();
            int randomSelect = r.nextInt(this.serverList.size());
            String newAdd = (String) this.serverList.get(randomSelect);
            System.out.println("[Reconnection]: Random selected server for reconnection:" + newAdd);
            // try to build new channel and generate new stubs for new server
            this.channel = ManagedChannelBuilder.forTarget(newAdd).usePlaintext().build();
            this.asynStub = JChannelsServiceGrpc.newStub(this.channel);
            this.blockingStub = JChannelsServiceGrpc.newBlockingStub(this.channel);
            // send a unary request for test
            boolean tryResult = tryOneConnect();
            // using try result to judge
            if (tryResult) {
                client.address = newAdd;
                System.out.println("[Reconnection]: Reconnect successfully to server-" + client.address);
                return true;
            }
            // maximum reconnection time
            if (count > 9999) {
                break;
            }
        }
        System.out.println("[Reconnection]: Reconnect many times, end the reconnection loop.");
        return false;
    }

    private void getState(StreamObserver requestStreamObserver) {
        // state request
        StateReq stateReq = StateReq.newBuilder()
                .setSource(client.uuid)
                .setCluster(client.cluster)
                .setJchannelAddress(client.jchannel_address)
                .build();
        Request req = Request.newBuilder()
                .setStateReq(stateReq)
                .build();
        // System.out.println(client.name + " calls getState() request for Jgroups cluster: " + client.cluster);
        requestStreamObserver.onNext(req);
    }


    class Control implements Runnable {
        ReentrantLock inputLock;
        ArrayList sharedList;
        AtomicBoolean isWork;

        public Control(ArrayList sharedList, AtomicBoolean isWork) {
            this.sharedList = sharedList;
            this.isWork = isWork;
            this.inputLock = new ReentrantLock();
        }

        @Override
        public void run() {
            while (true) {
                // 4.1 start gRPC client and call connect() request.
                // change: remove the argument isWork
                StreamObserver requestSender = startGrpc(this.isWork);
                // change
                // connectCluster(requestSender);
                // 4.2 getState() of JChannel
                // change
                 // getState(requestSender);
                // 4.3 check loop for connection problem and input content, and send request.
                this.checkLoop(requestSender);
                // 4.4 reconnect part.
                boolean result = reconnect();
                if (!result) {
                    System.out.println("End the control loop of stub.");
                    break;
                }
            }

        }

        // check input and state of streaming, and send messsage
        private void checkLoop(StreamObserver requestSender) {
            while (true) {
                // the if statement is for inputLoop thread and state of bidirectional streaming.
                // If the channel
                if (client.msgList.size() != 0 && client.isWork.get()) {
                    // treat a input.
                    // tag, add a client stub treatment.
                    Object obj = client.msgList.get(0);
                    Request msgReq = judgeRequest(obj);
                    requestSender.onNext(msgReq);
                    stubLock.lock();
                    try {
                        // requestSender.onNext(client.msgList.get(0));
                        client.msgList.remove(0);
                    } finally {
                        stubLock.unlock();
                    }

                } else if (!client.isWork.get()) {
                    break;
                } else if (!client.down.get()){
                    try{
                        System.exit(0);
                    } catch (Exception e){
                        e.printStackTrace();
                    }
                }
            }
        }

    }



    public void startStub(){
        Control control = new Control(client.msgList, client.isWork);
        Thread thread1 = new Thread(control);
        thread1.start();
    }
}
