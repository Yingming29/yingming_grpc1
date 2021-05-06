package cn.yingming.grpc1;

import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import cn.yingming.grpc1.Utils;

public class test {

    public static void main(String[] args) throws Exception {
        // System.out.println("line1 " +"\n" + "line2");
        //Utils.createTXTFile("Node1");
        List<String> msgList = new ArrayList<>();
        Utils.readGrpcTxt(msgList,"Node1");
        //Utils.readNodeTxt(msgList,"Node1");
        Utils.clearTxt("grpc/txt/Node1-ClientMsg.txt");

    }
}
