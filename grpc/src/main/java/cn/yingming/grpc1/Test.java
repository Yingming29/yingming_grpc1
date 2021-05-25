package cn.yingming.grpc1;

import java.util.concurrent.ConcurrentHashMap;

public class Test {
    public static void main(String[] args) {
        ClusterMap m = new ClusterMap("TestCreator");
        m.getMap().put("Testuuid", "TestAddress");
        byte[] b = Utils.serializeClusterInf(m);
        System.out.println(b);
        Object obj =  Utils.unserializeClusterInf(b);
        System.out.println(obj);
        System.out.println("-------");
        ClusterMap m2 = (ClusterMap) obj;
        System.out.println(m2.getCreator());
        System.out.println(m2.getMap());
    }
}
