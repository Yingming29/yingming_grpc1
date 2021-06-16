package cn.yingming.grpc1;

import org.jgroups.*;
import org.jgroups.stack.ProtocolStack;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class RemoteJChannel extends JChannel {
    public String address;
    public String uuid;
    public String name;
    public String cluster;
    public ReentrantLock mainLock;
    public AtomicBoolean isWork;
    public ArrayList msgList;
    public String jchannel_address;
    public JChannelClientStub clientStub;
    public AtomicBoolean down;
    public RemoteJChannelView view;
    public boolean stats;
    public boolean discard_own_messages;

    public RemoteJChannel(String name, String address) throws Exception {
        this.address = address;
        // as the source of the RemoteJChannel
        this.uuid = UUID.randomUUID().toString();
        this.name = name;
        this.cluster = null;
        this.mainLock = new ReentrantLock();
        this.isWork = new AtomicBoolean(false);
        this.msgList = new ArrayList();
        // generated fake address.
        this.jchannel_address = "JChannel-" + this.name;
        this.clientStub = null;
        this.down = new AtomicBoolean(true);
        this.view = new RemoteJChannelView();
        this.stats = false;
        this.discard_own_messages = false;
    }

    @Override
    public Receiver getReceiver() {
        try {
            throw new Exception("RemoteJChannel does not have Receiver. getReceiver() always returns null.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public JChannel setReceiver(Receiver r) {
        try {
            throw new Exception("RemoteJChannel does not have Receiver. " +
                    "setReceiver() does not success.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public JChannel receiver(Receiver r) {
        try {
            throw new Exception("RemoteJChannel does not have Receiver. " +
                    "receiver() and setReceiver() does not success.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }

    @Override
    public Address getAddress() {
        return this.address();
    }

    @Override
    public Address address() {
        try {
            throw new Exception("RemoteJChannel does not have Address. " +
                    "Please use getAddressAsString() or getAddressAsUUID().");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public String getName() {
        return this.name;
    }


    public String name() {
        return this.name;
    }

    @Override
    // setName
    public JChannel name(String name) {
        return this.setName(name);
    }

    @Override
    // getClusterName
    public String clusterName() {
        return this.getClusterName();
    }

    @Override
    public View getView() {
        try {
            throw new Exception("RemoteJChannel does not have View object. " +
                    "Please use new method getRemoteJChannelView().");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public View view() {
        try {
            throw new Exception("RemoteJChannel does not have View object. " +
                    "Please use new method remoteJChannelView().");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public RemoteJChannelView getRemoteJChannelView(){
        return this.remoteJChannelView();
    }

    public RemoteJChannelView remoteJChannelView(){
        return this.isWork.get() ? this.view : null;
    }

    @Override
    public ProtocolStack getProtocolStack() {
        try {
            throw new Exception("RemoteJChannel does not have ProtocolStack object. " +
                    "getProtocolStack() always returns null.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public ProtocolStack stack() {
        try {
            throw new Exception("RemoteJChannel does not have ProtocolStack object. " +
                    "stack() always returns null.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public UpHandler getUpHandler() {
        try {
            throw new Exception("RemoteJChannel does not have UpHandler. " +
                    "getUpHandler() always returns null.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public JChannel setUpHandler(UpHandler h) {
        try {
            throw new Exception("RemoteJChannel does not have UpHandler. " +
                    "setUpHandler() just returns this RemoteJChannel object.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return this;
    }


    public boolean getStats() {
        return this.stats;
    }

    public boolean stats() {
        return this.stats;
    }

    public JChannel setStats(boolean stats) {
        this.stats = stats;
        return this;
    }

    public JChannel stats(boolean stats) {
        this.stats = stats;
        return this;
    }

    public boolean getDiscardOwnMessages() {
        return this.discard_own_messages;
    }

    public JChannel setDiscardOwnMessages(boolean flag) {
        this.discard_own_messages = flag;
        return this;
    }

    public boolean flushSupported() {
        try {
            throw new Exception("RemoteJChannel does not have flush. " +
                    "flushSupported() just returns false.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }



    public static void main(String[] args) throws Exception {
        RemoteJChannel rj = new RemoteJChannel("abc", "abc");
        rj.getReceiver();
        System.out.println("1231");
    }
}
