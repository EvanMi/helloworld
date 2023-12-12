package com.yumi.http.client.util;

import com.yumi.http.client.protocol.Http;

import java.io.Serializable;
import java.nio.CharBuffer;

public class CharArrayBuffer implements CharSequence, Serializable {
    private static final long serialVersionUID = -6208952725094867135L;

    private char[] buffer;
    private int len;

    public CharArrayBuffer(final int capacity) {
        super();
        this.buffer = new char[capacity];
    }

    private void expand(final int newLen) {
        final char[] newBuffer = new char[Math.max(this.buffer.length << 1, newLen)];
        System.arraycopy(this.buffer, 0, newBuffer, 0, this.len);
        this.buffer = newBuffer;
    }

    public void append(final char[] b, final int off, final int len) {
        if (b == null) {
            return;
        }
        if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) < 0) || ((off + len) > b.length)) {
            throw new IndexOutOfBoundsException("off: "+off+" len: "+len+" b.length: "+b.length);
        }
        if (len == 0) {
            return;
        }
        final int newLen = this.len + len;
        if (newLen > this.buffer.length) {
            expand(newLen);
        }
        System.arraycopy(b, off, this.buffer, this.len, len);
        this.len = newLen;
    }

    public void append(final String str) {
        final String s = str != null ? str : "null";
        final int strLen = s.length();
        final int newLen = this.len + strLen;
        if (newLen > this.buffer.length) {
            expand(newLen);
        }
        s.getChars(0, strLen, this.buffer, this.len);
        this.len = newLen;
    }

    public void append(final CharArrayBuffer b, final int off, final int len) {
        if (b == null) {
            return;
        }
        append(b.buffer, off, len);
    }

    public void append(final CharArrayBuffer b) {
        if (b == null) {
            return;
        }
        append(b.buffer,0, b.len);
    }

    public void append(final char ch) {
        final int newLen = this.len + 1;
        if (newLen > this.buffer.length) {
            expand(newLen);
        }
        this.buffer[this.len] = ch;
        this.len = newLen;
    }

    public void append(final byte[] b, final int off, final int len) {
        if (b == null) {
            return;
        }
        if ((off < 0) || (off > b.length) || (len < 0) || ((off + len) < 0) || ((off + len) > b.length)) {
            throw new IndexOutOfBoundsException("off: "+off+" len: "+len+" b.length: "+b.length);
        }
        if (len == 0) {
            return;
        }
        final int oldLen = this.len;
        final int newLen = oldLen + len;
        if (newLen > this.buffer.length) {
            expand(newLen);
        }
        for (int i1 = off, i2 = oldLen; i2 < newLen; i1++, i2++) {
            this.buffer[i2] = (char) (b[i1] & 0xff);
        }
        this.len = newLen;
    }

    public void append(final ByteArrayBuffer b, final int off, final int len) {
        if (b == null) {
            return;
        }
        append(b.buffer(), off, len);
    }

    public void append(final Object obj) {
        append(String.valueOf(obj));
    }

    public void clear() {
        this.len = 0;
    }

    public char[] toCharArray() {
        final char[] b = new char[this.len];
        if (this.len > 0) {
            System.arraycopy(this.buffer, 0, b, 0, this.len);
        }
        return b;
    }

    @Override
    public char charAt(final int i) {
        return this.buffer[i];
    }
    public char[] buffer() {
        return this.buffer;
    }
    public int capacity() {
        return this.buffer.length;
    }
    @Override
    public int length() {
        return this.len;
    }
    public void ensureCapacity(final int required) {
        if (required <= 0) {
            return;
        }
        final int available = this.buffer.length - this.len;
        if (required > available) {
            expand(this.len + required);
        }
    }
    public void setLength(final int len) {
        if (len < 0 || len > this.buffer.length) {
            throw new IndexOutOfBoundsException("len: "+len+" < 0 or > buffer len: "+this.buffer.length);
        }
        this.len = len;
    }
    public boolean isEmpty() {
        return this.len == 0;
    }
    public boolean isFull() {
        return this.len == this.buffer.length;
    }
    public int indexOf(final int ch, final int from, final int to) {
        int beginIndex = from;
        if (beginIndex < 0) {
            beginIndex = 0;
        }
        int endIndex = to;
        if (endIndex > this.len) {
            endIndex = this.len;
        }
        if (beginIndex > endIndex) {
            return -1;
        }
        for (int i = beginIndex; i < endIndex; i++) {
            if (this.buffer[i] == ch) {
                return i;
            }
        }
        return -1;
    }
    public int indexOf(final int ch) {
        return indexOf(ch, 0, this.len);
    }

    public String substring(final int beginIndex, final int endIndex) {
        if (beginIndex < 0) {
            throw new IndexOutOfBoundsException("Negative beginIndex: " + beginIndex);
        }
        if (endIndex > this.len) {
            throw new IndexOutOfBoundsException("endIndex: " + endIndex + " > length: " + this.len);
        }
        if (beginIndex > endIndex) {
            throw new IndexOutOfBoundsException("beginIndex: " + beginIndex + " > endIndex: " + endIndex);
        }
        return new String(this.buffer, beginIndex, endIndex - beginIndex);
    }
    public String substringTrimmed(final int beginIndex, final int endIndex) {
        if (beginIndex < 0) {
            throw new IndexOutOfBoundsException("Negative beginIndex: " + beginIndex);
        }
        if (endIndex > this.len) {
            throw new IndexOutOfBoundsException("endIndex: " + endIndex + " > length: " + this.len);
        }
        if (beginIndex > endIndex) {
            throw new IndexOutOfBoundsException("beginIndex: " + beginIndex + " > endIndex: " + endIndex);
        }
        int beginIndex0 = beginIndex;
        int endIndex0 = endIndex;
        while (beginIndex0 < endIndex && Http.isWhitespace(this.buffer[beginIndex0])) {
            beginIndex0++;
        }
        while (endIndex0 > beginIndex0 && Http.isWhitespace(this.buffer[endIndex0 - 1])) {
            endIndex0--;
        }
        return new String(this.buffer, beginIndex0, endIndex0 - beginIndex0);
    }
    @Override
    public CharSequence subSequence(final int beginIndex, final int endIndex) {
        if (beginIndex < 0) {
            throw new IndexOutOfBoundsException("Negative beginIndex: " + beginIndex);
        }
        if (endIndex > this.len) {
            throw new IndexOutOfBoundsException("endIndex: " + endIndex + " > length: " + this.len);
        }
        if (beginIndex > endIndex) {
            throw new IndexOutOfBoundsException("beginIndex: " + beginIndex + " > endIndex: " + endIndex);
        }
        return CharBuffer.wrap(this.buffer, beginIndex, endIndex);
    }

    @Override
    public String toString() {
        return new String(this.buffer, 0, this.len);
    }
}
