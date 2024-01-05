package com.yumi.tests;

import java.util.ArrayList;
import java.util.List;

public class CanonicalNameTest {
    public static void main(String[] args) {
        System.out.println(int.class.getCanonicalName());
        System.out.println(Integer.class.getCanonicalName());

        int[] ints = new int[0];
        System.out.println(ints.getClass().getCanonicalName());
        List<String> lst = new ArrayList<>();
        System.out.println(lst.getClass().getCanonicalName());

        System.out.println(Inner.class.getCanonicalName());
    }


    class Inner {}
}
