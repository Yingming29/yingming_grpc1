package cn.yingming.grpc1;

import org.jgroups.*;
import org.jgroups.stack.AddressGenerator;
import org.jgroups.stack.ProtocolStack;
import org.jgroups.util.*;

import java.util.*;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

public class RemoteJChannel extends JChannel {
    public String address;
    public String uuid;
    public String name;
    public String cluster;
    public String jchannel_address;

    public AtomicBoolean isWork;
    public ArrayList msgList;

    // change from JChannelClientStub to RemoteJChannelStub
    public RemoteJChannelStub clientStub;
    public AtomicBoolean down;
    public RemoteJChannelView view;

    // whether receive message of itself
    public boolean discard_own_messages;
    // whether stats?
    public boolean stats;
    // record for stats of remote jchannel
    public StatsRJ stats_obj;

    public RemoteJChannel(String name, String address) throws Exception {
        this.address = address;
        // as the source of the RemoteJChannel
        this.uuid = UUID.randomUUID().toString();
        this.name = name;
        this.cluster = null;
        this.msgList = new ArrayList();
        // generated fake address.
        this.jchannel_address = "JChannel-" + this.name;
        this.clientStub = null;
        this.view = new RemoteJChannelView();

        // whether the grpc connection work
        this.isWork = new AtomicBoolean(false);
        // whether shutdown the RemoteJChannel
        this.down = new AtomicBoolean(false);
        // whether receive message of itself
        this.discard_own_messages = false;
        // whether record the stats of the RemoteJChannel
        this.stats = false;
        // change: create class for stats obj record
        this.stats_obj = null;
    }

    @Override
    public Receiver getReceiver() {
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
        if(this.clientStub != null && this.clientStub.channel != null){
            return this.clientStub.channel.getState(true).toString();
        } else{
            throw new IllegalStateException("The stub or channel of stub does not work.");
        }
    }
    @Override
    public boolean isOpen() {
        if(this.clientStub != null && this.clientStub.channel != null){
            if (!this.clientStub.channel.isTerminated() && !this.clientStub.channel.isShutdown()){
                return true;
            }
        } else{
            throw new IllegalStateException("The stub or channel of stub does not work.");
        }
        return false;
    }

    @Override
    public boolean isConnected() {
        throw new UnsupportedOperationException("RemoteJChannel does not have CONNECTED state." +
                "Please use isOpen() or getState().");
    }

    @Override
    public boolean isConnecting() {
        throw new UnsupportedOperationException("RemoteJChannel does not have CONNECTING state." +
                "Please use isOpen(), isClose() or getState().");
    }

    @Override
    public boolean isClosed() {
        if(this.clientStub != null && this.clientStub.channel != null){
            if (this.clientStub.channel.isTerminated() || this.clientStub.channel.isShutdown()){
                return true;
            }
        } else{
            throw new IllegalStateException("The stub or channel of stub does not work.");
        }
        return false;
    }

    public static String getVersion() {
        return "RemoteJChannel v0.5";
    }

    @Override
    public synchronized JChannel addChannelListener(ChannelListener listener) {
        throw new UnsupportedOperationException("RemoteJChannel does not have ChannelListener.");
    }

    @Override
    public synchronized JChannel removeChannelListener(ChannelListener listener) {
        throw new UnsupportedOperationException("RemoteJChannel does not have ChannelListener.");
    }

    @Override
    public synchronized JChannel clearChannelListeners() {
        throw new UnsupportedOperationException("RemoteJChannel does not have ChannelListener.");
    }

    @Override
    public JChannel addAddressGenerator(AddressGenerator address_generator) {
        throw new UnsupportedOperationException("RemoteJChannel does not have AddressGenerator.");
    }

    @Override
    public boolean removeAddressGenerator(AddressGenerator address_generator) {
        throw new UnsupportedOperationException("RemoteJChannel does not have AddressGenerator.");
    }

