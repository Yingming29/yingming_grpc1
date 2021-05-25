package cn.yingming.grpc1;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

import com.google.protobuf.InvalidProtocolBufferException;
import io.grpc.jchannelRpc.ConnectReq;
import io.grpc.jchannelRpc.Request;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.ObjectMessage;
import org.jgroups.Receiver;
import org.jgroups.View;
import org.jgroups.util.Util;

public class SimpleChat implements Receiver{
	JChannel channel;
	String user_name = System.getProperty("user.name", "n/a");
	final List<String> state = new LinkedList<String>();
	private void start() throws Exception {
		channel = new JChannel();
		channel.setReceiver(this).connect("ChatCluster");
		channel.getState(null, 10000);
		eventLoop();
		channel.close();
	}
	@Override
	public void viewAccepted(View new_view) {
		System.out.println("** view: " + new_view);
	}
	@Override
	public void receive(Message msg) {
		String line = msg.getSrc() + ":" + msg.getObject();
		System.out.println("Before:" + line);
		try {
			/*
			Request req = Request.parseFrom((byte[]) msg.getPayload());
			System.out.println("After:" + req);
			ConnectReq req2 = req.getConnectRequest();
			System.out.println(req2);
			 */
			Object obj =  Utils.unserializeClusterInf((byte[]) msg.getPayload());
			System.out.println(obj);
			System.out.println("-------");
			ClusterMap m2 = (ClusterMap) obj;
			System.out.println(m2.getCreator());
			System.out.println(m2.getMap());
		} catch (Exception e) {
			e.printStackTrace();
		}

		synchronized (state) {
			state.add(line);
		}
	}
	@Override
	public void getState(OutputStream output) throws Exception {
		synchronized (state) {
			Util.objectToStream(state, new DataOutputStream(output));
		}
	}
	@Override
	public void setState(InputStream input) throws Exception{
		List<String> list;
		list = (List<String>)Util.objectFromStream(new DataInputStream(input));
		synchronized (state) {
			state.clear();
			state.addAll(list);
		}
		System.out.println(list.size() + " messages in chat history.");
		list.forEach(System.out::println);
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
				/*
				ConnectReq crq = ConnectReq.newBuilder()
						.setSource("source")
						.setCluster(line)
						.build();
				Request req = Request.newBuilder()
						.setConnectRequest(crq)
						.build();
						byte[] b =  req.toByteArray();
				*/
				ClusterMap m = new ClusterMap("TestCreator");
				m.getMap().put("Testuuid", "TestAddress");
				byte[] b = Utils.serializeClusterInf(m);

				// destination address is null, send msg to everyone in the cluster.
				Message msg = new ObjectMessage(null, b);
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
