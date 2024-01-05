package com.yumi.utils.bloom;

public class BloomTest {

    public static void main(String[] args) {
        BloomFilter filter = BloomFilter.createByFn(10, 100000);
        System.out.println(filter);
        BitsArray bitsArray = BitsArray.create(filter.getM());
        filter.hashTo("firstString", bitsArray);
        filter.hashTo("secondString", bitsArray);

        System.out.println(filter.isHit("firstString", bitsArray));
        System.out.println(filter.isHit("lili", bitsArray));
        System.out.println(filter.checkFalseHit(filter.calcBitPositions("thirdString"), bitsArray));
    }
}
