package cn.yingming.grpc1;

import java.io.File;
import java.io.IOException;

public class Utils {
    public static void parseMessage() {

    }
    public static void createTXTFile(String nodeName) throws IOException {
        // "txtFile/" +
        String fileName = nodeName + "-ClientMsg.txt";
        File sharedFileName = new File(fileName);
        if (!sharedFileName.exists()){
            sharedFileName.createNewFile();
            System.out.println("Create .txt file for storing message from clients.");
        }
    }
}