    @Override
    public String getProperties() {
        throw new UnsupportedOperationException("RemoteJChannel does not have ProtocolStack.");
    }

    @Override
    public String printProtocolSpec(boolean include_props) {
        throw new UnsupportedOperationException("RemoteJChannel does not have ProtocolStack.");
    }


    @Override
    public Map<String, Map<String, Object>> dumpStats() {
        throw new UnsupportedOperationException("RemoteJChannel does not have ProtocolStack.");
    }

    @Override
    public Map<String, Map<String, Object>> dumpStats(String protocol_name, List<String> attrs) {
        throw new UnsupportedOperationException("RemoteJChannel does not have ProtocolStack.");
    }

    @Override
    public Map<String, Map<String, Object>> dumpStats(String protocol_name) {
        throw new UnsupportedOperationException("RemoteJChannel does not have ProtocolStack.");
    }


    // incomplete
    // return object or print string?
    public StatsRJ remoteJChannelDumpStats(){
        return this.stats_obj;
    }

    @Override
    public synchronized JChannel connect(String cluster_name) throws Exception {
        if (cluster_name == null || cluster_name.equals("")){
            throw new IllegalArgumentException("The cluster_name cannot be null.");
        }
        this.cluster = cluster_name;
        boolean checkResult = this.checkProperty();
        if (checkResult){
            this.clientStub = new RemoteJChannelStub(this);
            this.clientStub.startStub();
            return this;
        } else{
            throw new IllegalStateException("The connect() does not work " +
                    "because the RemoteJchannel miss some properties.");
        }
    }

    // target is the address of grpc server.
    public synchronized JChannel connect(String cluster_name, String target) throws Exception {
        if (cluster_name == null || cluster_name.equals("")){
            throw new IllegalArgumentException("The cluster_name cannot be null.");
        } else if (target == null || target.equals("")){
            throw new IllegalArgumentException("The target cannot be null.");
        }
        this.cluster = cluster_name;
        this.address = target;
        boolean checkResult = this.checkProperty();
        if (checkResult){
            this.clientStub = new RemoteJChannelStub(this);
            this.clientStub.startStub();
            return this;
        } else{
            throw new IllegalStateException("The connect() does not work " +
                    "because the RemoteJchannel miss some properties.");
        }
    }

    private boolean checkProperty(){
        if (this.name == null || this.name.equals("")){
            throw new IllegalStateException("The name of RemoteJChannel is null.");
        } else if (this.address == null || this.address.equals("")){
            throw new IllegalStateException("The address (for grpc server) of RemoteJChannel is null.");
        } else if (this.jchannel_address == null || this.jchannel_address.equals("")){
            throw new IllegalStateException("The jchannel_address of RemoteJChannel is null.");
        } else if (this.view == null){
            throw new IllegalStateException("The view of RemoteJChannel is null.");
        } else if (this.cluster == null || this.cluster.equals("")){
            throw new IllegalStateException("The cluster of RemoteJChannel is null.");
        } else if (this.isWork.get()){
            throw new IllegalStateException("The isWork of RemoteJChannel is true.");
        } else if (this.msgList == null){
            throw new IllegalStateException("The msgList (message list) of RemoteJChannel is null.");
        } else{
            return true;
        }
    }

