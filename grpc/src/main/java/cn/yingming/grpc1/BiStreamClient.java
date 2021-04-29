package cn.yingming.grpc1;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.bistream.CommunicateGrpc;
import io.grpc.bistream.StreamRequest;
import io.grpc.bistream.StreamResponse;
import io.grpc.stub.StreamObserver;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class BiStreamClient {
    private final ManagedChannel channel;
    // not used because they just have Bi-directional Mode. Just need asynStub
    private final CommunicateGrpc.CommunicateBlockingStub blockingStub;
    private final CommunicateGrpc.CommunicateStub asynStub;
    private static final String host = "127.0.0.1";
    private static final int port = 50051;
    private String uuid;

    public BiStreamClient(String host, int port) {
        // Build Channel and use plaintext
        channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        // Generate Stub
        // System.out.println(channel.toString());
        blockingStub = CommunicateGrpc.newBlockingStub(channel);
        asynStub = CommunicateGrpc.newStub(channel);
        uuid = UUID.randomUUID().toString();
    }

    public void start(String name){
        // There is also a StreamObserver for checking the continued requests.
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
        // Join..
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
        String line = in.readLine();
        return line;
    }
    public static void main(String[] args) throws IOException {
        BiStreamClient client = new BiStreamClient(host, port);
        String nameStr = client.setName();
        client.start(nameStr);
    }
}
