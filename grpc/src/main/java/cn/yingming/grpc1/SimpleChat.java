package cn.yingming.grpc1;

import java.io.BufferedReader;

import java.io.InputStreamReader;

import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;
import org.jgroups.Receiver;
import org.jgroups.View;


public class SimpleChat implements Receiver{
	JChannel channel;
	String user_name = System.getProperty("user.name", "n/a");
	private void start() throws Exception {
		channel = new JChannel();
		channel.setReceiver(this).connect("ChatCluster");
		eventLoop();
		channel.close();
	}
	@Override
	public void viewAccepted(View new_view) {
		System.out.println("** view: " + new_view);
	}
	@Override
	public void receive(Message msg) {
		String line = msg.getSrc() + ":" + msg.getPayload();
		System.out.println("Before decode:" + line);

		String content = new String((byte[]) msg.getPayload());
		line = msg.getSrc() + ":" + content;
		System.out.println("After decode:" + line);

	}

	private void eventLoop() {
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		while (true) {
			try {
				System.out.println(">");
				// Before readLine(), clear the in stream.
				System.out.flush();
				String line = in.readLine().toLowerCase();
				if (line.startsWith("quit") || line.startsWith("exit")) {
					break;
				}
				byte[] b = line.getBytes();

				Message msg = new ObjectMessage(null, b);
				msg.setFlagIfAbsent(Message.TransientFlag.DONT_LOOPBACK);
				channel.send(msg);
			} catch (Exception e) {
				// TODO: handle exception
			}
		}
		
	}
	
	public static void main(String[] args) throws Exception {
		new SimpleChat().start();
	}
}
