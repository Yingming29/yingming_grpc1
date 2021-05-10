package cn.yingming.grpc1;

import io.grpc.bistream.StreamRequest;

import java.io.File;
import java.io.IOException;

public class Utils {
    public static String streamToStrMsg(StreamRequest req){
        String name;
        String msg;
        String timeStr;
        // confirm the String strMsg has correct format
        if (req.getName().trim() == ""){
            name = "Unknown Name";
        } else{
            name = req.getName().trim();
        }
        if (req.getMessage().trim() == ""){
            msg = "Unknown message content";
        } else{
            msg = req.getMessage().trim();
        }
        if (req.getTimestamp().trim() == ""){
            timeStr = "Unknown timestamp";
        } else {
            timeStr = req.getTimestamp().trim();
        }
        String strMsg = name + "\t" + msg + "\t" + timeStr;

        return strMsg;
    }

    public static void createTxtFile(String name) throws IOException {
        String fileName = "grpc/txt/" + name + "-signal.txt";
        File sharedFileName1 = new File(fileName);
        if (!sharedFileName1.exists()) {
            sharedFileName1.createNewFile();
            System.out.println(name + ": Create .txt file for server state and signal.");
        } else {
            sharedFileName1.delete();
            sharedFileName1.createNewFile();
            System.out.println(name + ": Delete existing the file and Create .txt file.");
        }
    }
}
