package cn.yingming.grpc1;

import io.grpc.jchannelRpc.ViewRep;

import java.util.LinkedList;
import java.util.concurrent.locks.ReentrantLock;

public class RemoteJChannelView {
    String coordinator;
    LinkedList members;
    int num;
    public RemoteJChannelView(){
        this.coordinator = null;
        this.members = new LinkedList();
        this.num = 0;
    }
    public void setCoordinator(String coordinator){
        this.coordinator = coordinator;
    }

    public String getCoordinator(){
        return this.coordinator;
    }
    // update the local view, members, num and coordinator
    public void updateView(ViewRep view){
        LinkedList l = new LinkedList();
        l.addAll(view.getOneAddressList());
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        try{
            this.setMembers(l);
            this.setCoordinator(view.getCreator());
            this.setNum(view.getViewNum());
            System.out.println("updateView() of RemoteJChannelView.");
        } finally {
            lock.unlock();
        }
    }

    public void setMembers(LinkedList new_members){
        this.members = new_members;
    }

    public LinkedList getMembers(){
        return this.members;
    }

    public int getNum(){
        return this.num;
    }

    public void setNum(int num){
        this.num = num;
    }

    @Override
    public String toString(){
        StringBuffer sb = new StringBuffer();
        sb.append("Coordinator=").append(this.getCoordinator()).append('\n')
                .append("Member size=").append(this.getMembers().size()).append('\n')
                .append("View number=").append(this.getNum()).append('\n')
                .append("Members=").append(this.getMembers().toString()).append('\n');
        return sb.toString();
    }
}
