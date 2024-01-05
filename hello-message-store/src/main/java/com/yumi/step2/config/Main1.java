package com.yumi.step2.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.CopyOnWriteArrayList;

public class Main1 {

    public static void main(String[] args) {
        CopyOnWriteArrayList<String> strings = new CopyOnWriteArrayList<>();
        strings.add("1");
        strings.add("2");

        ListIterator<String> stringListIterator = strings.listIterator(strings.size());
        List<String> toRemove = new ArrayList<>();
        while (stringListIterator.hasPrevious()) {
            String previous = stringListIterator.previous();
            System.out.println(previous);
            //stringListIterator.remove();
            toRemove.add(previous);
        }
        strings.removeAll(toRemove);
        System.out.println(strings);
    }
}
