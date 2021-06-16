package cn.yingming.grpc1;

public class Test2 {
    String name;
    int age;
    Test2(String name, int age){
        this.name = name;
        this.age = age;
        System.out.println("father");
    }
    Test2(){
        this.name = "default";
        this.age = 0;
        System.out.println("father");
    }
    public void fathermethod(){
        System.out.println("123");
    }

}