    @Override
    protected synchronized JChannel connect(String cluster_name, boolean useFlushIfPresent) throws Exception {
        throw new UnsupportedOperationException("RemoteJChannel does not support this connect()." +
                "PLease use connect(String cluster) or connect(String cluster, String target)");
    }
    @Override
    public synchronized JChannel connect(String cluster_name, Address target, long timeout) throws Exception {
        throw new UnsupportedOperationException("RemoteJChannel does not support this connect()." +
                "PLease use connect(String cluster) or connect(String cluster, String target)");
    }
    @Override
    public synchronized JChannel connect(String cluster_name, Address target, long timeout, boolean useFlushIfPresent) throws Exception {
        throw new UnsupportedOperationException("RemoteJChannel does not support this connect()." +
                "PLease use connect(String cluster) or connect(String cluster, String target)");
    }
    @Override
    public synchronized JChannel disconnect(){
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        try{
            String msg = "disconnect";
            this.msgList.add(msg);
        } finally {
            lock.unlock();
        }
        return this;
    }
    @Override
    public synchronized void close(){
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        try{
            String msg = "disconnect";
            this.msgList.add(msg);
        } finally {
            lock.unlock();
        }
    }
    @Override
    public JChannel send(Message msg) throws Exception {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method." +
                " Please use other send().");
    }
    @Override
    public JChannel send(Address dst, Object obj) throws Exception {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method." +
                " Please use other send().");
    }
    @Override
    public JChannel send(Address dst, byte[] buf) throws Exception {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method." +
                " Please use other send().");
    }
    @Override
    public JChannel send(Address dst, byte[] buf, int offset, int length) throws Exception {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method." +
                " Please use other send().");
    }


    // the send() just for broadcast in cluster
    public JChannel send(String msg){
        if (msg == null){
            throw new IllegalArgumentException("The msg argument cannot be null.");
        }
        MessageRJ message = new MessageRJ(msg);
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        try{
            this.msgList.add(message);
        } finally {
            lock.unlock();
        }
        return this;
    }

    // for unicast
    public JChannel send(String msg, String dst){
        if (msg == null || dst == null || msg.equals("") || dst.equals("")){
            throw new IllegalArgumentException("The msg or dst argument cannot be null.");
        }
        MessageRJ message = new MessageRJ(msg, dst);
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        try{
            this.msgList.add(message);
        } finally {
            lock.unlock();
        }
        return this;
    }

    // send byte[] with unicast
    public JChannel send(MessageRJ msg){
        if (msg == null || msg.getBuf() == null || msg.getDst() == null || msg.getDst().equals("")){
            throw new IllegalArgumentException("The msg or dst or byte[] argument cannot be null.");
        }
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        try{
            this.msgList.add(msg);
        } finally {
            lock.unlock();
        }
        return this;
    }

    // send byte[] with dst (unicast)
    public JChannel send(String dst, byte[] buf){
        if (buf == null || dst == null || dst.equals("")){
            throw new IllegalArgumentException("The byte[] or dst argument cannot be null.");
        }
        MessageRJ msg = new MessageRJ(buf, dst);
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        try{
            this.msgList.add(msg);
        } finally {
            lock.unlock();
        }
        return this;
    }

    // send byte[] without dst
    public JChannel send(byte[] buf){
        if (buf == null){
            throw new IllegalArgumentException("The byte[] argument cannot be null.");
        }
        MessageRJ msg = new MessageRJ(buf);
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        try{
            this.msgList.add(msg);
        } finally {
            lock.unlock();
        }
        return this;
    }

    @Override
    public JChannel getState(Address target, long timeout) throws Exception {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method." +
                " Please use the getStateRJ(String target)");
    }

    @Override
    public JChannel getState(Address target, long timeout, boolean useFlushIfPresent) throws Exception {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method." +
                " Please use the getStateRJ(String target)");
    }

    // change the cmd ?
    public JChannel getStateRJ(String target){
        if (this.msgList == null){
            throw new NullPointerException("The msgList is null.");
        }
        String cmd;
        if (target == null || target.equals("")){
            cmd = "getState() null";
        } else{
            cmd = "getState() " + target.trim();
        }
        ReentrantLock lock = new ReentrantLock();
        lock.lock();
        try{
            System.out.println("Give a getStateRJ() to stub.");
            this.msgList.add(cmd);
        } finally {
            lock.unlock();
        }
        return this;
    }
    @Override
    public JChannel startFlush(boolean automatic_resume) throws Exception {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method, it does not" +
                "have Flush protocol.");
    }
    @Override
    public JChannel startFlush(List<Address> flushParticipants, boolean automatic_resume) throws Exception {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method, it does not" +
                "have Flush protocol.");
    }
    @Override
    public JChannel stopFlush() {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method, it does not" +
                "have Flush protocol.");
    }
    @Override
    public JChannel stopFlush(List<Address> flushParticipants) {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method, it does not" +
                "have Flush protocol.");
    }
    @Override
    public Object down(Event evt) {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method, it does not" +
                "have Event object.");
    }
    @Override
    public Object down(Message msg) {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method, it does not" +
                "have Event object.");
    }
    @Override
    public Object up(Event evt) {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method, it does not" +
                "have Event object.");
    }
    @Override
    public Object up(Message msg) {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method, it does not" +
                "have Event object.");
    }
    @Override
    public JChannel up(MessageBatch batch) {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method, it does not" +
                "have Event object.");
    }

    @Override
    public String toString(boolean details) {
        StringBuilder sb = new StringBuilder();
        sb.append("JChannel address=").append(this.jchannel_address).append('\n').append("cluster_name=").append(this.cluster).append('\n').append("my_view=").append(this.view.toString()).append('\n').append("state=").append(this.getState()).append('\n');
        if (details) {
            sb.append("discard_own_messages=").append(this.discard_own_messages).append('\n');
            sb.append("state_transfer_supported=").append("Not support").append('\n');
            sb.append("props=").append("Not support").append('\n');
            sb.append("grpc server address=").append(this.address).append('\n');
            sb.append("available grpc server addresses=").append(this.clientStub.serverList.toString()).append('\n');
        }

        return sb.toString();
    }

    @Override
    protected boolean _preConnect(String cluster_name) throws Exception {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method.");
    }

    @Override
    protected JChannel _connect(Event evt) throws Exception {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method.");
    }

    @Override
    protected JChannel cleanup() {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method.");
    }
    @Override
    protected JChannel getState(Address target, long timeout, Callable<Boolean> flushInvoker) throws Exception {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method.");
    }
    @Override
    protected Object invokeCallback(int type, Object arg) {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method.");
    }

    @Override
    protected JChannel init() {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method.");
    }

    @Override
    protected JChannel startStack(String cluster_name) throws Exception {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method.");
    }

    @Override
    protected JChannel setAddress() {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method.");
    }
    @Override
    protected Address generateAddress() {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method.");
    }


    @Override
    protected JChannel checkClosed() {
        if (!this.down.get()) {
            throw new IllegalStateException("channel is closed");
        } else {
            return this;
        }
    }

    @Override
    protected JChannel checkClosedOrNotConnected() {
        if (!this.down.get()) {
            throw new IllegalStateException("channel is closed");
        } else {
            return this;
        }
    }
    @Override
    protected JChannel _close(boolean disconnect) {
        if (!this.down.get()) {
            return this;
        } else {
            if (disconnect) {
                this.disconnect();
            }
            return this;
        }
    }
    @Override
    protected JChannel stopStack(boolean stop, boolean destroy) {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method.");
    }
    @Override
    protected Address determineCoordinator() {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method. " +
                "Please use determineCoordinatorRJ().");
    }
    protected String determineCoordinatorRJ() {
        return this.view != null ? this.view.getCoordinator() : null;
    }
    @Override
    protected JChannel notifyChannelConnected(JChannel c) {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method.");
    }
    @Override
    protected JChannel notifyChannelDisconnected(JChannel c) {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method.");
    }
    @Override
    protected JChannel notifyChannelClosed(JChannel c) {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method.");
    }
    @Override
    protected JChannel notifyListeners(Consumer<ChannelListener> func, String msg) {
        throw new UnsupportedOperationException("RemoteJChannel does not support this method.");
    }

    public static enum State {
    }

    public static void main(String[] args) throws Exception {
        RemoteJChannel rj = new RemoteJChannel("abc", "abc");

        System.out.println(rj.getVersion());
    }
}
