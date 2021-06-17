package cn.yingming.grpc1;

import io.grpc.jchannelRpc.MessageReq;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class Test3 {

    public Test3(){

    }

    public void method(){
        try{
            throw new IllegalStateException("123");
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    public static void main(String[] args) {
        ArrayList l = new ArrayList();
        int a = 1;
        String b = "2";
        MessageRJ c = new MessageRJ();
        byte[] d = "asdas".getBytes(StandardCharsets.UTF_8);
        l.add(a);
        l.add(b);
        l.add(c);
        l.add(d);
        for (int i = 0; i < l.size(); i++) {
            System.out.println(l.get(i).getClass());
        }

        MessageRJ msg = new MessageRJ("str1", "str2");
        System.out.println(msg.getBuf());
        MessageReq msgReq1 = MessageReq.newBuilder()
                .setSource("uuid")
                .setJchannelAddress("jchannaddr")
                .setCluster("cluster")
                .setContent("content")
                .setTimestamp("time")
                .setDestination("dst")
                .build();

        MessageReq msgReq2 = MessageReq.newBuilder()
                .setSource("uuid")
                .setJchannelAddress("jchannaddr")
                .setCluster("cluster")
                .setContent("content")
                .setTimestamp("time")
                .setDestination(null)
                .build();
        System.out.println(msgReq1);
        System.out.println(msgReq1);

    }
}
