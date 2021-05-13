package cn.yingming.grpc1;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.bistream.*;
import io.grpc.stub.StreamObserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

//
public class BiStreamClient {
    private ManagedChannel channel;
    private CommunicateGrpc.CommunicateBlockingStub blockingStub;
    private CommunicateGrpc.CommunicateStub asynStub;
    private String address;
    private String uuid;
    private String name;
    private ArrayList serverList;
    // lock of main thread
    private final ReentrantLock mainLock;
    private AtomicBoolean isWork;
    private ArrayList msgList;

    public BiStreamClient(String address) {
        this.address = address;
        this.name = null;
        this.uuid = UUID.randomUUID().toString();
        // gRPC Channel and stub
        this.channel = ManagedChannelBuilder.forTarget(address).usePlaintext().build();
        this.asynStub = CommunicateGrpc.newStub(this.channel);
        this.blockingStub = CommunicateGrpc.newBlockingStub(this.channel);
        this.mainLock = new ReentrantLock();
        // shared part, in lock
        /* isWork means whether the connection is available, which is control by

         */
        this.isWork = new AtomicBoolean(true);
        this.msgList = new ArrayList();
        this.serverList = new ArrayList();
    }

    private StreamObserver startGrpc(AtomicBoolean isWork) {
        ReentrantLock lock = new ReentrantLock();
        // Service 1
        StreamObserver<StreamRequest> requestStreamObserver = asynStub.createConnection(new StreamObserver<StreamResponse>() {
            @Override
            public void onNext(StreamResponse streamResponse) {
                System.out.println("[gRPC]:" + streamResponse.getTimestamp() + " [" + streamResponse.getName() + "]: " + streamResponse.getMessage());
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

    private void tryOneConnect(CommunicateGrpc.CommunicateBlockingStub blockingStub, String uuid, AtomicBoolean isWork) {
        ReqAsk req = ReqAsk.newBuilder().setSource(uuid).build();
        try {
            RepAsk rep = blockingStub.withDeadlineAfter(5000, TimeUnit.MILLISECONDS).ask(req);
            if (rep.getSurvival()) {
                ReentrantLock lock = new ReentrantLock();
                lock.lock();
                try {
                    isWork.set(true);
                } finally {
                    lock.unlock();
                }
            } else {
                System.out.println("[Reconnection]: One server refuses, next server.");
            }
        } catch (Exception e) {
            System.out.println("[Reconnection]: The new try connection is also not available.");
            // e.printStackTrace();
        }
    }

    private void join(StreamObserver requestStreamObserver) {
        // Join
        StreamRequest joinReq = StreamRequest.newBuilder()
                .setJoin(true)
                .setSource(uuid)
                .setName(name)
                .build();
        System.out.println("[gRPC]:Send join request:" + joinReq.toString());
        requestStreamObserver.onNext(joinReq);
    }

    private String setName() throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Input Name.");
        System.out.println(">");
        System.out.flush();
        String line = in.readLine().trim();
        this.name = line;
        return line;
    }

    // Thread for stdin input loop.
    class inputLoop implements Runnable {
        ReentrantLock inputLock;
        ArrayList sharedList;
        AtomicBoolean isWork;
        String uuid;
        String name;

        public inputLoop(String uuid, String name, ArrayList sharedList, AtomicBoolean isWork) {
            this.uuid = uuid;
            this.name = name;
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
                    isQuit(line);
                    // set up time for msg, and build message
                    Date d = new Date();
                    SimpleDateFormat dft = new SimpleDateFormat("hh:mm:ss");
                    StreamRequest msgReq = StreamRequest.newBuilder()
                            .setSource(this.uuid)
                            .setName(this.name)
                            .setMessage(line)
                            .setTimestamp(dft.format(d))
                            .build();
                    // Check the isWork, and do action.Add message to that shared message list or print error.
                    if (isWork.get()) {
                        inputLock.lock();
                        try {
                            this.sharedList.add(msgReq);
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            inputLock.unlock();
                        }
                    } else {
                        System.out.println("The connection does not work. Please wait.");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        private void isQuit(String line) {
            if (line.equals("quit")) {
                // Can add a part for sending quit request to server.
                Date d = new Date();
                SimpleDateFormat dft = new SimpleDateFormat("hh:mm:ss");
                StreamRequest msgReq = StreamRequest.newBuilder()
                        .setSource(this.uuid)
                        .setName(this.name)
                        .setTimestamp(dft.format(d))
                        .setQuit(true)
                        .build();
                if (isWork.get()) {
                    inputLock.lock();
                    try {
                        this.sharedList.add(msgReq);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        inputLock.unlock();
                    }
                } else {
                    System.out.println("The connection does not work. Please wait.");
                }
                // End the client.
                System.exit(0);
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
            this.asynStub = CommunicateGrpc.newStub(this.channel);
            this.blockingStub = CommunicateGrpc.newBlockingStub(this.channel);
            // send a unary request for test
            tryOneConnect(this.blockingStub, this.uuid, this.isWork);
            // using isWork to judge
            if (this.isWork.get()) {
                this.address = newAdd;
                System.out.println("[Reconnection]: Reconnect successfully to server-" + this.address);
                return true;
            }
            // maximum reconnection time is 10
            if (count > 10) {
                break;
            }
        }
        System.out.println("[Reconnection]: Reconnect many times, end the reconnection loop.");
        return false;
    }

    // Generates addresses of servers and Servers' addresses start from 127.0.0.1.
    private void addToServerList(int size) {
        String ip = "127.0.0.1:";
        int port = 50050;
        for (int i = 0; i < size; i++) {
            port++;
            this.serverList.add(ip + "" + port);
        }
    }

    // check input and state of streaming, and send messsage
    private void checkLoop(StreamObserver requestSender, BiStreamClient client) {
        while (true) {
            // the if statement is for inputLoop thread and state of bidirectional streaming.
            // If the channel
            if (client.msgList.size() != 0 && client.isWork.get()) {
                requestSender.onNext(client.msgList.get(0));
                client.mainLock.lock();
                try {
                    // requestSender.onNext(client.msgList.get(0));
                    client.msgList.remove(0);
                } finally {
                    client.mainLock.unlock();
                }
            } else if (!client.isWork.get()) {
                break;
            }
        }
    }

    // main logic
    private void start(String size) throws IOException {
        // 1.Set the name of the client and add servers' address to that server list
        this.setName();
        this.addToServerList(Integer.parseInt(size));
        // 2.Create inputLoop Thread.
        inputLoop inputThread = new inputLoop(this.uuid, this.name, this.msgList, this.isWork);
        Thread thread1 = new Thread(inputThread);
        thread1.start();
        // 3. while loop for a client and reconnect.
        while (true) {
            // 3.1 start gRPC client and send join request.
            StreamObserver requestSender = this.startGrpc(this.isWork);
            this.join(requestSender);
            // 3.2 check loop for connection problem and input content, and send request.
            this.checkLoop(requestSender, this);
            // 3.3 reconnect part.
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
        System.out.printf("Start: Connect to gRPC server: %s \n", args[0]);
        client.start(args[1]);
    }
}
