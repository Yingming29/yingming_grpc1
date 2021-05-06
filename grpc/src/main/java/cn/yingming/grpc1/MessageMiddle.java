package cn.yingming.grpc1;

public class MessageMiddle {
    String msgName;
    String msgMessage;
    String msgTimestamp;

    public MessageMiddle(String msgName, String msgMessage, String msgTimestamp){
        this.msgName = msgName;
        this.msgMessage = msgMessage;
        this.msgTimestamp = msgTimestamp;
    }
    //.
    public static String parseMsgToLine(MessageMiddle msg) throws Exception {
        String line;
        if (msg.msgName != null && msg.msgMessage != null && msg.msgTimestamp != null ){
            line = msg.msgName + "\t" + msg.msgMessage + "\t" + msg.msgTimestamp;
            System.out.println(line);
        } else{
            throw new Exception("");
        }
        return line.trim();
    }
}
