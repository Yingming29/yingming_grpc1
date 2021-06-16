package cn.yingming.grpc1;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Test {
    public static void main(String[] args) throws Exception {
        /*
        ClusterMap m = new ClusterMap("TestCreator");
        m.getMap().put("Testuuid", "TestAddress");
        byte[] b = Utils.serializeClusterInf(m);
        String a = "abvc";

        System.out.println(b);
        Object obj =  Utils.unserializeClusterInf(b);
        System.out.println(obj);
        System.out.println(obj.getClass());
        System.out.println("-------");
        ClusterMap m2 = (ClusterMap) obj;
        System.out.println(m2.getCreator());
        System.out.println(m2.getMap());


        System.out.println(b.getClass());
        System.out.println(a.getClass());

         */
        List l = new ArrayList<String>();
        l.add("312312312321");
        l.add("123");
        l.add("abc");
        l.add("12312312312");
        System.out.println(l);
        System.out.println(l.indexOf("123"));
        // RemoteJChannel j = new RemoteJChannel("3213");

    }
}
