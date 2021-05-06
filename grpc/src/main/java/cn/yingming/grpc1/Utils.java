package cn.yingming.grpc1;

import java.io.File;
import java.io.IOException;

public class Utils {
    public static void parseMessage() {

    }
    public static boolean createTXTFile(String nodeName) throws IOException {
        String fileName = "grpc/txt/" + nodeName + "-ClientMsg.txt";
        File sharedFileName1 = new File(fileName);
        if (!sharedFileName1.exists()){
            sharedFileName1.createNewFile();
            System.out.println(nodeName + ": Create .txt file for storing message from clients.");
        } else{
            sharedFileName1.delete();
            sharedFileName1.createNewFile();
            System.out.println(nodeName + ": Delete existing the file and Create .txt file for storing message from clients.");
        }
        String fileName2 = "grpc/txt/" + nodeName + "-NodesMsg.txt";
        File sharedFileName2 = new File(fileName2);
        if (!sharedFileName2.exists()){
            sharedFileName2.createNewFile();
            System.out.println(nodeName + ": Create .txt file for storing message from nodes.");
        } else{
            sharedFileName2.delete();
            sharedFileName2.createNewFile();
            System.out.println(nodeName + ": Delete existing the file and Create .txt file for storing message from nodes.");
        }
        if (sharedFileName1.exists()&& sharedFileName2.exists()){
            return true;
        } else{
            return false;
        }
    }
}
