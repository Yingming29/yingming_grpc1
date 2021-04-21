package io.grpc.bistream;

import static io.grpc.MethodDescriptor.generateFullMethodName;

/**
 * <pre>
 * define the service
 * </pre>
 */
@javax.annotation.Generated(
    value = "by gRPC proto compiler (version 1.37.0)",
    comments = "Source: bistream.proto")
public final class CommunicateGrpc {

  private CommunicateGrpc() {}

  public static final String SERVICE_NAME = "cn.yingming.grpc1.Communicate";

  // Static method descriptors that strictly reflect the proto.
  private static volatile io.grpc.MethodDescriptor<io.grpc.bistream.StreamRequest,
      io.grpc.bistream.StreamResponse> getCreateConnectionMethod;

  @io.grpc.stub.annotations.RpcMethod(
      fullMethodName = SERVICE_NAME + '/' + "createConnection",
      requestType = io.grpc.bistream.StreamRequest.class,
      responseType = io.grpc.bistream.StreamResponse.class,
      methodType = io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
  public static io.grpc.MethodDescriptor<io.grpc.bistream.StreamRequest,
      io.grpc.bistream.StreamResponse> getCreateConnectionMethod() {
    io.grpc.MethodDescriptor<io.grpc.bistream.StreamRequest, io.grpc.bistream.StreamResponse> getCreateConnectionMethod;
    if ((getCreateConnectionMethod = CommunicateGrpc.getCreateConnectionMethod) == null) {
      synchronized (CommunicateGrpc.class) {
        if ((getCreateConnectionMethod = CommunicateGrpc.getCreateConnectionMethod) == null) {
          CommunicateGrpc.getCreateConnectionMethod = getCreateConnectionMethod =
              io.grpc.MethodDescriptor.<io.grpc.bistream.StreamRequest, io.grpc.bistream.StreamResponse>newBuilder()
              .setType(io.grpc.MethodDescriptor.MethodType.BIDI_STREAMING)
              .setFullMethodName(generateFullMethodName(SERVICE_NAME, "createConnection"))
              .setSampledToLocalTracing(true)
              .setRequestMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.grpc.bistream.StreamRequest.getDefaultInstance()))
              .setResponseMarshaller(io.grpc.protobuf.ProtoUtils.marshaller(
                  io.grpc.bistream.StreamResponse.getDefaultInstance()))
              .setSchemaDescriptor(new CommunicateMethodDescriptorSupplier("createConnection"))
              .build();
        }
      }
    }
    return getCreateConnectionMethod;
  }

  /**
   * Creates a new async stub that supports all call types for the service
   */
  public static CommunicateStub newStub(io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CommunicateStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<CommunicateStub>() {
        @java.lang.Override
        public CommunicateStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new CommunicateStub(channel, callOptions);
        }
      };
    return CommunicateStub.newStub(factory, channel);
  }

  /**
   * Creates a new blocking-style stub that supports unary and streaming output calls on the service
   */
  public static CommunicateBlockingStub newBlockingStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CommunicateBlockingStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<CommunicateBlockingStub>() {
        @java.lang.Override
        public CommunicateBlockingStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new CommunicateBlockingStub(channel, callOptions);
        }
      };
    return CommunicateBlockingStub.newStub(factory, channel);
  }

  /**
   * Creates a new ListenableFuture-style stub that supports unary calls on the service
   */
  public static CommunicateFutureStub newFutureStub(
      io.grpc.Channel channel) {
    io.grpc.stub.AbstractStub.StubFactory<CommunicateFutureStub> factory =
      new io.grpc.stub.AbstractStub.StubFactory<CommunicateFutureStub>() {
        @java.lang.Override
        public CommunicateFutureStub newStub(io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
          return new CommunicateFutureStub(channel, callOptions);
        }
      };
    return CommunicateFutureStub.newStub(factory, channel);
  }

  /**
   * <pre>
   * define the service
   * </pre>
   */
  public static abstract class CommunicateImplBase implements io.grpc.BindableService {

    /**
     */
    public io.grpc.stub.StreamObserver<io.grpc.bistream.StreamRequest> createConnection(
        io.grpc.stub.StreamObserver<io.grpc.bistream.StreamResponse> responseObserver) {
      return io.grpc.stub.ServerCalls.asyncUnimplementedStreamingCall(getCreateConnectionMethod(), responseObserver);
    }

    @java.lang.Override public final io.grpc.ServerServiceDefinition bindService() {
      return io.grpc.ServerServiceDefinition.builder(getServiceDescriptor())
          .addMethod(
            getCreateConnectionMethod(),
            io.grpc.stub.ServerCalls.asyncBidiStreamingCall(
              new MethodHandlers<
                io.grpc.bistream.StreamRequest,
                io.grpc.bistream.StreamResponse>(
                  this, METHODID_CREATE_CONNECTION)))
          .build();
    }
  }

  /**
   * <pre>
   * define the service
   * </pre>
   */
  public static final class CommunicateStub extends io.grpc.stub.AbstractAsyncStub<CommunicateStub> {
    private CommunicateStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CommunicateStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CommunicateStub(channel, callOptions);
    }

    /**
     */
    public io.grpc.stub.StreamObserver<io.grpc.bistream.StreamRequest> createConnection(
        io.grpc.stub.StreamObserver<io.grpc.bistream.StreamResponse> responseObserver) {
      return io.grpc.stub.ClientCalls.asyncBidiStreamingCall(
          getChannel().newCall(getCreateConnectionMethod(), getCallOptions()), responseObserver);
    }
  }

  /**
   * <pre>
   * define the service
   * </pre>
   */
  public static final class CommunicateBlockingStub extends io.grpc.stub.AbstractBlockingStub<CommunicateBlockingStub> {
    private CommunicateBlockingStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CommunicateBlockingStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CommunicateBlockingStub(channel, callOptions);
    }
  }

  /**
   * <pre>
   * define the service
   * </pre>
   */
  public static final class CommunicateFutureStub extends io.grpc.stub.AbstractFutureStub<CommunicateFutureStub> {
    private CommunicateFutureStub(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      super(channel, callOptions);
    }

    @java.lang.Override
    protected CommunicateFutureStub build(
        io.grpc.Channel channel, io.grpc.CallOptions callOptions) {
      return new CommunicateFutureStub(channel, callOptions);
    }
  }

  private static final int METHODID_CREATE_CONNECTION = 0;

  private static final class MethodHandlers<Req, Resp> implements
      io.grpc.stub.ServerCalls.UnaryMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ServerStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.ClientStreamingMethod<Req, Resp>,
      io.grpc.stub.ServerCalls.BidiStreamingMethod<Req, Resp> {
    private final CommunicateImplBase serviceImpl;
    private final int methodId;

    MethodHandlers(CommunicateImplBase serviceImpl, int methodId) {
      this.serviceImpl = serviceImpl;
      this.methodId = methodId;
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public void invoke(Req request, io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        default:
          throw new AssertionError();
      }
    }

    @java.lang.Override
    @java.lang.SuppressWarnings("unchecked")
    public io.grpc.stub.StreamObserver<Req> invoke(
        io.grpc.stub.StreamObserver<Resp> responseObserver) {
      switch (methodId) {
        case METHODID_CREATE_CONNECTION:
          return (io.grpc.stub.StreamObserver<Req>) serviceImpl.createConnection(
              (io.grpc.stub.StreamObserver<io.grpc.bistream.StreamResponse>) responseObserver);
        default:
          throw new AssertionError();
      }
    }
  }

  private static abstract class CommunicateBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoFileDescriptorSupplier, io.grpc.protobuf.ProtoServiceDescriptorSupplier {
    CommunicateBaseDescriptorSupplier() {}

    @java.lang.Override
    public com.google.protobuf.Descriptors.FileDescriptor getFileDescriptor() {
      return io.grpc.bistream.Bistream.getDescriptor();
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.ServiceDescriptor getServiceDescriptor() {
      return getFileDescriptor().findServiceByName("Communicate");
    }
  }

  private static final class CommunicateFileDescriptorSupplier
      extends CommunicateBaseDescriptorSupplier {
    CommunicateFileDescriptorSupplier() {}
  }

  private static final class CommunicateMethodDescriptorSupplier
      extends CommunicateBaseDescriptorSupplier
      implements io.grpc.protobuf.ProtoMethodDescriptorSupplier {
    private final String methodName;

    CommunicateMethodDescriptorSupplier(String methodName) {
      this.methodName = methodName;
    }

    @java.lang.Override
    public com.google.protobuf.Descriptors.MethodDescriptor getMethodDescriptor() {
      return getServiceDescriptor().findMethodByName(methodName);
    }
  }

  private static volatile io.grpc.ServiceDescriptor serviceDescriptor;

  public static io.grpc.ServiceDescriptor getServiceDescriptor() {
    io.grpc.ServiceDescriptor result = serviceDescriptor;
    if (result == null) {
      synchronized (CommunicateGrpc.class) {
        result = serviceDescriptor;
        if (result == null) {
          serviceDescriptor = result = io.grpc.ServiceDescriptor.newBuilder(SERVICE_NAME)
              .setSchemaDescriptor(new CommunicateFileDescriptorSupplier())
              .addMethod(getCreateConnectionMethod())
              .build();
        }
      }
    }
    return result;
  }
}
