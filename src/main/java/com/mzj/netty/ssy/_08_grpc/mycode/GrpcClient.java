package com.mzj.netty.ssy._08_grpc.mycode;

import com.mzj.netty.ssy._08_grpc.*;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

import java.time.LocalDateTime;
import java.util.Iterator;

/**
 * @Auther: mazhongjia
 * @Description:
 */
public class GrpcClient {

    /**
     * 方式1：输入参数与返回值都是普通的java对象
     */
    public static void client1(){
        //其中usePlaintext(true)：创建的是一个不安全的、不是用ssl证书加密的通道
        ManagedChannel managedChannel = ManagedChannelBuilder.forAddress("localhost",8080).usePlaintext().build();

        //通过grpc生成的服务stub对象调用rpc请求，这里获取的是阻塞的Stub，也就是同步调用并等待RPC调用返回结果，grpc同样也支持异步调用
        StudentServiceGrpc.StudentServiceBlockingStub blockingStub = StudentServiceGrpc.newBlockingStub(managedChannel);

        MyResponse myResponse = blockingStub.getRealNameByUsername(MyRequest.newBuilder().setUsername("huna").build());

        System.out.println(myResponse.getRealname());
    }

    /**
     * 方式2：输入参数是普通的java对象，返回值是流类型
     */
    public static void client2(){
        ManagedChannel managedChannel = ManagedChannelBuilder.forAddress("localhost",8080).usePlaintext().build();

        StudentServiceGrpc.StudentServiceBlockingStub blockingStub = StudentServiceGrpc.newBlockingStub(managedChannel);

        /**
         * 流式返回值，API编码时返回的是一个迭代器
         */
        Iterator<StudentResponse> iterator = blockingStub.getStudentsByAge(StudentRequest.newBuilder().setAge(31).build());

        while(iterator.hasNext()){
            StudentResponse studentResponse = iterator.next();
            System.out.println(studentResponse.getName()+studentResponse.getAge()+studentResponse.getCity());
        }

    }

    /**
     * 方式3：输入参数是流类型，返回值是普通的java对象
     *
     * 客户端
     */
    public static void client3(){
        //1.客户端首选创建StreamObserver对象（客户端创建的listener对象，用于传给服务端，监听服务端发送消息的回调listener）
        //这里创建的StreamObserver中回调方法是在服务端返回response后，调用，用于客户端处理服务端返回数据（关注回调方法参数类型）
        StreamObserver<StudentResponseList> studentResponseListStreamObserver = new StreamObserver<StudentResponseList>() {
            @Override
            public void onNext(StudentResponseList value) {
                value.getStudentResponseList().forEach(studentResponse -> {
                    System.out.println(studentResponse.getName());
                    System.out.println(studentResponse.getAge());
                    System.out.println(studentResponse.getCity());
                    System.out.println("*********");
                });
            }

            @Override
            public void onError(Throwable t) {
                System.out.println(t.getMessage());
            }

            @Override
            public void onCompleted() {
                System.out.println("completed!");
            }
        };

        //2、创建客户端向服务端发送数据
        //****只要客户端是以流式的方式向服务端发送请求，那么交互肯定是异步的****=>典型的异步调用---回调方式的接口设计
        //而之前两种方式使用的xxxxServiceBlockingStub是同步使用的，因此需要构造可以用于异步的stub（xxxxServiceStub）
        ManagedChannel managedChannel = ManagedChannelBuilder.forAddress("localhost",8080).usePlaintext().build();
        StudentServiceGrpc.StudentServiceStub asyncStub = StudentServiceGrpc.newStub(managedChannel);
        //通过asyncStub可以调用IDL中声明的所有rpc service接口（异步的方式，mzj：可以对比两种方式，使用API接口--传递参数、返回值有什么不同，体会同步/异步接口的设计感），但是客户端以流行色向服务端发送的rpc请求接口，必须得是通过这种异步的方式
        StreamObserver<StudentRequest> studentRequestStreamObserver = asyncStub.getStudentsWrapperByAges(studentResponseListStreamObserver);
        //-------------异步接口--------------：
//        void asyncStub.getRealNameByUsername(MyRequest request,
//                io.grpc.stub.StreamObserver<MyResponse> responseObserver);
        //-------------同步接口--------------：
//        MyResponse myResponse = blockingStub.getRealNameByUsername(MyRequest request)

        studentRequestStreamObserver.onNext(StudentRequest.newBuilder().setAge(20).build());
        studentRequestStreamObserver.onNext(StudentRequest.newBuilder().setAge(30).build());
        studentRequestStreamObserver.onNext(StudentRequest.newBuilder().setAge(40).build());
        studentRequestStreamObserver.onNext(StudentRequest.newBuilder().setAge(50).build());

        studentRequestStreamObserver.onCompleted();
        //mzj：这里设计的服务端/客户端交互接口，跟我水电GDI设计的异步接口区别在（注意，这里写的区别仅是API接口设计）：
        //grpc异步接口设计，接口返回值设计成StreamObserver<StudentRequest>，而我设计成public GDIFuture<GDIResponse>  method(GDLListener listener)
        //区别在于，我是①通过返回值让客户端持有并可以同步获取请求结果②通过参数listener被动通知（回调传递返回结果）
        //而grpc是通过返回值让客户端可以调用onNext发送流式request请求
        //设计的目的不同，初衷不同。。。。
    }

    /**
     * 方式4：输入参数是流类型，返回值也是流类型
     */
    public static void client4(){
        //1.客户端首选创建StreamObserver对象（用于传给服务端监听服务端发送消息的listener）
        StreamObserver<StreamResponse> responseStreamObserver = new StreamObserver<StreamResponse>() {
            @Override
            public void onNext(StreamResponse value) {
                System.out.println(value.getResponseInfo());
            }

            @Override
            public void onError(Throwable t) {
                System.out.println(t.getMessage());
            }

            @Override
            public void onCompleted() {
                System.out.println("completed!");
            }
        };
        //2、客户端向服务端发送数据
        ManagedChannel managedChannel = ManagedChannelBuilder.forAddress("localhost",8080).usePlaintext().build();
        StudentServiceGrpc.StudentServiceStub asyncStub = StudentServiceGrpc.newStub(managedChannel);

        StreamObserver<StreamRequest> requestStreamObserver = asyncStub.biTalk(responseStreamObserver);

        for(int i=0;i<10;i++){
            requestStreamObserver.onNext(StreamRequest.newBuilder().setRequestInfo(LocalDateTime.now().toString()).build());
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
//        client1();
//        client2();

//        client3();

        client4();
        //测试client3、client4时（客户端以流式向服务端发送数据），由于通信是异步的，所以要保证主线程不退出，才能完成调用，测试出效果
        Thread.sleep(10000);
    }
}
