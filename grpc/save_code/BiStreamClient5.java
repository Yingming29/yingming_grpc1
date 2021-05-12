package cn.yingming.grpc1;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.bistream.*;
import io.grpc.stub.StreamObserver;

import java.io.*;

import java.nio.Buffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

// gRPC client
public class BiStreamClient {
    private ManagedChannel channel;
    // not used because they just have Bi-directional Mode. Just need asynStub
    // private final CommunicateGrpc.CommunicateBlockingStub blockingStub;
    private CommunicateGrpc.CommunicateStub asynStub;
    private String host;
    private int port;
    public String uuid;
    private String name;
    private final ReentrantLock lock;
    private AtomicBoolean connect;
    BufferedReader in;
    public BiStreamClient(String host, int port) {
        this.host = host;
        this.port = port;
        // Build Channel and use plaintext
        this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        // Generate Stub
        this.asynStub = CommunicateGrpc.newStub(channel);
        this.uuid = UUID.randomUUID().toString();
        this.name = null;
        this.lock = new ReentrantLock();
    }

    private void start(String name){
        // Service 1
        StreamObserver<StreamRequest> requestStreamObserver = asynStub.createConnection(new StreamObserver<StreamResponse>() {
            @Override
            public void onNext(StreamResponse streamResponse) {
                System.out.println(streamResponse.getTimestamp() + " [" + streamResponse.getName() + "]: " + streamResponse.getMessage());
            }
            @Override
            public void onError(Throwable throwable) {
                System.out.println(throwable.getMessage());
                System.out.println("The client will reconnect to the next gRPC server.");
                connect.set(false);
            }

            @Override
            public void onCompleted() {
                System.out.println("onCompleted");
            }
        });
        System.out.println("gRCP:" + Thread.currentThread().toString());
        join(requestStreamObserver);

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while(true){
            try {
                System.out.println(">");
                System.out.flush();
                String line = in.readLine();
                System.out.println("[ Send msg ]: " + line);
                // set up time for msg
                Date d = new Date();
                SimpleDateFormat dft = new SimpleDateFormat("hh:mm:ss");
                StreamRequest msgReq = StreamRequest.newBuilder()
                        .setSource(uuid)
                        .setName(name)
                        .setMessage(line)
                        .setTimestamp(dft.format(d))
                        .build();
                requestStreamObserver.onNext(msgReq);
            } catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    private void join(StreamObserver requestStreamObserver){
        // Join
        StreamRequest joinReq = StreamRequest.newBuilder()
                .setJoin(true)
                .setSource(uuid)
                .setName(name)
                .build();
        System.out.println(joinReq.toString());
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

    private void reconnect(){
        this.lock.lock();
        try{
            this.channel = ManagedChannelBuilder.forAddress("127.0.0.1", 50052).usePlaintext().build();
            this.asynStub = CommunicateGrpc.newStub(this.channel);
        }finally {
            this.lock.unlock();
        }
    }


    static class inputLoop implements Runnable{

        private StreamObserver<StreamRequest> observer;
        private AtomicBoolean connect;
        private BufferedReader in;
        String uuid, name;
        public inputLoop(StreamObserver observer, AtomicBoolean connect, String uuid, String name, BufferedReader in) {
            this.observer = observer;
            this.connect = connect;
            this.uuid = uuid;
            this.name = name;
            this.in = in;
        }
        public void interrupt(){
            this.connect.set(false);
        }

        @Override
        public void run() {
            System.out.println(" Sub thread run: " + Thread.currentThread().toString());
            while(connect.get()){
                System.out.println(">");
                System.out.flush();
                String line = null;
                try{
                    line = in.readLine();
                } catch(IOException e){
                    e.printStackTrace();
                }
                System.out.println("[ Send msg ]: " + line);
                // set up time for msg
                Date d = new Date();
                SimpleDateFormat dft = new SimpleDateFormat("hh:mm:ss");
                StreamRequest msgReq = StreamRequest.newBuilder()
                        .setSource(uuid)
                        .setName(name)
                        .setMessage(line)
                        .setTimestamp(dft.format(d))
                        .build();
                observer.onNext(msgReq);
                    /*
                    if (1 == 2) {
                        Thread.sleep(1);
                    } else{
                        System.out.println("test");
                    }/*
                catch(InterruptedException i){
                    Thread.currentThread().interrupt();
                }*/

            }
        }
    }

    /*
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while(true){
            try {
                System.out.println(">");
                System.out.flush();
                String line = in.readLine();
                System.out.println("[ Send msg ]: " + line);
                // set up time for msg
                Date d = new Date();
                SimpleDateFormat dft = new SimpleDateFormat("hh:mm:ss");
                StreamRequest msgReq = StreamRequest.newBuilder()
                        .setSource(uuid)
                        .setName(name)
                        .setMessage(line)
                        .setTimestamp(dft.format(d))
                        .build();
                requestStreamObserver.onNext(msgReq);
            } catch(Exception e){
                e.printStackTrace();
            }
        }
     */
    public static void main(String[] args) throws IOException{
        BiStreamClient client = new BiStreamClient(args[0], Integer.parseInt(args[1]));
        System.out.printf("Connect to gRPC server: %s:%s \n", args[0], Integer.parseInt(args[1]));
        String nameStr = client.setName();
        client.start(nameStr);
    }
}
