package cn.yingming.grpc1;

public class TestSub extends Test2{

    String name;
    int age;

    TestSub(){
        this.name = "sub";
        this.age = 77;
        System.out.println("son");
        System.out.println(this.name + ":" + this.age);
    }

    public static void main(String[] args) {
        TestSub ts = new TestSub();
        ts.fathermethod();
    }
}
