/*
 * Copyright 1999-2101 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.fastjson.serializer;

import static com.alibaba.fastjson.util.IOUtils.replaceChars;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.ref.SoftReference;
import java.nio.charset.Charset;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.util.IOUtils;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public final class SerializeWriter extends Writer {

    /**
     * The buffer where data is stored.
     */
    protected char                                          buf[];

    /**
     * The number of chars in the buffer.
     */
    protected int                                           count;

    private final static ThreadLocal<SoftReference<char[]>> bufLocal = new ThreadLocal<SoftReference<char[]>>();

    protected int                                           features;

    protected final Writer                                    writer;

    public SerializeWriter(){
        this((Writer) null);
    }

    public SerializeWriter(Writer writer){
        this.writer = writer;
        this.features = JSON.DEFAULT_GENERATE_FEATURE;

        SoftReference<char[]> ref = bufLocal.get();

        if (ref != null) {
            buf = ref.get();
            bufLocal.set(null);
        }

        if (buf == null) {
            buf = new char[1024];
        }
    }

    public SerializeWriter(SerializerFeature... features){
        this(null, features);
    }
    
    public SerializeWriter(Writer writer, SerializerFeature... features){
        this (writer, 0, features);
    }

    public SerializeWriter(Writer writer, int featuresValue, SerializerFeature[] features){
        this.writer = writer;

        SoftReference<char[]> ref = bufLocal.get();

        if (ref != null) {
            buf = ref.get();
            bufLocal.set(null);
        }

        if (buf == null) {
            buf = new char[1024];
        }

        for (SerializerFeature feature : features) {
            featuresValue |= feature.mask;
        }
        this.features = featuresValue;
    }

    public SerializeWriter(int initialSize){
        this(null, initialSize);
    }

    public SerializeWriter(Writer writer, int initialSize){
        this.writer = writer;

        if (initialSize <= 0) {
            throw new IllegalArgumentException("Negative initial size: " + initialSize);
        }
        buf = new char[initialSize];
    }

    public void config(SerializerFeature feature, boolean state) {
        if (state) {
            features |= feature.mask;
        } else {
            features &= ~feature.mask;
        }
    }

    public boolean isEnabled(SerializerFeature feature) {
        return (features & feature.mask) != 0;
    }

    /**
     * Writes a character to the buffer.
     */
    public void write(int c) {
        int newcount = count + 1;
        if (newcount > buf.length) {
            if (writer == null) {
                expandCapacity(newcount);
            } else {
                flush();
                newcount = 1;
            }
        }
        buf[count] = (char) c;
        count = newcount;
    }

    /**
     * Writes characters to the buffer.
     * 
     * @param c the data to be written
     * @param off the start offset in the data
     * @param len the number of chars that are written
     */
    public void write(char c[], int off, int len) {
        if (off < 0 //
            || off > c.length //
            || len < 0 //
            || off + len > c.length //
            || off + len < 0) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        int newcount = count + len;
        if (newcount > buf.length) {
            if (writer == null) {
                expandCapacity(newcount);
            } else {
                do {
                    int rest = buf.length - count;
                    System.arraycopy(c, off, buf, count, rest);
                    count = buf.length;
                    flush();
                    len -= rest;
                    off += rest;
                } while (len > buf.length);
                newcount = len;
            }
        }
        System.arraycopy(c, off, buf, count, len);
        count = newcount;
    }

    protected void expandCapacity(int minimumCapacity) {
        int newCapacity = (buf.length * 3) / 2 + 1;

        if (newCapacity < minimumCapacity) {
            newCapacity = minimumCapacity;
        }
        char newValue[] = new char[newCapacity];
        System.arraycopy(buf, 0, newValue, 0, count);
        buf = newValue;
    }

    /**
     * Write a portion of a string to the buffer.
     * 
     * @param str String to be written from
     * @param off Offset from which to start reading characters
     * @param len Number of characters to be written
     */
    public void write(String str, int off, int len) {
        int newcount = count + len;
        if (newcount > buf.length) {
            if (writer == null) {
                expandCapacity(newcount);
            } else {
                do {
                    int rest = buf.length - count;
                    str.getChars(off, off + rest, buf, count);
                    count = buf.length;
                    flush();
                    len -= rest;
                    off += rest;
                } while (len > buf.length);
                newcount = len;
            }
        }
        str.getChars(off, off + len, buf, count);
        count = newcount;
    }

    /**
     * Writes the contents of the buffer to another character stream.
     * 
     * @param out the output stream to write to
     * @throws IOException If an I/O error occurs.
     */
    public void writeTo(Writer out) throws IOException {
        if (this.writer != null) {
            throw new UnsupportedOperationException("writer not null");
        }
        out.write(buf, 0, count);
    }

    public void writeTo(OutputStream out, String charsetName) throws IOException {
        writeTo(out, Charset.forName(charsetName));
    }

    public void writeTo(OutputStream out, Charset charset) throws IOException {
        if (this.writer != null) {
            throw new UnsupportedOperationException("writer not null");
        }
        byte[] bytes = new String(buf, 0, count).getBytes(charset.name());
        out.write(bytes);
    }

    public SerializeWriter append(CharSequence csq) {
        String s = (csq == null ? "null" : csq.toString());
        write(s, 0, s.length());
        return this;
    }

    public SerializeWriter append(CharSequence csq, int start, int end) {
        String s = (csq == null ? "null" : csq).subSequence(start, end).toString();
        write(s, 0, s.length());
        return this;
    }

    public SerializeWriter append(char c) {
        write(c);
        return this;
    }

    public byte[] toBytes(String charsetName) {
        if (this.writer != null) {
            throw new UnsupportedOperationException("writer not null");
        }

        if (charsetName == null) {
            charsetName = "UTF-8";
        }

        try {
            return new String(buf, 0, count).getBytes(charsetName);
        } catch (UnsupportedEncodingException e) {
            throw new JSONException("toBytes error", e);
        }
    }

    public String toString() {
        return new String(buf, 0, count);
    }

    /**
     * Close the stream. This method does not release the buffer, since its contents might still be required. Note:
     * Invoking this method in this class will have no effect.
     */
    public void close() {
        if (writer != null && count > 0) {
            flush();
        }
        if (buf.length <= 1024 * 8) {
            bufLocal.set(new SoftReference<char[]>(buf));
        }

        this.buf = null;
    }

    public void write(String text) {
        if (text == null) {
            writeNull();
            return;
        }

        write(text, 0, text.length());
    }

    public void writeInt(int i) {
        if (i == Integer.MIN_VALUE) {
            write("-2147483648");
            return;
        }

        int size;
        final int x = i < 0 ? -i : i;
        for (int j = 0;; j++) {
            if (x <= SerializeWriter.sizeTable[j]) {
                size = j + 1;
                break;
            }
        }
        if (i < 0) {
            size ++;
        }

        int newcount = count + size;
        if (newcount > buf.length) {
            if (writer == null) {
                expandCapacity(newcount);
            } else {
                char[] chars = new char[size];
                IOUtils.getChars(i, size, chars);
                write(chars, 0, chars.length);
                return;
            }
        }

        IOUtils.getChars(i, newcount, buf);

        count = newcount;
    }

    public void writeByteArray(byte[] bytes) {
        int bytesLen = bytes.length;
        
        final boolean singleQuote = (features & SerializerFeature.UseSingleQuotes.mask) != 0;
        final char quote = singleQuote ? '\'' : '"';

        if (bytesLen == 0) {
            String emptyString = singleQuote ? "''" : "\"\"";
            write(emptyString);
            return;
        }

        final char[] CA = JSONLexer.CA;

        int eLen = (bytesLen / 3) * 3; // Length of even 24-bits.
        int charsLen = ((bytesLen - 1) / 3 + 1) << 2; // base64 character count
        // char[] chars = new char[charsLen];
        int offset = count;
        int newcount = count + charsLen + 2;
        if (newcount > buf.length) {
            if (writer != null) {
                write(quote);

                for (int s = 0; s < eLen;) {
                    // Copy next three bytes into lower 24 bits of int, paying attension to sign.
                    int i = (bytes[s++] & 0xff) << 16 | (bytes[s++] & 0xff) << 8 | (bytes[s++] & 0xff);

                    // Encode the int into four chars
                    write(CA[(i >>> 18) & 0x3f]);
                    write(CA[(i >>> 12) & 0x3f]);
                    write(CA[(i >>> 6) & 0x3f]);
                    write(CA[i & 0x3f]);
                }

                // Pad and encode last bits if source isn't even 24 bits.
                int left = bytesLen - eLen; // 0 - 2.
                if (left > 0) {
                    // Prepare the int
                    int i = ((bytes[eLen] & 0xff) << 10) | (left == 2 ? ((bytes[bytesLen - 1] & 0xff) << 2) : 0);

                    // Set last four chars
                    write(CA[i >> 12]);
                    write(CA[(i >>> 6) & 0x3f]);
                    write(left == 2 ? CA[i & 0x3f] : '=');
                    write('=');
                }

                write(quote);
                return;
            }
            expandCapacity(newcount);
        }
        count = newcount;
        buf[offset++] = quote;

        // Encode even 24-bits
        for (int s = 0, d = offset; s < eLen;) {
            // Copy next three bytes into lower 24 bits of int, paying attension to sign.
            int i = (bytes[s++] & 0xff) << 16 | (bytes[s++] & 0xff) << 8 | (bytes[s++] & 0xff);

            // Encode the int into four chars
            buf[d++] = CA[(i >>> 18) & 0x3f];
            buf[d++] = CA[(i >>> 12) & 0x3f];
            buf[d++] = CA[(i >>> 6) & 0x3f];
            buf[d++] = CA[i & 0x3f];
        }

        // Pad and encode last bits if source isn't even 24 bits.
        int left = bytesLen - eLen; // 0 - 2.
        if (left > 0) {
            // Prepare the int
            int i = ((bytes[eLen] & 0xff) << 10) | (left == 2 ? ((bytes[bytesLen - 1] & 0xff) << 2) : 0);

            // Set last four chars
            buf[newcount - 5] = CA[i >> 12];
            buf[newcount - 4] = CA[(i >>> 6) & 0x3f];
            buf[newcount - 3] = left == 2 ? CA[i & 0x3f] : '=';
            buf[newcount - 2] = '=';
        }
        buf[newcount - 1] = quote;
    }

    public void writeLong(long i) {
        if (i == Long.MIN_VALUE) {
            write("-9223372036854775808");
            return;
        }

        long val = i < 0 ? -i : i;
        int size = 0;
        long p = 10;
          for (int j = 1; j < 19; j++) {
              if (val < p) {
                  size = j;
                  break;
              }
              p = 10 * p;
          }
          
          if (size == 0) {
              size = 19;
          }
          if (i < 0) {
              size++;
          }

        int newcount = count + size;
        if (newcount > buf.length) {
            if (writer == null) {
                expandCapacity(newcount);
            } else {
                char[] chars = new char[size];
                IOUtils.getChars(i, size, chars);
                write(chars, 0, chars.length);
                return;
            }
        }

        IOUtils.getChars(i, newcount, buf);

        count = newcount;
    }

    public void writeNull() {
        write("null");
    }

    protected void writeStringWithDoubleQuote(String text, final char xx, boolean checkSpecial) {
        if (text == null) {
            writeNull();
            if (xx != 0) {
                write(xx);
            }
            return;
        }

        int len = text.length();
        int newcount = count + len + 2;
        if (xx != 0) {
            newcount++;
        }

        if (newcount > buf.length) {
            if (writer != null) {
                write('"');

                for (int j = 0; j < text.length(); ++j) {
                    char ch = text.charAt(j);
                    
                    if ((features & SerializerFeature.BrowserCompatible.mask) != 0) {
                        if (ch == '\b' //
                            || ch == '\f' //
                            || ch == '\n' //
                            || ch == '\r' //
                            || ch == '\t' //
                            || ch == '"' //
                            || ch == '/' //
                            || ch == '\\') {
                            write('\\');
                            write(replaceChars[(int) ch]);
                            continue;
                        }

                        if (ch < 32) {
                            write('\\');
                            write('u');
                            write('0');
                            write('0');
                            write(IOUtils.ASCII_CHARS[ch * 2]);
                            write(IOUtils.ASCII_CHARS[ch * 2 + 1]);
                            continue;
                        }

                        if (ch >= 127) {
                            write('\\');
                            write('u');
                            write(IOUtils.DIGITS[(ch >>> 12) & 15]);
                            write(IOUtils.DIGITS[(ch >>> 8) & 15]);
                            write(IOUtils.DIGITS[(ch >>> 4) & 15]);
                            write(IOUtils.DIGITS[ch & 15]);
                            continue;
                        }
                    } else {
                        if (ch < IOUtils.specicalFlags_doubleQuotes.length
                            && IOUtils.specicalFlags_doubleQuotes[ch] != 0 //
                            || (ch == '/' && (features & SerializerFeature.WriteSlashAsSpecial.mask) != 0)) {
                            write('\\');
                            write(replaceChars[(int) ch]);
                            continue;
                        }
                    }

                    write(ch);
                }

                write('"');
                if (xx != 0) {
                    write(xx);
                }
                return;
            }
            expandCapacity(newcount);
        }

        int start = count + 1;
        int end = start + len;

        buf[count] = '\"';
        text.getChars(0, len, buf, start);

        count = newcount;

        if ((features & SerializerFeature.BrowserCompatible.mask) != 0) {
            int lastSpecialIndex = -1;

            for (int i = start; i < end; ++i) {
                char ch = buf[i];

                if (ch == '"' //
                    || ch == '/' //
                    || ch == '\\') {
                    lastSpecialIndex = i;
                    newcount += 1;
                    continue;
                }

                if (ch == '\b' //
                    || ch == '\f' //
                    || ch == '\n' //
                    || ch == '\r' //
                    || ch == '\t') {
                    lastSpecialIndex = i;
                    newcount += 1;
                    continue;
                }

                if (ch < 32) {
                    lastSpecialIndex = i;
                    newcount += 5;
                    continue;
                }

                if (ch >= 127) {
                    lastSpecialIndex = i;
                    newcount += 5;
                    continue;
                }
            }

            if (newcount > buf.length) {
                expandCapacity(newcount);
            }
            count = newcount;

            for (int i = lastSpecialIndex; i >= start; --i) {
                char ch = buf[i];

                if (ch == '\b' //
                    || ch == '\f'//
                    || ch == '\n' //
                    || ch == '\r' //
                    || ch == '\t') {
                    System.arraycopy(buf, i + 1, buf, i + 2, end - i - 1);
                    buf[i] = '\\';
                    buf[i + 1] = replaceChars[(int) ch];
                    end += 1;
                    continue;
                }

                if (ch == '"' //
                    || ch == '/' //
                    || ch == '\\') {
                    System.arraycopy(buf, i + 1, buf, i + 2, end - i - 1);
                    buf[i] = '\\';
                    buf[i + 1] = ch;
                    end += 1;
                    continue;
                }

                if (ch < 32) {
                    System.arraycopy(buf, i + 1, buf, i + 6, end - i - 1);
                    buf[i] = '\\';
                    buf[i + 1] = 'u';
                    buf[i + 2] = '0';
                    buf[i + 3] = '0';
                    buf[i + 4] = IOUtils.ASCII_CHARS[ch * 2];
                    buf[i + 5] = IOUtils.ASCII_CHARS[ch * 2 + 1];
                    end += 5;
                    continue;
                }

                if (ch >= 127) {
                    System.arraycopy(buf, i + 1, buf, i + 6, end - i - 1);
                    buf[i] = '\\';
                    buf[i + 1] = 'u';
                    buf[i + 2] = IOUtils.DIGITS[(ch >>> 12) & 15];
                    buf[i + 3] = IOUtils.DIGITS[(ch >>> 8) & 15];
                    buf[i + 4] = IOUtils.DIGITS[(ch >>> 4) & 15];
                    buf[i + 5] = IOUtils.DIGITS[ch & 15];
                    end += 5;
                }
            }

            if (xx != 0) {
                buf[count - 2] = '\"';
                buf[count - 1] = xx;
            } else {
                buf[count - 1] = '\"';
            }

            return;
        }

        int specialCount = 0;
        int lastSpecialIndex = -1;
        int firstSpecialIndex = -1;
        char lastSpecial = '\0';
        if (checkSpecial) {
            for (int i = start; i < end; ++i) {
                char ch = buf[i];

                if (ch == '\u2028') {
                    specialCount++;
                    lastSpecialIndex = i;
                    lastSpecial = ch;
                    newcount += 4;

                    if (firstSpecialIndex == -1) {
                        firstSpecialIndex = i;
                    }
                    continue;
                }

                if (ch >= ']') {
                    if (ch >= 0x7F && ch <= 0xA0) {
                        if (firstSpecialIndex == -1) {
                            firstSpecialIndex = i;
                        }

                        specialCount++;
                        lastSpecialIndex = i;
                        lastSpecial = ch;
                        newcount += 4;
                    }
                    continue;
                }
                
                final boolean isSpecial;
                {
                    if (ch == ' ') {
                        isSpecial = false;
                    } else if (ch == '/' && (features & SerializerFeature.WriteSlashAsSpecial.mask) != 0) {
                        isSpecial = true;
                    } else if (ch > '#' && ch != '\\') {
                        isSpecial = false;
                    } else if (ch <= 0x1F || ch == '\\' || ch == '"') {
                        isSpecial = true;
                    } else {
                        isSpecial = false;
                    }
                }

                if (isSpecial) {
                    specialCount++;
                    lastSpecialIndex = i;
                    lastSpecial = ch;

                    if (ch < IOUtils.specicalFlags_doubleQuotes.length //
                        && IOUtils.specicalFlags_doubleQuotes[ch] == 4 //
                    ) {
                        newcount += 4;
                    }

                    if (firstSpecialIndex == -1) {
                        firstSpecialIndex = i;
                    }
                }
            }

            if (specialCount > 0) {
                writeSpecial(text, newcount, start, end, specialCount, lastSpecialIndex, firstSpecialIndex,
                             lastSpecial);
            }
        }

        if (xx != 0) {
            buf[count - 2] = '\"';
            buf[count - 1] = xx;
        } else {
            buf[count - 1] = '\"';
        }
    }

    protected void writeSpecial(String text, int newcount, int start, int end, int specialCount, int lastSpecialIndex,
                                int firstSpecialIndex, char lastSpecial) {
        newcount += specialCount;
        if (newcount > buf.length) {
            expandCapacity(newcount);
        }
        count = newcount;

        if (specialCount == 1) {
            if (lastSpecial == '\u2028') {
                int srcPos = lastSpecialIndex + 1;
                int destPos = lastSpecialIndex + 6;
                int LengthOfCopy = end - lastSpecialIndex - 1;
                System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);
                buf[lastSpecialIndex] = '\\';
                buf[++lastSpecialIndex] = 'u';
                buf[++lastSpecialIndex] = '2';
                buf[++lastSpecialIndex] = '0';
                buf[++lastSpecialIndex] = '2';
                buf[++lastSpecialIndex] = '8';
            } else {
                final char ch = lastSpecial;
                if (ch < IOUtils.specicalFlags_doubleQuotes.length //
                    && IOUtils.specicalFlags_doubleQuotes[ch] == 4) {
                    int srcPos = lastSpecialIndex + 1;
                    int destPos = lastSpecialIndex + 6;
                    int LengthOfCopy = end - lastSpecialIndex - 1;
                    System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);

                    int bufIndex = lastSpecialIndex;
                    buf[bufIndex++] = '\\';
                    buf[bufIndex++] = 'u';
                    buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                    buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                    buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                    buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                } else {
                    int srcPos = lastSpecialIndex + 1;
                    int destPos = lastSpecialIndex + 2;
                    int LengthOfCopy = end - lastSpecialIndex - 1;
                    System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);
                    buf[lastSpecialIndex] = '\\';
                    buf[++lastSpecialIndex] = replaceChars[(int) ch];
                }
            }
        } else if (specialCount > 1) {
            int textIndex = firstSpecialIndex - start;
            int bufIndex = firstSpecialIndex;
            for (int i = textIndex; i < text.length(); ++i) {
                char ch = text.charAt(i);

                if (ch < IOUtils.specicalFlags_doubleQuotes.length //
                    && IOUtils.specicalFlags_doubleQuotes[ch] != 0 //
                    || (ch == '/' && (features & SerializerFeature.WriteSlashAsSpecial.mask) != 0)) {
                    buf[bufIndex++] = '\\';
                    if (IOUtils.specicalFlags_doubleQuotes[ch] == 4) {
                        buf[bufIndex++] = 'u';
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                        end += 5;
                    } else {
                        buf[bufIndex++] = replaceChars[(int) ch];
                        end++;
                    }
                } else {
                    if (ch == '\u2028') {
                        buf[bufIndex++] = '\\';
                        buf[bufIndex++] = 'u';
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                        end += 5;
                    } else {
                        buf[bufIndex++] = ch;
                    }
                }
            }
        }
    }

    public void write(boolean value) {
        if (value) {
            write("true");
        } else {
            write("false");
        }
    }

    public void writeString(String text) {
        if ((features & SerializerFeature.UseSingleQuotes.mask) != 0) {
            writeStringWithSingleQuote(text);
        } else {
            writeStringWithDoubleQuote(text, (char) 0, true);
        }
    }

    protected void writeStringWithSingleQuote(String text) {
        if (text == null) {
            int newcount = count + 4;
            if (newcount > buf.length) {
                expandCapacity(newcount);
            }
            "null".getChars(0, 4, buf, count);
            count = newcount;
            return;
        }

        int len = text.length();
        int newcount = count + len + 2;
        if (newcount > buf.length) {
            if (writer != null) {
                write('\'');
                for (int i = 0; i < text.length(); ++i) {
                    char ch = text.charAt(i);
                    if (ch <= 13 || ch == '\\' || ch == '\'' //
                        || (ch == '/' && (features & SerializerFeature.WriteSlashAsSpecial.mask) != 0)) {
                        write('\\');
                        write(replaceChars[(int) ch]);
                    } else {
                        write(ch);
                    }
                }
                write('\'');
                return;
            }
            expandCapacity(newcount);
        }

        int start = count + 1;
        int end = start + len;

        buf[count] = '\'';
        text.getChars(0, len, buf, start);
        count = newcount;

        int specialCount = 0;
        int lastSpecialIndex = -1;
        char lastSpecial = '\0';
        for (int i = start; i < end; ++i) {
            char ch = buf[i];
            if (ch <= 13 || ch == '\\' || ch == '\'' //
                || (ch == '/' && (features & SerializerFeature.WriteSlashAsSpecial.mask) != 0)) {
                specialCount++;
                lastSpecialIndex = i;
                lastSpecial = ch;
            }
        }

        newcount += specialCount;
        if (newcount > buf.length) {
            expandCapacity(newcount);
        }
        count = newcount;

        if (specialCount == 1) {
            System.arraycopy(buf, lastSpecialIndex + 1, buf, lastSpecialIndex + 2, end - lastSpecialIndex - 1);
            buf[lastSpecialIndex] = '\\';
            buf[++lastSpecialIndex] = replaceChars[(int) lastSpecial];
        } else if (specialCount > 1) {
            System.arraycopy(buf, lastSpecialIndex + 1, buf, lastSpecialIndex + 2, end - lastSpecialIndex - 1);
            buf[lastSpecialIndex] = '\\';
            buf[++lastSpecialIndex] = replaceChars[(int) lastSpecial];
            end++;
            for (int i = lastSpecialIndex - 2; i >= start; --i) {
                char ch = buf[i];
                
                if (ch <= 13 || ch == '\\' || ch == '\'' //
                    || (ch == '/' && (features & SerializerFeature.WriteSlashAsSpecial.mask) != 0)) {
                    System.arraycopy(buf, i + 1, buf, i + 2, end - i - 1);
                    buf[i] = '\\';
                    buf[i + 1] = replaceChars[(int) ch];
                    end++;
                }
            }
        }

        buf[count - 1] = '\'';
    }

    public void writeFieldName(String key, boolean checkSpecial) {
        if (key == null) {
            write("null:");
            return;
        }

        if ((features & SerializerFeature.UseSingleQuotes.mask) != 0) {
            if ((features & SerializerFeature.QuoteFieldNames.mask) != 0) {
                writeStringWithSingleQuote(key);
                write(':');
            } else {
                writeKeyWithSingleQuoteIfHasSpecial(key);
            }
        } else {
            if ((features & SerializerFeature.QuoteFieldNames.mask) != 0) {
                writeStringWithDoubleQuote(key, ':', checkSpecial);
            } else {
                writeKeyWithDoubleQuoteIfHasSpecial(key);
            }
        }
    }

    private void writeKeyWithDoubleQuoteIfHasSpecial(String text) {
        final byte[] specicalFlags_doubleQuotes = IOUtils.specicalFlags_doubleQuotes;

        int len = text.length();
        int newcount = count + len + 1;
        if (newcount > buf.length) {
            if (writer != null) {
                if (len == 0) {
                    write('"');
                    write('"');
                    write(':');
                    return;
                }

                boolean hasSpecial = false;
                for (int i = 0; i < len; ++i) {
                    char ch = text.charAt(i);
                    if (ch < specicalFlags_doubleQuotes.length && specicalFlags_doubleQuotes[ch] != 0) {
                        hasSpecial = true;
                        break;
                    }
                }

                if (hasSpecial) {
                    write('"');
                }
                for (int i = 0; i < len; ++i) {
                    char ch = text.charAt(i);
                    if (ch < specicalFlags_doubleQuotes.length && specicalFlags_doubleQuotes[ch] != 0) {
                        write('\\');
                        write(replaceChars[(int) ch]);
                    } else {
                        write(ch);
                    }
                }
                if (hasSpecial) {
                    write('"');
                }
                write(':');
                return;
            }
            expandCapacity(newcount);
        }

        if (len == 0) {
            int newCount = count + 3;
            if (newCount > buf.length) {
                expandCapacity(count + 3);
            }
            buf[count++] = '"';
            buf[count++] = '"';
            buf[count++] = ':';
            return;
        }

        int start = count;
        int end = start + len;

        text.getChars(0, len, buf, start);
        count = newcount;

        boolean hasSpecial = false;

        for (int i = start; i < end; ++i) {
            char ch = buf[i];
            if (ch < specicalFlags_doubleQuotes.length && specicalFlags_doubleQuotes[ch] != 0) {
                if (!hasSpecial) {
                    newcount += 3;
                    if (newcount > buf.length) {
                        expandCapacity(newcount);
                    }
                    count = newcount;

                    System.arraycopy(buf, i + 1, buf, i + 3, end - i - 1);
                    System.arraycopy(buf, 0, buf, 1, i);
                    buf[start] = '"';
                    buf[++i] = '\\';
                    buf[++i] = replaceChars[(int) ch];
                    end += 2;
                    buf[count - 2] = '"';

                    hasSpecial = true;
                } else {
                    newcount++;
                    if (newcount > buf.length) {
                        expandCapacity(newcount);
                    }
                    count = newcount;

                    System.arraycopy(buf, i + 1, buf, i + 2, end - i);
                    buf[i] = '\\';
                    buf[++i] = replaceChars[(int) ch];
                    end++;
                }
            }
        }

        buf[count - 1] = ':';
    }

    private void writeKeyWithSingleQuoteIfHasSpecial(String text) {
        final byte[] specicalFlags_singleQuotes = IOUtils.specicalFlags_singleQuotes;

        int len = text.length();
        int newcount = count + len + 1;
        if (newcount > buf.length) {
            if (writer != null) {
                if (len == 0) {
                    write('\'');
                    write('\'');
                    write(':');
                    return;
                }

                boolean hasSpecial = false;
                for (int i = 0; i < len; ++i) {
                    char ch = text.charAt(i);
                    if (ch < specicalFlags_singleQuotes.length && specicalFlags_singleQuotes[ch] != 0) {
                        hasSpecial = true;
                        break;
                    }
                }

                if (hasSpecial) {
                    write('\'');
                }
                for (int i = 0; i < len; ++i) {
                    char ch = text.charAt(i);
                    if (ch < specicalFlags_singleQuotes.length && specicalFlags_singleQuotes[ch] != 0) {
                        write('\\');
                        write(replaceChars[(int) ch]);
                    } else {
                        write(ch);
                    }
                }
                if (hasSpecial) {
                    write('\'');
                }
                write(':');
                return;
            }

            expandCapacity(newcount);
        }

        if (len == 0) {
            int newCount = count + 3;
            if (newCount > buf.length) {
                expandCapacity(count + 3);
            }
            buf[count++] = '\'';
            buf[count++] = '\'';
            buf[count++] = ':';
            return;
        }

        int start = count;
        int end = start + len;

        text.getChars(0, len, buf, start);
        count = newcount;

        boolean hasSpecial = false;

        for (int i = start; i < end; ++i) {
            char ch = buf[i];
            if (ch < specicalFlags_singleQuotes.length && specicalFlags_singleQuotes[ch] != 0) {
                if (!hasSpecial) {
                    newcount += 3;
                    if (newcount > buf.length) {
                        expandCapacity(newcount);
                    }
                    count = newcount;

                    System.arraycopy(buf, i + 1, buf, i + 3, end - i - 1);
                    System.arraycopy(buf, 0, buf, 1, i);
                    buf[start] = '\'';
                    buf[++i] = '\\';
                    buf[++i] = replaceChars[(int) ch];
                    end += 2;
                    buf[count - 2] = '\'';

                    hasSpecial = true;
                } else {
                    newcount++;
                    if (newcount > buf.length) {
                        expandCapacity(newcount);
                    }
                    count = newcount;

                    System.arraycopy(buf, i + 1, buf, i + 2, end - i);
                    buf[i] = '\\';
                    buf[++i] = replaceChars[(int) ch];
                    end++;
                }
            }
        }

        buf[newcount - 1] = ':';
    }

    public void flush() {
        if (writer == null) {
            return;
        }

        try {
            writer.write(buf, 0, count);
            writer.flush();
        } catch (IOException e) {
            throw new JSONException(e.getMessage(), e);
        }
        count = 0;
    }
    
    final static int[]  sizeTable = { 9, 99, 999, 9999, 99999, 999999, 9999999, 99999999, 999999999, Integer.MAX_VALUE };

}
