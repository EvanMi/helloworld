package com.yumi.utils.ito;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class ItoTest {

    public static void main(String[] args) {
        testRemove(getCopyOnWriteArrayList());
    }

    public static void testRemove(List<String> list) {
        Iterator<String> iterator = list.iterator();
        while (iterator.hasNext()) {
            String next = iterator.next();
            if ("c".equals(next)) {
                iterator.remove();
            }
        }
        System.out.println(list);
    }


    private static List<String> getArrayList() {
        List<String> res = new ArrayList<>();
        fillList(res);
        return res;
    }

    private static List<String> getCopyOnWriteArrayList() {
        List<String> res = new CopyOnWriteArrayList<>();
        fillList(res);
        return res;
    }

    private static void fillList(List<String> res) {
        res.add("a");
        res.add("b");
        res.add("c");
        res.add("d");
    }
}
