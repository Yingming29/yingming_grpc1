package cn.yingming.grpc1;

import java.io.*;

public class TestStream {
    BufferedWriter writer = new BufferedWriter(new FileWriter("grpc/txt/user-signal.txt"));
    BufferedReader reader = new BufferedReader(new FileReader("grpc/txt/user-signal.txt"));
    BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
    String line;
    public TestStream() throws IOException {
    }
    public void start() throws IOException {
        while((line = in.readLine()) !=null ){
            System.out.println("IN");
        }
    }
    public static void main(String[] args) throws IOException {
        TestStream test = new TestStream();
        test.start();
    }
}
