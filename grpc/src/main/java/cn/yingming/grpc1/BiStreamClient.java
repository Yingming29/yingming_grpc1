package cn.yingming.grpc1;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.bistream.*;
import io.grpc.stub.StreamObserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

//
public class BiStreamClient {
    private final ManagedChannel channel;
    // not used because they just have Bi-directional Mode. Just need asynStub
    private final CommunicateGrpc.CommunicateBlockingStub blockingStub;
    private final CommunicateGrpc.CommunicateStub asynStub;
    private String host;
    private int port;
    private String uuid;
    private String name;
    private int count;
    public BiStreamClient(String host, int port) {
        this.host = host;
        this.port = port;
        // Build Channel and use plaintext
        this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        // Generate Stub
        this.blockingStub = CommunicateGrpc.newBlockingStub(channel);
        this.asynStub = CommunicateGrpc.newStub(channel);
        this.uuid = UUID.randomUUID().toString();
        this.count = 0;
    }

    public void start(String name){
        // Service 1
        StreamObserver<StreamRequest> requestStreamObserver = asynStub.createConnection(new StreamObserver<StreamResponse>() {
            @Override
            public void onNext(StreamResponse streamResponse) {
                System.out.println(streamResponse.getTimestamp() + " [" + streamResponse.getName() + "]: " + streamResponse.getMessage());
            }

            @Override
            public void onError(Throwable throwable) {
                System.out.println(throwable.getMessage());
            }

            @Override
            public void onCompleted() {
                System.out.println("onCompleted");
            }
        });

        // Join
        System.out.println(Thread.currentThread());
        StreamRequest joinReq = StreamRequest.newBuilder()
                .setJoin(true)
                .setSource(uuid)
                .setName(name)
                .build();
        System.out.println(joinReq.toString());
        requestStreamObserver.onNext(joinReq);
        // Stdin Input
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
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
    }

    public String setName() throws IOException {

        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        System.out.println("Input Name.");
        System.out.println(">");
        System.out.flush();
        String line = in.readLine().trim();
        this.name = line;
        return line;

    }

    public static void main(String[] args) throws IOException {
        BiStreamClient client = new BiStreamClient(args[0], Integer.parseInt(args[1]));
        String nameStr = client.setName();
        client.start(nameStr);
    }
}
