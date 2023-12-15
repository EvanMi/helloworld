package com.yumi.step1;

public class Main {
    public static void main(String[] args) throws Exception{
        MappedFile mappedFile = new MappedFile("/Users/mipengcheng3/works/log/00000000000000000000", 1073741824);
        mappedFile.appendMsg("1hello world1");
        mappedFile.appendMsg("2hello world2");
        mappedFile.appendMsg("2hello world3");
        mappedFile.commit(0);
        mappedFile.flush(0);
    }
}
