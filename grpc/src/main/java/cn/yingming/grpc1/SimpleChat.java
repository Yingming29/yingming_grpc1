package cn.yingming.grpc1;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;
import org.jgroups.Receiver;
import org.jgroups.View;

public class SimpleChat implements Receiver{
    JChannel channel;
    String user_name;

    SimpleChat() throws Exception {
        this.channel = new JChannel();
        this.user_name = System.getProperty("user.name", "n/a");
        this.channel.setReceiver(this).connect("NodesCluster");
    }
    @Override
    public void viewAccepted(View new_view) {
        System.out.println("** view: " + new_view);
    }
    @Override
    public void receive(Message msg) {
        String line = msg.getSrc() + ":" + msg.getObject();
        System.out.println(line);
    }

}
