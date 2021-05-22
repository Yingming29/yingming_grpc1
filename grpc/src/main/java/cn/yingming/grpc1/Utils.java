package cn.yingming.grpc1;

import io.grpc.jchannelRpc.ConnectReq;
import io.grpc.jchannelRpc.DisconnectReq;
import io.grpc.jchannelRpc.MessageReq;
import io.grpc.jchannelRpc.Request;

import java.io.File;
import java.io.IOException;

public class Utils {

    /* convert the connect and disconnect request to String, which will be used as the content of
    message of real jchannel between nodes.
     */
    public static String conDisReqToStrMsg(Request req){
        if(req.hasConnectRequest()){
            ConnectReq conReq = req.getConnectRequest();
            String msg = "[Connect]" + " " + conReq.getSource() + " " + conReq.getJchannelAddress() +
                    " " + conReq.getCluster() + " " + conReq.getTimestamp();
            return msg;
        } else if (req.hasDisconnectRequest()){
            DisconnectReq disReq = req.getDisconnectRequest();
            String msg = "[Disconnect]" + " " + disReq.getSource() + " " + disReq.getJchannelAddress() +
                    " " + disReq.getCluster() + " " + disReq.getTimestamp();
            return msg;
        } else{
            System.out.println("Can not know the type of Request.");
        }
        return null;
    }
    public static String msgReqToMsgStr(MessageReq req){
        if (req.getDestination().equals("") ||req.getDestination()==null){
            String msg = "[Broadcast]" + " " + req.getSource() + "" + req.getJchannelAddress() + " "
                    + req.getCluster() + " " + req.getContent() + " " + req.getTimestamp();
            return msg;
        } else{
            String msg = "[Unicast]" + " " + req.getSource() + "" + req.getJchannelAddress() + " "
                    + req.getCluster() + " " + req.getContent() + " " + req.getTimestamp() + " "
                    + req.getDestination();
            return msg;
        }
    }
}
