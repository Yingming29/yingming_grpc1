package cn.yingming.grpc1;

import org.apache.commons.collections.ListUtils;

import java.util.ArrayList;
import java.util.List;

public class TestSub {

    public static void main(String[] args) {
        List a = new ArrayList();
        List b = new ArrayList();

        a.add(1);
        a.add(2);
        a.add(3);
        a.add(4);
        b.add(2);
        b.add(1);
        b.add(3);
        b.add(4);
        // b.add(4);
        List result = ListUtils.subtract(a, b);
        System.out.println(result);
        System.out.println(a.equals(b));

        String test = "1:2:3";
        String[] x = test.split(":",2);
        System.out.println(x[0]);
        System.out.println(x[1]);
    }
}
