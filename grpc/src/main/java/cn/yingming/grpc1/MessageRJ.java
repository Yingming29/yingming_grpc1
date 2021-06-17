package cn.yingming.grpc1;

public class MessageRJ {
    byte[] buf;
    String dst;
    String msg;
    public MessageRJ(){
        this.buf = null;
        this.dst = null;
        this.msg = null;
    }
    public MessageRJ(byte[] buf, String dst){
        this.buf = buf;
        this.dst = dst;
    }
    public MessageRJ(byte[] buf){
        this.buf = buf;
        this.dst = null;
    }
    public MessageRJ(String msg){
        this.msg = msg;
        this.dst = null;
    }
    public MessageRJ(String msg, String dst){
        this.dst = dst;
        this.msg = msg;
    }

    public boolean check(){
        if (this.getMsg() != null && this.getBuf() != null){
            return false;
        } else{
            return true;
        }
    }

    public void setBuf(byte[] buf) {
        this.buf = buf;
    }

    public void setDst(String dst) {
        this.dst = dst;
    }

    public byte[] getBuf() {
        return buf;
    }

    public String getDst() {
        return dst;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public String getMsg() {
        return msg;
    }
}
