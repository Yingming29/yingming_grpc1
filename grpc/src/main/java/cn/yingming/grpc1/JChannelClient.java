package cn.yingming.grpc1;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class JChannelClient {
    public String address;
    public String uuid;
    public String name;
    public String cluster;
    // lock of main thread
    private final ReentrantLock mainLock;
    public AtomicBoolean isWork;
    public ArrayList msgList;
    public String jchannel_address;
    public JChannelClientStub clientStub;
    public AtomicBoolean down;
    public RemoteJChannelView view;

    public JChannelClient(String address) {
        this.address = address;
        this.name = null;
        this.jchannel_address = null;
        this.uuid = UUID.randomUUID().toString();
        this.mainLock = new ReentrantLock();
        this.isWork = new AtomicBoolean(true);
        this.cluster = null;
        this.msgList = new ArrayList();
        this.clientStub = null;
        this.down = new AtomicBoolean(true);
        this.view = new RemoteJChannelView();
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

    private void startClientStub(){
        this.clientStub = new JChannelClientStub(this);
    }

    private void inputLoop(){
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            String line = null;
            try {
                // Input line
                System.out.println(">");
                System.out.flush();
                line = in.readLine();

                if(!this.down.get()){
                    break;
                }

                // Check the isWork, and do action.Add message to that shared message list or print error.
                if (!isWork.get()) {
                    System.out.println("The connection does not work. Store the message.");
                }

                // store the message to
                mainLock.lock();
                try {
                    this.msgList.add(line);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    mainLock.unlock();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
            if (line.equals("disconnect")){
                break;
            }
        }
    }

    private void clientStart() throws IOException {
        // set name and cluster
        this.setName();
        this.setCluster();
        // start the stub.
        this.startClientStub();
        this.clientStub.startStub();
        this.inputLoop();
        System.exit(0);
    }
    public static void main(String[] args) {
        JChannelClient client = new JChannelClient(args[0]);
        System.out.printf("Start java client: Will connect to gRPC server: %s \n", args[0]);
        try{
            client.clientStart();
        } catch (Exception e){
            e.printStackTrace();
        }
    }

}
