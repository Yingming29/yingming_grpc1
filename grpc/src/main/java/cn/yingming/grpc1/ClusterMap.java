package cn.yingming.grpc1;

import io.grpc.jchannelRpc.ViewRep;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

public class ClusterMap implements Serializable {
    public ConcurrentHashMap<String, String> map;
    public int viewNum;
    public String creator;
    public ReentrantLock lock;
    public ClusterMap(String creator){
        this.map = new ConcurrentHashMap<String, String>();
        this.viewNum = 0;
        this.creator = creator;
        this.lock = new ReentrantLock();
    }
    public ConcurrentHashMap getMap(){
        return this.map;
    }
    // add the methods for view
    public int getViewNum(){
        return viewNum;
    }
    public void addViewNum(){
        viewNum ++;
    }
    public String getCreator(){ return creator;}
    public ViewRep generateView(){
        List clientList = new ArrayList();
        ViewRep rep = null;
        this.lock.lock();
        try{
            for (String eachUuid:this.map.keySet()) {
                String addStr = this.map.get(eachUuid);
                clientList.add(addStr);
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
}
/*
    class ClusterMap{
        public ConcurrentHashMap<String, String> map;
        public int viewNum;
        public String creator;
        public ReentrantLock lock;
        public ClusterMap(String creator){
            this.map = new ConcurrentHashMap<String, String>();
            this.viewNum = 0;
            this.creator = creator;
            this.lock = new ReentrantLock();
        }
        public ConcurrentHashMap getMap(){
            return map;
        }
        // add the methods for view
        public int getViewNum(){
            return viewNum;
        }
        public void addViewNum(){
            viewNum ++;
        }
        public String getCreator(){ return creator;}
        public ViewRep generateView(){
            List clientList = new ArrayList();
            ViewRep rep = null;
            this.lock.lock();
            try{
                for (String eachUuid:this.map.keySet()) {
                    String addStr = this.map.get(eachUuid);
                    clientList.add(addStr);
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
    }
 */