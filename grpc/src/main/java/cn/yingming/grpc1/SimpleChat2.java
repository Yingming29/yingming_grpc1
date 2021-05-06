package cn.yingming.grpc1;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.util.Util;

public class SimpleChat2 implements Receiver{
    JChannel channel;
    String user_name = System.getProperty("user.name", "n/a");
    final List<String> state = new LinkedList<String>();
    private void start() throws Exception {
        channel = new JChannel();
        channel.setReceiver(this).connect("NodesCluster");
        channel.getState(null, 10000);
        eventLoop();
        channel.close();
    }
    @Override
    public void viewAccepted(View new_view) {
        System.out.println("** view: " + new_view);
    }
    @Override
    public void receive(Message msg) {
        String line = msg.getSrc() + ":" + msg.getObject();
        System.out.println(line);
        synchronized (state) {
            state.add(line);
        }
    }
    @Override
    public void getState(OutputStream output) throws Exception {
        synchronized (state) {
            Util.objectToStream(state, new DataOutputStream(output));
        }
    }
    @Override
    public void setState(InputStream input) throws Exception{
        List<String> list;
        list = (List<String>)Util.objectFromStream(new DataInputStream(input));
        synchronized (state) {
            state.clear();
            state.addAll(list);
        }
        System.out.println(list.size() + " messages in chat history.");
        list.forEach(System.out::println);
    }
    private void eventLoop() {
        BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            try {
                System.out.println(">");
                // Before readLine(), clear the in stream.
                System.out.flush();
                String line = in.readLine().toLowerCase();
                if (line.startsWith("quit") || line.startsWith("exit")) {
                    break;
                }
                line = "[" + user_name + "]" + line;
                // destination address is null, send msg to everyone in the cluster.
                Message msg = new ObjectMessage(null, line);
                channel.send(msg);
            } catch (Exception e) {
                // TODO: handle exception.
            }
        }

    }

    public static void main(String[] args) throws Exception {
        new SimpleChat2().start();
    }
}