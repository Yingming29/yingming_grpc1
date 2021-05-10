package cn.yingming.grpc1;

import io.grpc.bistream.StreamRequest;

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

}
