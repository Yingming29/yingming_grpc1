package cn.yingming.grpc1;

import io.grpc.jchannelRpc.ViewRep;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class ClusterMap implements Serializable {
    public ConcurrentHashMap<String, String> map;
    public int viewNum;
    public String creator;
    public ReentrantLock lock;
    public ArrayList orderList;
    public ClusterMap(String creator){
        this.map = new ConcurrentHashMap<String, String>();
        this.viewNum = 0;
        this.creator = creator;
        this.lock = new ReentrantLock();
        this.orderList = new ArrayList<String>();
    }
    public ConcurrentHashMap getMap(){
        return this.map;
    }

    public void removeClient(String uuid){
        Iterator<String> it = this.orderList.iterator();
        String target = this.map.get(uuid).trim();
        int index = orderList.indexOf(target);
        if (index != -1){
            System.out.println("Remove the client from its cluster.");
            orderList.remove(index);
        } else{
            System.out.println("The client does not exist in the cluster.");
        }

    }
    public int getViewNum(){
        return viewNum;
    }
    public void addViewNum(){
        viewNum ++;
    }
    public String getCreator(){ return (String) this.orderList.get(0);}
    public ViewRep generateView(){
        ViewRep rep = null;
        this.lock.lock();
        try{
            //List clientList = this.orderList;
            List clientList = new ArrayList();
            for (Object each:this.getMap().keySet()) {
                clientList.add(this.getMap().get(each.toString()));
            }
            rep = ViewRep.newBuilder()
                    .setCreator(getCreator())
                    .setViewNum(getViewNum())
                    .setSize(clientList.size())
                    .setJchannelAddresses(clientList.toString())
                    .build();
            addViewNum();
        } finally {
            this.lock.unlock();
        }
        return rep;
    }
    public ArrayList getList(){
        return this.orderList;
    }
}