package com.yumi.tests;

import java.util.ArrayList;
import java.util.List;

public class GenericTest {



    static class B_ {
        @Override
        public String toString() {
            return "B__";
        }
    }
    static class A_ extends B_ {}
    static class A extends A_ {}
    static class B extends A {}
    static class C extends B {}
    static class D extends C {}


    public static void addToList (List<? super A> list) {
        list.add(new B());
        list.add(new C());
        list.add(new D());
    }

    public static void printList(List<? extends B_> list) {
        for (B_ b : list) {
            System.out.println(b);
        }
    }

    public static void main(String[] args) {
        List<A_> aList = new ArrayList<>();
        addToList(aList);
        printList(aList);
    }
}
