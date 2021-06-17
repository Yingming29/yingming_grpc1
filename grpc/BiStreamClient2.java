/*
package cn.yingming.grpc1;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
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
public class BiStreamClient2 {
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

    public BiStreamClient2(String address) {
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


        this.isWork = new AtomicBoolean(false);
        this.serverList = new ArrayList();
    }

    private StreamObserver startGrpc() {
        ReentrantLock lock = new ReentrantLock();
        System.out.println("Start a new gPRC streaming.");
        lock.lock();
        try {
            isWork.set(true);
        } finally {
            lock.unlock();
        }
        // Service 1
        StreamObserver<StreamRequest> requestStreamObserver = asynStub.createConnection(new StreamObserver<StreamResponse>() {
            @Override
            public void onNext(StreamResponse streamResponse) {
                if (streamResponse.getAddresses()!= ""){
                    update(streamResponse.getAddresses());
                } else{
                    System.out.println("[gRPC]:" + streamResponse.getTimestamp() + " [" + streamResponse.getName() + "]: " + streamResponse.getMessage());
                }
            }
            public void update(String addresses){
                String[] add = addresses.split(" ");
                List<String> newList = Arrays.asList(add);
                lock.lock();
                try {
                    serverList.clear();
                    serverList.addAll(newList);
                    System.out.println("Update addresses of servers: " + serverList);
                } finally {
                    lock.unlock();
                }
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
                onCompleted();
            }

            @Override
            public void onCompleted() {
                System.out.println("[gRPC]: onCompleted of the current channel.");
                reconnect();
            }

        });

        return requestStreamObserver;
    }

    private void reconnect() {
        synchronized (this){
            int count = 0;
            while (true) {
                // System.out.println("Reconnect:" + Thread.currentThread().toString());
                count++;
                Random r = new Random();
                int randomSelect = r.nextInt(serverList.size());
                String newAdd = (String) serverList.get(randomSelect);
                System.out.println("[Reconnection]: Random selected server for reconnection:" + newAdd);
                // try to build new channel and generate new stubs for new server
                channel = ManagedChannelBuilder.forTarget(newAdd).usePlaintext().build();
                asynStub = CommunicateGrpc.newStub(channel);
                blockingStub = CommunicateGrpc.newBlockingStub(channel);
                // send a unary request for test
                tryOneConnect();

                // using isWork to judge
                if (isWork.get()) {
                    address = newAdd;
                    System.out.println("[Reconnection]: Reconnect successfully to server-" + address);
                    break;
                }
                // maximum reconnection time is 10
                if (count > 9999) {
                    System.out.println("Over the maximum times of reconnection, end.");
                    System.exit(0);
                }
            }
            this.notify();
        }
    }

    private void tryOneConnect() {
        ReqAsk req = ReqAsk.newBuilder().setSource(this.uuid).build();
        try {
            RepAsk rep = this.blockingStub.withDeadlineAfter(5000, TimeUnit.MILLISECONDS).ask(req);
            if (rep.getSurvival()) {
                ReentrantLock lock = new ReentrantLock();
                lock.lock();
                try {
                    this.isWork.set(true);
                } finally {
                    lock.unlock();
                }
            } else {
                System.out.println("[Reconnection]: One server refuses, next server.");
            }
        } catch (Exception e) {
            System.out.println("[Reconnection]: The new try connection is also not available.");
           //  e.printStackTrace();
        }
    }

    private void join(StreamObserver requestStreamObserver) {
        StreamRequest joinReq = StreamRequest.newBuilder()
                .setJoin(true)
                .setSource(this.uuid)
                .setName(this.name)
                .build();
        System.out.println("[gRPC]:Send join request:" + joinReq.toString());
        try {
            requestStreamObserver.onNext(joinReq);
        } catch (Exception e){
            System.out.println("The first server is not available.");
            e.printStackTrace();
        }

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
        AtomicBoolean isWork;
        String uuid;
        String name;
        StreamObserver requestObserver;

        public inputLoop(String uuid, String name, AtomicBoolean isWork) {
            this.uuid = uuid;
            this.name = name;
            this.isWork = isWork;
            this.inputLock = new ReentrantLock();
            this.requestObserver = null;
        }
        public void setRequestObserver(StreamObserver requestObserver){
            this.requestObserver = requestObserver;
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
                    // System.out.println("Input loop:" + Thread.currentThread().toString());
                    // set up time for msg, and build message
                    Date d = new Date();
                    SimpleDateFormat dft = new SimpleDateFormat("hh:mm:ss");
                    StreamRequest msgReq = null;
                    // set the content of message
                    if (line.equals("quit")){
                        msgReq = StreamRequest.newBuilder()
                                .setSource(this.uuid)
                                .setName(this.name)
                                .setTimestamp(dft.format(d))
                                .setQuit(true)
                                .build();
                    } else{
                        msgReq = StreamRequest.newBuilder()
                                .setSource(this.uuid)
                                .setName(this.name)
                                .setMessage(line)
                                .setTimestamp(dft.format(d))
                                .build();
                    }
                    // Check the isWork, and do action. send msg or print error.
                    if (isWork.get()) {
                        inputLock.lock();
                        try {
                            // send message
                            requestObserver.onNext(msgReq);
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            inputLock.unlock();
                        }
                    } else{
                        System.out.println("The connection does not work. Please wait.");
                    }
                    // if quit, end the client
                    if (line.equals("quit")){
                        System.exit(0);
                    }


                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
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

    // main logic
    private void start() throws IOException {
        // 1.Set the name of the client and add servers' address to that server list
        this.setName();
        // this.addToServerList(Integer.parseInt(size));
        // 2.Create inputLoop Thread.
        inputLoop inputThread = new inputLoop(this.uuid, this.name, this.isWork);
        Thread thread1 = new Thread(inputThread);
        thread1.start();
        // 3. blocking while()
        synchronized (this){
            while (true) {
                StreamObserver requestSender = this.startGrpc();
                inputThread.setRequestObserver(requestSender);
                this.join(requestSender);
                try {
                    this.wait();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        BiStreamClient2 client = new BiStreamClient2(args[0]);
        System.out.printf("Start: Connect to gRPC server: %s \n", args[0]);
        client.start();
    }
}
*/
