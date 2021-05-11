package cn.yingming.grpc1;

import io.grpc.ManagedChannelBuilder;
import io.grpc.bistream.CommunicateGrpc;

public class Test {
    public int print(){
        int a = 1;
        int b = 2;
        for (int i = 0; i < 5; i++) {
            if (a == 1){
                if (b == 2){
                    return 1;
                }
            } else{
                System.out.println("else");
            }
            System.out.println("break");
        }
        return 0;
    }
    public static void main(String[] args) {
        Test t = new Test();
        int a = t.print();
        System.out.println(a);
    }
}
