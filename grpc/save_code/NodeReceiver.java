package cn.yingming.grpc1;

import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.View;

public class NodeReceiver implements Receiver {
    NodeReceiver(){

    }
    @Override
    public void viewAccepted(View new_view) {
        System.out.println("** view: " + new_view);
        System.out.println(new_view.getMembers());
    }
    @Override
    public void receive(Message msg) {
        String line = msg.getSrc() + ": " + msg.getObject();
        System.out.println(line);
    }

}
