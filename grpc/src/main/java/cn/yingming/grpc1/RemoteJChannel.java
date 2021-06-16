package cn.yingming.grpc1;

import org.jgroups.*;
import org.jgroups.stack.ProtocolStack;
import org.jgroups.util.NameCache;

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
        /*
        try {
            throw new Exception("RemoteJChannel does not have Receiver. getReceiver() always returns null.");
        } catch (Exception e) {
            e.printStackTrace();
        }

         */
        throw new UnsupportedOperationException("RemoteJChannel does not have Receiver. " +
                "getReceiver() does not return anything.");
    }

    @Override
    public JChannel setReceiver(Receiver r) {
        throw new UnsupportedOperationException("RemoteJChannel does not have Receiver. " +
                "setReceiver() does not return anything.");
    }

    @Override
    public JChannel receiver(Receiver r) {
        throw new UnsupportedOperationException("RemoteJChannel does not have Receiver. " +
                "receiver() does not return anything.");
    }

    @Override
    public Address getAddress() {
        return this.address();
    }

    @Override
    public Address address() {
        throw new UnsupportedOperationException("RemoteJChannel does not have Address. " +
                "Please use getAddressAsString() and getAddressAsUUID().");
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
        throw new UnsupportedOperationException("RemoteJChannel does not have View object. " +
                "Please use new method getRemoteJChannelView().");
    }

    @Override
    public View view() {
        throw new UnsupportedOperationException("RemoteJChannel does not have View object. " +
                "Please use new method remoteJChannelView().");
    }

    public RemoteJChannelView getRemoteJChannelView(){
        return this.remoteJChannelView();
    }

    public RemoteJChannelView remoteJChannelView(){
        return this.isWork.get() ? this.view : null;
    }

    @Override
    public ProtocolStack getProtocolStack() {
        throw new UnsupportedOperationException("RemoteJChannel does not have ProtocolStack object. " +
                "getProtocolStack() does not return anything.");
    }

    @Override
    public ProtocolStack stack() {
        throw new UnsupportedOperationException("RemoteJChannel does not have ProtocolStack object. " +
                "stack() does not return anything.");
    }

    @Override
    public UpHandler getUpHandler() {
        throw new UnsupportedOperationException("RemoteJChannel does not have UpHandler. " +
                "getUpHandler() does not return anything.");
    }

    @Override
    public JChannel setUpHandler(UpHandler h) {
        throw new UnsupportedOperationException("RemoteJChannel does not have UpHandler. " +
                "setUpHandler() does not return anything.");
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
        throw new UnsupportedOperationException("RemoteJChannel does not have flush. " +
                "flushSupported() does not return anything.");
    }

    @Override
    /*
    The methods returns the generated FAKE jchannel address. eg. JChannel-xxx
     */
    public String getAddressAsString() {
        return this.jchannel_address != null ? this.jchannel_address : "n/a";
    }

    @Override
    /*
    The methods returns the generated uuid.
     */
    public String getAddressAsUUID() {
        return this.uuid != null ? this.uuid : null;
    }

    @Override
    public JChannel setName(String name) {
        if (name != null) {
            if (this.isWork.get()) {
                throw new IllegalStateException("name cannot be set if channel is connected (should be done before)");
            }
            if (this.name != null) {
                throw new IllegalStateException("name cannot be set if channel has name property. ");
            }
            this.name = name;
            /*
            if (this.local_addr != null) {

                NameCache.add(this.local_addr, this.name);
            }

             */
        }
        return this;
    }

    @Override
    public String getClusterName() {
        return this.isWork.get() ? this.cluster : null;
    }

    @Override
    public String getViewAsString() {
        if (isWork.get() && this.view != null){
            return this.view.toString();
        } else{
            throw new IllegalStateException("View cannot be get if channel is not connected or does not have View");
        }
    }

    @Override
    public String getState() {
        return this.clientStub.;
    }




    public static void main(String[] args) throws Exception {
        RemoteJChannel rj = new RemoteJChannel("abc", "abc");
        rj.getReceiver();
        System.out.println("1231");
    }
}
