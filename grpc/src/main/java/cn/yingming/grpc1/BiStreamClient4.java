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
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

//
public class BiStreamClient4 {
    private ManagedChannel channel;
    // not used because they just have Bi-directional Mode. Just need asynStub
    // private final CommunicateGrpc.CommunicateBlockingStub blockingStub;
    private CommunicateGrpc.CommunicateStub asynStub;
    private String host;
    private int port;
    private String uuid;
    private String name;
    private ArrayList serverList;
    // lock of main thread
    private final ReentrantLock mainLock;
    private AtomicBoolean isWork;
    private ArrayList msgList;

    public BiStreamClient4(String host, int port) {
        this.host = host;
        this.port = port;
        this.name = null;
        this.uuid = UUID.randomUUID().toString();
        // gRPC Channel and stub
        this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        this.asynStub = CommunicateGrpc.newStub(channel);
        this.mainLock = new ReentrantLock();
        // shared part
        this.isWork = new AtomicBoolean(true);
        this.msgList = new ArrayList();
        this.serverList = new ArrayList<Integer>();
        // for test
        this.serverList.add(50051);
        this.serverList.add(50052);
        // this.serverList.add(50053);
    }

    private StreamObserver startGrpc(String name, AtomicBoolean isWork){
        // Service 1
        StreamObserver<StreamRequest> requestStreamObserver = asynStub.createConnection(new StreamObserver<StreamResponse>() {
            @Override
            public void onNext(StreamResponse streamResponse) {
                System.out.println("In gRPC:" + Thread.currentThread().toString());
                // System.out.println(channel.getState(false));
                System.out.println(streamResponse.getTimestamp() + " [" + streamResponse.getName() + "]: " + streamResponse.getMessage());
            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println(throwable.getMessage());
                System.out.println("onError() of gRPC connection, need to reconnect to the next server.");
                ReentrantLock lock = new ReentrantLock();
                lock.lock();
                try{
                    isWork.set(false);
                } finally {
                  lock.unlock();
                }
                channel.shutdown();
                onCompleted();
            }

            @Override
            public void onCompleted() {
                System.out.println("onCompleted");
            }
        });
        return requestStreamObserver;
    }
    private void join(StreamObserver requestStreamObserver){
        // Join
        StreamRequest joinReq = StreamRequest.newBuilder()
                .setJoin(true)
                .setSource(uuid)
                .setName(name)
                .build();
        System.out.println("Send join request:" + joinReq.toString());
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
    static class inputLoop implements Runnable{
        ReentrantLock inputLock;
        ArrayList sharedList;
        AtomicBoolean isWork;
        String uuid;
        String name;
        public inputLoop(String uuid, String name, ArrayList sharedList, AtomicBoolean isWork){
            this.uuid = uuid;
            this.name = name;
            this.sharedList = sharedList;
            this.isWork = isWork;
            this.inputLock = new ReentrantLock();
        }
        @Override
        public void run() {
            BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

            while(true){
                try {
                    // Input line
                    System.out.println(">");
                    System.out.flush();
                    String line = in.readLine();
                    System.out.println("[ Send msg ]: " + line);
                    System.out.println("Input thread:" + Thread.currentThread().toString());
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
                    if (isWork.get()){
                        inputLock.lock();
                        try{
                            this.sharedList.add(msgReq);
                        } catch (Exception e){
                            e.printStackTrace();
                        } finally {
                            inputLock.unlock();
                        }
                    } else{
                        System.out.println("The connection does not work. Please wait.");
                    }
                    System.out.println("Input thread Loop.");
                } catch(Exception e){
                    e.printStackTrace();
                }
            }
        }
    }

    private boolean reconnect() {

        for (int i = 0; i < this.serverList.size(); i++) {
            int next = (int) this.serverList.get(i);
            int now = this.port;
            System.out.println(next + " " + now);
            if (next != now){
                this.channel = ManagedChannelBuilder.forAddress(this.host, next).usePlaintext().build();
                this.asynStub = CommunicateGrpc.newStub(this.channel);
                try{
                    Thread.sleep(5000);
                } catch (InterruptedException e){
                    e.printStackTrace();
                }
                String state = this.channel.getState(false).toString();
                System.out.println(state);
                if (state.equals("READY") || state.equals("IDLE")){
                    System.out.println("Reconnect successfully. " + host + ":" + port);
                    this.isWork.set(true);
                    return true;
                }
            } else{
                System.out.println("The current server is not available." +this.host + ":" + this.port);
            }
        }
        System.out.println("All server are not available.");
        return false;
    }

    public static void main(String[] args) throws IOException {
        BiStreamClient4 client = new BiStreamClient4(args[0], Integer.parseInt(args[1]));
        System.out.printf("Connect to gRPC server: %s:%s \n", args[0], Integer.parseInt(args[1]));
        ArrayList serverList = new ArrayList();

        // 1.Set the name of the client.
        String nameStr = client.setName();
        // 2.Create inputLoop Thread.
        inputLoop inputThread = new inputLoop(client.uuid, client.name, client.msgList, client.isWork);
        Thread thread1 = new Thread(inputThread);
        thread1.start();
        // 3. while loop for a client and reconnect.
        while(true){
            // 3.1 start gRPC client and send join request.
            System.out.println("3.1");
            StreamObserver requestSender = client.startGrpc(nameStr, client.isWork);
            client.join(requestSender);
            // 3.2 check loop for connection problem and input content, and send request.
            while(true){
                if (client.msgList.size()!=0 && client.isWork.get()){
                    client.mainLock.lock();
                    try{
                        requestSender.onNext(client.msgList.get(0));
                        client.msgList.remove(0);
                    } finally {
                        client.mainLock.unlock();
                    }
                } else if(!client.isWork.get()){
                    break;
                }
            }
            // 3.3 reconnect part.
            boolean result = client.reconnect();
            if (!result){
                System.out.println("End.");
                break;
            }
        }

        // 4. End all threads.
        System.exit(0);
    }
}
