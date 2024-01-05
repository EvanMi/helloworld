package com.yumi.step2;

import com.yumi.step2.config.FlushDiskType;

public class Main {
    public static void main(String[] args) throws Exception{
        MappedFile mappedFile = new MappedFile("/Users/mipengcheng3/works/log/00000000000000000000", 1073741824);
        mappedFile.warmMappedFile(FlushDiskType.ASYNC_FLUSH, 10);

        mappedFile.appendMsg("1hello world1");
        mappedFile.appendMsg("2hello world2");
        mappedFile.appendMsg("2hello world3");
        mappedFile.commit(0);
        mappedFile.flush(0);

        mappedFile.munlock();
    }
}
