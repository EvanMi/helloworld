package com.yumi.zero.copy;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.WritableByteChannel;

public class ZeroCopyClient {

    //org.apache.rocketmq.broker.pagecache.ManyMessageTransfer

    public void sendFile(WritableByteChannel target) throws Exception {
        String path = this.getClass().getClassLoader().getResource("").getPath();
        System.out.println(path);
        File file = new File(path + "/com/yumi/zero/copy/test.txt");
        RandomAccessFile rw = new RandomAccessFile(file, "rw");
        FileChannel channel = rw.getChannel();
        long size = file.length(), write = 0;
        while (write < size) {
            System.out.println(write);
            long l = channel.transferTo(write, size - write, target);
            write += l;
        }
    }

    public void sendDirectAndFile(WritableByteChannel target) throws Exception {
        //direct byte buffer
        ByteBuffer aIntBuffer = ByteBuffer.allocate(2);
        aIntBuffer.clear();
        aIntBuffer.put((byte)65);
        aIntBuffer.put((byte)95);
        aIntBuffer.flip();
        String path = this.getClass().getClassLoader().getResource("").getPath();
        File file = new File(path + "/com/yumi/zero/copy/test.txt");
        RandomAccessFile rw = new RandomAccessFile(file, "rw");
        FileChannel channel = rw.getChannel();
        MappedByteBuffer map = channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length());
        while (aIntBuffer.hasRemaining()) {
            target.write(aIntBuffer);
        }
        while (map.hasRemaining()) {
            target.write(map);
        }
    }


    public SocketChannel createSocketChannel() throws Exception{
        InetSocketAddress isa = new InetSocketAddress("127.0.0.1", 8008);
        SocketChannel sc = SocketChannel.open();
        sc.configureBlocking(true);

        sc.socket().setSoLinger(false, -1);
        sc.socket().setTcpNoDelay(true);
        sc.socket().connect(isa, 2000);
        sc.socket().setSoTimeout(1000);
        return sc;
    }

    public static void main(String[] args) throws Exception{
        ZeroCopyClient zeroCopyClient = new ZeroCopyClient();
        SocketChannel socketChannel = zeroCopyClient.createSocketChannel();
        //zeroCopyClient.sendFile(socketChannel);
        zeroCopyClient.sendDirectAndFile(socketChannel);
    }
}
