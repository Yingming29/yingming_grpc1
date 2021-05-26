package cn.yingming.grpc1;

import io.grpc.jchannelRpc.ConnectReq;
import io.grpc.jchannelRpc.DisconnectReq;
import io.grpc.jchannelRpc.MessageReq;
import io.grpc.jchannelRpc.Request;

import java.io.*;

public class Utils {

    /* convert the connect and disconnect request to String, which will be used as the content of
    message of real jchannel between nodes.
     */
    public static String conDisReqToStrMsg(Request req){
        if(req.hasConnectRequest()){
            ConnectReq conReq = req.getConnectRequest();
            // [Connect] uuid jchannel_address cluster timestamp
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
            String msg = "[Broadcast]" + " " + req.getSource() + " " + req.getJchannelAddress() + " "
                    + req.getCluster() + " " + req.getContent();
            System.out.println(msg);
            return msg;
        } else{
            String msg = "[Unicast]" + " " + req.getSource() + " " + req.getJchannelAddress() + " "
                    + req.getCluster() + " " + req.getDestination()  + " " + req.getContent();
            return msg;
        }
    }

    public static byte[] serializeClusterInf(Object obj){
        ObjectOutputStream objStream = null;
        ByteArrayOutputStream bytesStream = null;

        bytesStream = new ByteArrayOutputStream();
        try{
            objStream = new ObjectOutputStream(bytesStream);
            objStream.writeObject(obj);
            byte[] bytes = bytesStream.toByteArray();
            System.out.println("Serialize the cluster inf successfully.");
            return bytes;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try{
                objStream.close();
                bytesStream.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
        return null;
    }

    public static Object unserializeClusterInf(byte[] bytes){
        ByteArrayInputStream bytesStream = null;
        try{
            bytesStream = new ByteArrayInputStream(bytes);
            ObjectInputStream ois = new ObjectInputStream(bytesStream);
            // System.out.println("Unserialize");
            return ois.readObject();
        } catch (IOException e){
            // e.printStackTrace();
        } catch (ClassNotFoundException e){
            // e.printStackTrace();
        } finally {
            try{
                bytesStream.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
        return null;
    }
}
