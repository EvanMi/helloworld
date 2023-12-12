package com.yumi.http.client.io;

import com.yumi.http.client.protocol.Http;
import com.yumi.http.client.util.ByteArrayBuffer;
import com.yumi.http.client.util.CharArrayBuffer;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;

public class SessionOutputBufferImpl implements SessionOutputBuffer, BufferInfo{
    private static final byte[] CRLF = new byte[] {Http.CR, Http.LF};
    private final ByteArrayBuffer buffer;
    private final int fragementSizeHint;
    private final CharsetEncoder encoder;

    private OutputStream outStream;
    private ByteBuffer bBuf;

    public SessionOutputBufferImpl(
            final int bufferSize,
            final int fragementSizeHint,
            final CharsetEncoder charEncoder) {
        this.buffer = new ByteArrayBuffer(bufferSize);
        this.fragementSizeHint = Math.max(fragementSizeHint, 0);
        this.encoder = charEncoder;
    }

    public SessionOutputBufferImpl(final int bufferSize) {
        this(bufferSize, bufferSize, null);
    }

    public void bind(final OutputStream outStream) {
        this.outStream = outStream;
    }

    public boolean isBound() {
        return this.outStream != null;
    }

    @Override
    public int capacity() {
        return this.buffer.capacity();
    }

    @Override
    public int length() {
        return this.buffer.length();
    }

    @Override
    public int available() {
        return capacity() - length();
    }

    private void streamWrite(final byte[] b, final int off, final int len) throws IOException {
        this.outStream.write(b, off, len);
    }

    private void flushStream() throws IOException {
        if (this.outStream != null) {
            this.outStream.flush();
        }
    }

    private void flushBuffer() throws IOException {
        final int len = this.buffer.length();
        if (len > 0) {
            streamWrite(this.buffer.buffer(), 0, len);
            this.buffer.clear();
        }
    }

    @Override
    public void flush() throws IOException {
        flushBuffer();
        flushStream();
    }

    @Override
    public void write(final byte[] b, final int off, final int len) throws IOException {
        if (b == null) {
            return;
        }
        // Do not want to buffer large-ish chunks
        // if the byte array is larger then MIN_CHUNK_LIMIT
        // write it directly to the output stream
        if (len > this.fragementSizeHint || len > this.buffer.capacity()) {
            // flush the buffer
            flushBuffer();
            // write directly to the out stream
            streamWrite(b, off, len);
        } else {
            // Do not let the buffer grow unnecessarily
            final int freeCapacity = this.buffer.capacity() - this.buffer.length();
            if (len > freeCapacity) {
                // flush the buffer
                flushBuffer();
            }
            // buffer
            this.buffer.append(b, off, len);
        }
    }

    @Override
    public void write(final byte[] b) throws IOException {
        if (b == null) {
            return;
        }
        write(b, 0, b.length);
    }

    @Override
    public void write(final int b) throws IOException {
        if (this.fragementSizeHint > 0) {
            if (this.buffer.isFull()) {
                flushBuffer();
            }
            this.buffer.append(b);
        } else {
            flushBuffer();
            this.outStream.write(b);
        }
    }

    @Override
    public void writeLine(final String s) throws IOException {
        if (s == null) {
            return;
        }
        if (s.length() > 0) {
            if (this.encoder == null) {
                for (int i = 0; i < s.length(); i++) {
                    write(s.charAt(i));
                }
            } else {
                final CharBuffer cbuf = CharBuffer.wrap(s);
                writeEncoded(cbuf);
            }
        }
        write(CRLF);
    }

    @Override
    public void writeLine(final CharArrayBuffer charBuffer) throws IOException {
        if (charBuffer == null) {
            return;
        }
        if (this.encoder == null) {
            int off = 0;
            int remaining = charBuffer.length();
            while (remaining > 0) {
                int chunk = this.buffer.capacity() - this.buffer.length();
                chunk = Math.min(chunk, remaining);
                if (chunk > 0) {
                    this.buffer.append(charBuffer, off, chunk);
                }
                if (this.buffer.isFull()) {
                    flushBuffer();
                }
                off += chunk;
                remaining -= chunk;
            }
        } else {
            final CharBuffer cbuf = CharBuffer.wrap(charBuffer.buffer(), 0, charBuffer.length());
            writeEncoded(cbuf);
        }
        write(CRLF);
    }

    private void writeEncoded(final CharBuffer cBuf) throws IOException {
        if (!cBuf.hasRemaining()) {
            return;
        }
        if (this.bBuf == null) {
            this.bBuf = ByteBuffer.allocate(1024);
        }
        this.encoder.reset();
        while (cBuf.hasRemaining()) {
            final CoderResult result = this.encoder.encode(cBuf, this.bBuf, true);
            handleEncodingResult(result);
        }
        final CoderResult result = this.encoder.flush(this.bBuf);
        handleEncodingResult(result);
        this.bBuf.clear();
    }

    private void handleEncodingResult(final CoderResult result) throws IOException {
        if (result.isError()) {
            result.throwException();
        }
        this.bBuf.flip();
        while (this.bBuf.hasRemaining()) {
            write(this.bBuf.get());
        }
        this.bBuf.compact();
    }
}
