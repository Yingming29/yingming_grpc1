package cn.yingming.grpc1;

import io.grpc.bistream.StreamRequest;
import io.grpc.bistream.StreamResponse;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

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

    public static void parseMessage() {

    }

    // Create two .txt files for storing messages from gRPC clients and JChannels.
    public static boolean createTxtFile(String nodeName) throws IOException {
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

    // Read .txt file storing msg from gRpc clients
    public static void readGrpcTxt(List msgList, String nodeName){
        String filePath = "grpc/txt/" + nodeName + "-ClientMsg.txt";
        // List<String> msgList = new ArrayList<>();
        try {
            msgList = Files.readAllLines(Paths.get(filePath));
        } catch (IOException e){
            e.printStackTrace();
        }
        if (msgList.size() == 0){
            System.out.println("Not receive message from gRPC clients.");
        } else{
            //System.out.println(msgList);
            System.out.println("Receive message from gRPC clients.");
        }
    }

    // Read .txt file storing msg from nodes.
    public static void readNodeTxt(List msgList, String nodeName){
        String filePath = "grpc/txt/" + nodeName + "-NodesMsg.txt";
        // List<String> msgList = new ArrayList<>();
        try {
            msgList = Files.readAllLines(Paths.get(filePath));
        } catch (IOException e){
            e.printStackTrace();
        }
        if (msgList.size() == 0){
            System.out.println("Not receive message from Nodes.");
        } else{
            System.out.println(msgList);
            System.out.println("Receive message from Nodes.");
        }
    }
    /*
    // Add the new message as a line into .txt file.
    public static void addMsgToTxt(MessageMiddle msg, String nodeName, String type) throws Exception {
        String filePath;
        String msgLine;

        // Get file path by type.
        if (type == "Node"){
            filePath = "grpc/txt/" + nodeName + "-NodesMsg.txt";
        } else if (type == "Grpc"){
            filePath = "grpc/txt/" + nodeName + "-ClientMsg.txt";
        } else {
            throw new Exception("Error type.");
        }
        // convert msg to String line
        msgLine = parseMsgToLine(msg);
        // add line to .txt file.
        FileWriter fileWriter = new FileWriter(new File(filePath), true);
        BufferedWriter bufferedWriter = new BufferedWriter(fileWriter);
        try{
            bufferedWriter.write(msgLine + "\n");
        } finally {
            bufferedWriter.close();
            fileWriter.close();
        }

    }

     */

    // Clear lines in .txt file.
    public static void clearTxt(String filePath) throws Exception {
        File file = new File(filePath);
        FileWriter fileWriter = new FileWriter(file);
        try{
            if(!file.exists()) {
                throw new Exception(filePath + ": In Clear .txt, file is not existing.");
            } else{
                fileWriter.write("");
                fileWriter.flush();

            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // At the end, it must close the file writer stream.
            fileWriter.close();
        }
    }
}
