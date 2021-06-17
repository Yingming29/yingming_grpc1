package cn.yingming.grpc1;

import java.util.ArrayList;

public class RemoteJChannelView {

    String coordinator;
    ArrayList members;
    int num;
    public RemoteJChannelView(){
        this.coordinator = null;
        this.members = new ArrayList();
        this.num = 0;
    }
    public void setCoordinator(String coordinator){
        this.coordinator = coordinator;
    }

    public String getCoordinator(){
        return this.coordinator;
    }

    public void setMembers(ArrayList newView){
        this.members = newView;
    }

    public ArrayList getMembers(){
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
        String str = "** View:[" + this.getCoordinator() + "|" + this.getNum() +
                "] (" + this.getMembers().size() + ")" + this.getMembers().toString();
        return str;
    }
}
