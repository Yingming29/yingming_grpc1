package cn.yingming.grpc1;

import io.grpc.jchannelRpc.ConnectReq;
import io.grpc.jchannelRpc.DisconnectReq;
import io.grpc.jchannelRpc.MessageReq;
import io.grpc.jchannelRpc.Request;

import java.io.*;

public class Utils {

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
