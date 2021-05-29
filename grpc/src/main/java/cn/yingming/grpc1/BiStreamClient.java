package cn.yingming.grpc1;

import io.grpc.ClientCall;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.jchannelRpc.*;
import io.grpc.stub.StreamObserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

//
public class BiStreamClient {
    // the grpc for the bidirectional streaming
    private ManagedChannel channel;
    private JChannelsServiceGrpc.JChannelsServiceBlockingStub blockingStub;
    private JChannelsServiceGrpc.JChannelsServiceStub asynStub;
    private String address;
    public String uuid;
    private String name;
    private ArrayList serverList;
    // lock of main thread
    private final ReentrantLock mainLock;
    private AtomicBoolean isWork;
    private ArrayList msgList;
    public String jchannel_address;
    public String cluster;
    private ClientStub clientStub;
    public AtomicBoolean down;
    public LinkedList history;

    public BiStreamClient(String address) {
        this.address = address;
        this.name = null;
        this.jchannel_address = null;
        this.cluster = null;
        this.uuid = UUID.randomUUID().toString();
        // gRPC Channel and stub
        this.channel = ManagedChannelBuilder.forTarget(address).usePlaintext().build();
        this.asynStub = JChannelsServiceGrpc.newStub(this.channel);
        this.blockingStub = JChannelsServiceGrpc.newBlockingStub(this.channel);
        this.mainLock = new ReentrantLock();
        this.isWork = new AtomicBoolean(true);
        this.msgList = new ArrayList();
        this.serverList = new ArrayList();
        this.clientStub = null;
        this.down = new AtomicBoolean(true);
        this.history = new LinkedList();
    }

    private StreamObserver startGrpc(AtomicBoolean isWork) {

        ReentrantLock lock = new ReentrantLock();
        // Service 1
        StreamObserver<Request> requestStreamObserver = asynStub.connect(new StreamObserver<Response>() {

            @Override
            public void onNext(Response response) {
                clientStub.judgeResponse(response);
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
        mainLock.lock();
        try {
            serverList.clear();
            serverList.addAll(newList);
            System.out.println("Update addresses of servers: " + serverList);
        } finally {
            mainLock.unlock();
        }
    }

    private boolean tryOneConnect() {
        try {
            Thread.sleep(5000);
        } catch (Exception e){
            e.printStackTrace();
        }

        ReqAsk req = ReqAsk.newBuilder().setSource(this.uuid).build();
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
                .setSource(uuid)
                .setJchannelAddress(jchannel_address)
                .setCluster(cluster)
                .setTimestamp(dft.format(d))
                .build();
        Request req = Request.newBuilder()
                .setConnectRequest(joinReq)
                .build();
        System.out.println(this.name + " calls connect() request to Jgroups cluster: " + cluster);
        requestStreamObserver.onNext(req);
        // change the state of client
        mainLock.lock();
        try {
            isWork.set(true);
        } finally {
            mainLock.unlock();
        }
    }
    // set the name and the JChannel address
    private String setName() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Input Name.");
        System.out.println(">");
        System.out.flush();
        String line = in.readLine().trim();
        this.name = line;
        this.jchannel_address = "JChannel-" + this.name;
        return line;
    }

    // Set the cluster
    private void setCluster() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Input cluster.");
        System.out.println(">");
        System.out.flush();
        String line = in.readLine().trim();
        this.cluster = line;
    }

    /* Thread for stdin input loop.
       The stub-thread can out in the client-stub? not sure.
     */
    class inputLoop implements Runnable {
        ReentrantLock inputLock;
        ArrayList sharedList;
        AtomicBoolean isWork;

        public inputLoop(ArrayList sharedList, AtomicBoolean isWork) {

            this.sharedList = sharedList;
            this.isWork = isWork;
            this.inputLock = new ReentrantLock();
        }

        @Override
        public void run() {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

            while (true) {
                try {
                    // Input line
                    System.out.println(">");
                    System.out.flush();
                    String line = in.readLine();

                    // Check the isWork, and do action.Add message to that shared message list or print error.
                    if (!isWork.get()) {
                        System.out.println("The connection does not work. Store the message.");
                    }
                    // store the message to
                    inputLock.lock();
                    try {
                        this.sharedList.add(line);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        inputLock.unlock();
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    // Do a reconnection loop with given times. e.g. 10 times.
    private boolean reconnect() {
        int count = 0;
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
                this.address = newAdd;
                System.out.println("[Reconnection]: Reconnect successfully to server-" + this.address);
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

    // check input and state of streaming, and send messsage
    private void checkLoop(StreamObserver requestSender, BiStreamClient client) {
        while (true) {
            // the if statement is for inputLoop thread and state of bidirectional streaming.
            // If the channel
            if (client.msgList.size() != 0 && client.isWork.get()) {
                // treat a input.
                // tag, add a client stub treatment.
                String line = (String) msgList.get(0);
                Request msgReq = clientStub.judgeRequest(line);

                requestSender.onNext(msgReq);
                client.mainLock.lock();
                try {
                    // requestSender.onNext(client.msgList.get(0));
                    client.msgList.remove(0);
                } finally {
                    client.mainLock.unlock();
                }

            } else if (!client.isWork.get()) {
                break;
            } else if (!client.down.get()){
                System.out.println("End");
                try{
                    System.exit(0);
                } catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    private void startClientStub(){
        this.clientStub = new ClientStub(this);
    }

    private void getState(StreamObserver requestStreamObserver) {
        // state request
        StateReq stateReq = StateReq.newBuilder()
                .setSource(uuid)
                .setCluster(cluster)
                .setJchannelAddress(jchannel_address)
                .build();
        Request req = Request.newBuilder()
                .setStateReq(stateReq)
                .build();
        System.out.println(this.name + " calls getState() request for Jgroups cluster: " + cluster);
        requestStreamObserver.onNext(req);
    }

    // main logic
    private void start() throws IOException {
        // 1.Set the name of the client and jchannel cluster.
        this.setName();
        this.setCluster();
        // 2.Start client stub
        this.startClientStub();
        // 3.Create inputLoop Thread.
        inputLoop inputThread = new inputLoop(this.msgList, this.isWork);
        Thread thread1 = new Thread(inputThread);
        thread1.start();
        // 4. while loop for a client and reconnect.
        while (true) {
            // 4.1 start gRPC client and call connect() request.
            StreamObserver requestSender = this.startGrpc(this.isWork);
            this.connectCluster(requestSender);
            // 4.2 getState() of JChannel
            this.getState(requestSender);
            // 4.3 check loop for connection problem and input content, and send request.
            this.checkLoop(requestSender, this);
            // 4.4 reconnect part.
            boolean result = this.reconnect();
            if (!result) {
                System.out.println("End.");
                break;
            }
        }
        // 4. End.
        System.exit(0);
    }

    public static void main(String[] args) throws IOException {
        BiStreamClient client = new BiStreamClient(args[0]);
        System.out.printf("Start: Will connect to gRPC server: %s \n", args[0]);
        client.start();
    }
}
