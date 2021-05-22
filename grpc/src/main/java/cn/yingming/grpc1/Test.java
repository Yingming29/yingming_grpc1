package cn.yingming.grpc1;

import java.util.concurrent.ConcurrentHashMap;

public class Test {
    ConcurrentHashMap serviceMap;
    public Test() {
        this.serviceMap = new ConcurrentHashMap<String, ClusterMap>();
    }
    private static class ClusterMap{
        protected ConcurrentHashMap<String, String> map;
        protected int viewSize;
        public ClusterMap(){
            this.map = new ConcurrentHashMap<String, String>();
            this.viewSize = 0;
        }
        public ConcurrentHashMap getMap(){
            return map;
        }
        public int getViewSize(){
            return viewSize;
        }
    }
    public static void main(String[] args) {
        Test t = new Test();
        ClusterMap newCluster = new ClusterMap();
        t.serviceMap.put("test1", newCluster);
        newCluster.getMap().put("abc", "bcd");
        // ClusterMap m = t.serviceMap.get("test1");
        System.out.println(t.serviceMap.get("test1"));
    }
}
