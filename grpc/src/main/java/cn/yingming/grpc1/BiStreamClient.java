package cn.yingming.grpc1;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.bistream.*;
import io.grpc.stub.StreamObserver;

import java.io.*;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

//
public class BiStreamClient {
    private ManagedChannel channel;
    // not used because they just have Bi-directional Mode. Just need asynStub
    // private final CommunicateGrpc.CommunicateBlockingStub blockingStub;
    private CommunicateGrpc.CommunicateStub asynStub;
    private String host;
    private int port;
    private String uuid;
    private String name;
    private final ReentrantLock lock;

    public BiStreamClient(String host, int port) {
        this.host = host;
        this.port = port;
        // Build Channel and use plaintext
        this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        // Generate Stub
        // this.blockingStub = CommunicateGrpc.newBlockingStub(channel);
        this.asynStub = CommunicateGrpc.newStub(channel);
        this.uuid = UUID.randomUUID().toString();
        this.name = null;
        this.lock = new ReentrantLock();
    }

    private void start(String name) throws IOException {
        // Stdin Input and file input.
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        boolean connect = false;
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
            }

            @Override
            public void onCompleted() {
                System.out.println("onCompleted");
            }
        });
        // Join request.
        join(requestStreamObserver);

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

    public static void main(String[] args) throws IOException {
        BiStreamClient client = new BiStreamClient(args[0], Integer.parseInt(args[1]));
        System.out.printf("Connect to gRPC server: %s:%s \n", args[0], Integer.parseInt(args[1]));
        String nameStr = client.setName();
        while (true) {
            client.start(nameStr);
            System.out.println(123);
        }
    }
}
