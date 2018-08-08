/*
 * Copyright (c) 2018 PonySDK
 *  Owners:
 *  Luciano Broussal  <luciano.broussal AT gmail.com>
 *	Mathieu Barbier   <mathieu.barbier AT gmail.com>
 *	Nicolas Ciaravola <nicolas.ciaravola.pro AT gmail.com>
 *
 *  WebSite:
 *  http://code.google.com/p/pony-sdk/
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.ponysdk.core.util;

import java.io.IOException;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import javax.json.JsonArray;
import javax.json.JsonException;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonString;
import javax.json.JsonStructure;
import javax.json.JsonValue;
import javax.json.stream.JsonGenerationException;

/**
 *
 */
public class JsonEncoder {

    private final Writer writer;

    public JsonEncoder(final Writer writer) {
        this.writer = writer;
    }

    public void writeArray(final JsonArray array) {
        init();
        generatorWriteStartArray();
        for (final JsonValue value : array) {
            generatorWrite(value);
        }
        generatorWriteEnd();
        generatorFlushBuffer();
    }

    public void writeObject(final JsonObject object) {
        init();
        generatorWriteStartObject();
        for (final Map.Entry<String, JsonValue> e : object.entrySet()) {
            generatorWrite(e.getKey(), e.getValue());
        }
        generatorWriteEnd();
        generatorFlushBuffer();
    }

    public void write(final JsonStructure value) {
        if (value instanceof JsonArray) {
            writeArray((JsonArray) value);
        } else {
            writeObject((JsonObject) value);
        }
    }

    public void write(final JsonValue value) {
        switch (value.getValueType()) {
            case OBJECT:
                writeObject((JsonObject) value);
                return;
            case ARRAY:
                writeArray((JsonArray) value);
                return;
            default:
                init();
                generatorWrite(value);
                generatorFlushBuffer();
        }
    }

    private static final char[] INT_MIN_VALUE_CHARS = "-2147483648".toCharArray();
    private static final int[] INT_CHARS_SIZE_TABLE = { 9, 99, 999, 9999, 99999, 999999, 9999999, 99999999, 999999999,
                                                        Integer.MAX_VALUE };

    private static final char[] DIGIT_TENS = { '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '1', '1', '1', '1', '1', '1', '1',
                                               '1', '1', '1', '2', '2', '2', '2', '2', '2', '2', '2', '2', '2', '3', '3', '3', '3',
                                               '3', '3', '3', '3', '3', '3', '4', '4', '4', '4', '4', '4', '4', '4', '4', '4', '5',
                                               '5', '5', '5', '5', '5', '5', '5', '5', '5', '6', '6', '6', '6', '6', '6', '6', '6',
                                               '6', '6', '7', '7', '7', '7', '7', '7', '7', '7', '7', '7', '8', '8', '8', '8', '8',
                                               '8', '8', '8', '8', '8', '9', '9', '9', '9', '9', '9', '9', '9', '9', '9', };

    private static final char[] DIGIT_ONES = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6',
                                               '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3',
                                               '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0',
                                               '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7',
                                               '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', '1', '2', '3', '4',
                                               '5', '6', '7', '8', '9', '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', };

    /**
     * All possible chars for representing a number as a String
     */
    private static final char[] DIGITS = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' };

    private static enum Scope {
        IN_NONE,
        IN_OBJECT,
        IN_FIELD,
        IN_ARRAY
    }

    private static enum Context {
        IN_NONE_FIRST(Scope.IN_NONE, true),
        IN_OBJECT_FIRST(Scope.IN_OBJECT, true),
        IN_FIELD_FIRST(Scope.IN_FIELD, true),
        IN_ARRAY_FIRST(Scope.IN_ARRAY, true),
        IN_NONE(Scope.IN_NONE, false),
        IN_OBJECT(Scope.IN_OBJECT, false),
        IN_FIELD(Scope.IN_FIELD, false),
        IN_ARRAY(Scope.IN_ARRAY, false);

        private final Scope scope;
        private final boolean first;

        private Context(final Scope scope, final boolean first) {
            this.scope = scope;
            this.first = first;
        }

        private Context getNotFirst() {
            switch (this) {
                case IN_NONE_FIRST:
                    return Context.IN_NONE;
                case IN_OBJECT_FIRST:
                    return Context.IN_OBJECT;
                case IN_FIELD_FIRST:
                    return Context.IN_FIELD;
                case IN_ARRAY_FIRST:
                    return Context.IN_ARRAY;
                default:
                    return this;
            }
        }
    }

    private Context currentContext;
    private final Deque<Context> stack = new ArrayDeque<>();

    // Using own buffering mechanism as JDK's BufferedgeneratorWriter uses synchronized
    // methods. Also, flushBuffer() is useful when you don't want to actually
    // flush the underlying output source
    private final char buf[] = new char[4096]; // capacity >= INT_MIN_VALUE_CHARS.length
    private int len = 0;

    void init() {
        len = 0;
        currentContext = Context.IN_NONE_FIRST;
        stack.clear();
    }

    private void generatorWriteStartObject() {
        if (currentContext.scope == Scope.IN_OBJECT) {
            throw new JsonGenerationException("GENERATOR_ILLEGAL_METHOD " + currentContext.scope);
        }
        if (currentContext.scope == Scope.IN_NONE && !currentContext.first) {
            throw new JsonGenerationException("GENERATOR_ILLEGAL_MULTIPLE_TEXT");
        }
        generatorWriteComma();
        generatorWriteChar('{');
        stack.push(currentContext);
        currentContext = Context.IN_OBJECT_FIRST;

    }

    private void generatorWriteStartObject(final String name) {
        if (currentContext.scope != Scope.IN_OBJECT) {
            throw new JsonGenerationException("GENERATOR_ILLEGAL_METHOD " + currentContext.scope);
        }
        generatorWriteName(name);
        generatorWriteChar('{');
        stack.push(currentContext);
        currentContext = Context.IN_OBJECT_FIRST;

    }

    private void generatorWriteName(final String name) {
        generatorWriteComma();
        generatorWriteEscapedString(name);
        generatorWriteColon();

    }

    private void generatorWrite(final String name, final String fieldValue) {
        if (currentContext.scope != Scope.IN_OBJECT) {
            throw new JsonGenerationException("GENERATOR_ILLEGAL_METHOD " + currentContext.scope);
        }
        generatorWriteName(name);
        generatorWriteEscapedString(fieldValue);

    }

    private void generatorWrite(final String name, final int value) {
        if (currentContext.scope != Scope.IN_OBJECT) {
            throw new JsonGenerationException("GENERATOR_ILLEGAL_METHOD " + currentContext.scope);
        }
        generatorWriteName(name);
        generatorWriteInt(value);

    }

    private void generatorWrite(final String name, final long value) {
        if (currentContext.scope != Scope.IN_OBJECT) {
            throw new JsonGenerationException("GENERATOR_ILLEGAL_METHOD " + currentContext.scope);
        }
        generatorWriteName(name);
        generatorWriteString(String.valueOf(value));

    }

    private void generatorWrite(final String name, final double value) {
        if (currentContext.scope != Scope.IN_OBJECT) {
            throw new JsonGenerationException("GENERATOR_ILLEGAL_METHOD " + currentContext.scope);
        }
        if (Double.isInfinite(value) || Double.isNaN(value)) {
            throw new NumberFormatException("GENERATOR_DOUBLE_INFINITE_NAN");
        }
        generatorWriteName(name);
        generatorWriteString(String.valueOf(value));

    }

    private void generatorWrite(final String name, final BigInteger value) {
        if (currentContext.scope != Scope.IN_OBJECT) {
            throw new JsonGenerationException("GENERATOR_ILLEGAL_METHOD " + currentContext.scope);
        }
        generatorWriteName(name);
        generatorWriteString(String.valueOf(value));

    }

    private void generatorWrite(final String name, final BigDecimal value) {
        if (currentContext.scope != Scope.IN_OBJECT) {
            throw new JsonGenerationException("GENERATOR_ILLEGAL_METHOD " + currentContext.scope);
        }
        generatorWriteName(name);
        generatorWriteString(String.valueOf(value));

    }

    private void generatorWrite(final String name, final boolean value) {
        if (currentContext.scope != Scope.IN_OBJECT) {
            throw new JsonGenerationException("GENERATOR_ILLEGAL_METHOD " + currentContext.scope);
        }
        generatorWriteName(name);
        generatorWriteString(value ? "true" : "false");

    }

    private void generatorWriteNull(final String name) {
        if (currentContext.scope != Scope.IN_OBJECT) {
            throw new JsonGenerationException("GENERATOR_ILLEGAL_METHOD " + currentContext.scope);
        }
        generatorWriteName(name);
        generatorWriteString("null");

    }

    private void generatorWrite(final JsonValue value) {
        checkContextForValue();

        switch (value.getValueType()) {
            case ARRAY:
                final JsonArray array = (JsonArray) value;
                generatorWriteStartArray();
                for (final JsonValue child : array) {
                    generatorWrite(child);
                }
                generatorWriteEnd();
                break;
            case OBJECT:
                final JsonObject object = (JsonObject) value;
                generatorWriteStartObject();
                for (final Map.Entry<String, JsonValue> member : object.entrySet()) {
                    generatorWrite(member.getKey(), member.getValue());
                }
                generatorWriteEnd();
                break;
            case STRING:
                final JsonString str = (JsonString) value;
                generatorWrite(str.getString());
                break;
            case NUMBER:
                final JsonNumber number = (JsonNumber) value;
                generatorWriteValue(number.toString());
                popFieldContext();
                break;
            case TRUE:
                generatorWrite(true);
                break;
            case FALSE:
                generatorWrite(false);
                break;
            case NULL:
                generatorWriteNull();
                break;
        }

    }

    private void generatorWriteStartArray() {
        if (currentContext.scope == Scope.IN_OBJECT) {
            throw new JsonGenerationException("GENERATOR_ILLEGAL_METHOD " + currentContext.scope);
        }
        if (currentContext.scope == Scope.IN_NONE && !currentContext.first) {
            throw new JsonGenerationException("GENERATOR_ILLEGAL_MULTIPLE_TEXT");
        }
        generatorWriteComma();
        generatorWriteChar('[');
        stack.push(currentContext);
        currentContext = Context.IN_ARRAY_FIRST;

    }

    private void generatorWriteStartArray(final String name) {
        if (currentContext.scope != Scope.IN_OBJECT) {
            throw new JsonGenerationException("GENERATOR_ILLEGAL_METHOD " + currentContext.scope);
        }
        generatorWriteName(name);
        generatorWriteChar('[');
        stack.push(currentContext);
        currentContext = Context.IN_ARRAY_FIRST;

    }

    private void generatorWrite(final String name, final JsonValue value) {
        if (currentContext.scope != Scope.IN_OBJECT) {
            throw new JsonGenerationException("GENERATOR_ILLEGAL_METHOD " + currentContext.scope);
        }
        switch (value.getValueType()) {
            case ARRAY:
                final JsonArray array = (JsonArray) value;
                generatorWriteStartArray(name);
                for (final JsonValue child : array) {
                    generatorWrite(child);
                }
                generatorWriteEnd();
                break;
            case OBJECT:
                final JsonObject object = (JsonObject) value;
                generatorWriteStartObject(name);
                for (final Map.Entry<String, JsonValue> member : object.entrySet()) {
                    generatorWrite(member.getKey(), member.getValue());
                }
                generatorWriteEnd();
                break;
            case STRING:
                final JsonString str = (JsonString) value;
                generatorWrite(name, str.getString());
                break;
            case NUMBER:
                final JsonNumber number = (JsonNumber) value;
                generatorWriteValue(name, number.toString());
                break;
            case TRUE:
                generatorWrite(name, true);
                break;
            case FALSE:
                generatorWrite(name, false);
                break;
            case NULL:
                generatorWriteNull(name);
                break;
        }

    }

    private void generatorWrite(final String value) {
        checkContextForValue();
        generatorWriteComma();
        generatorWriteEscapedString(value);
        popFieldContext();

    }

    private void generatorWrite(final int value) {
        checkContextForValue();
        generatorWriteComma();
        generatorWriteInt(value);
        popFieldContext();

    }

    private void generatorWrite(final long value) {
        checkContextForValue();
        generatorWriteValue(String.valueOf(value));
        popFieldContext();

    }

    private void generatorWrite(final double value) {
        checkContextForValue();
        if (Double.isInfinite(value) || Double.isNaN(value)) {
            throw new NumberFormatException("GENERATOR_DOUBLE_INFINITE_NAN");
        }
        generatorWriteValue(String.valueOf(value));
        popFieldContext();

    }

    private void generatorWrite(final BigInteger value) {
        checkContextForValue();
        generatorWriteValue(value.toString());
        popFieldContext();

    }

    private void checkContextForValue() {
        if (!currentContext.first && currentContext.scope != Scope.IN_ARRAY && currentContext.scope != Scope.IN_FIELD
                || currentContext.first && currentContext.scope == Scope.IN_OBJECT) {
            throw new JsonGenerationException("GENERATOR_ILLEGAL_METHOD " + currentContext.scope);
        }
    }

    private void generatorWrite(final BigDecimal value) {
        checkContextForValue();
        generatorWriteValue(value.toString());
        popFieldContext();

    }

    private void popFieldContext() {
        if (currentContext.scope == Scope.IN_FIELD) {
            currentContext = stack.pop();
        }
    }

    private void generatorWrite(final boolean value) {
        checkContextForValue();
        generatorWriteComma();
        generatorWriteString(value ? "true" : "false");
        popFieldContext();

    }

    private void generatorWriteNull() {
        checkContextForValue();
        generatorWriteComma();
        generatorWriteString("null");
        popFieldContext();

    }

    private void generatorWriteValue(final String value) {
        generatorWriteComma();
        generatorWriteString(value);
    }

    private void generatorWriteValue(final String name, final String value) {
        generatorWriteComma();
        generatorWriteEscapedString(name);
        generatorWriteColon();
        generatorWriteString(value);
    }

    private void generatorWriteKey(final String name) {
        if (currentContext.scope != Scope.IN_OBJECT) {
            throw new JsonGenerationException("GENERATOR_ILLEGAL_METHOD " + currentContext.scope);
        }
        generatorWriteName(name);
        stack.push(currentContext);
        currentContext = Context.IN_FIELD;

    }

    private void generatorWriteEnd() {
        if (currentContext.scope == Scope.IN_NONE) {
            throw new JsonGenerationException("generatorWriteEnd() cannot be called in no context");
        }
        generatorWriteChar(currentContext.scope == Scope.IN_ARRAY ? ']' : '}');
        currentContext = stack.pop();
        popFieldContext();

    }

    protected void generatorWriteComma() {
        if (!currentContext.first && currentContext.scope != Scope.IN_FIELD) {
            generatorWriteChar(',');
        }
        currentContext = currentContext.getNotFirst();
    }

    protected void generatorWriteColon() {
        generatorWriteChar(':');
    }

    // begin, end-1 indexes represent characters that need not
    // be escaped
    //
    // XXXssssssssssssXXXXXXXXXXXXXXXXXXXXXXrrrrrrrrrrrrrrXXXXXX
    //    ^           ^                     ^             ^
    //    |           |                     |             |
    //   begin       end                   begin         end
    void generatorWriteEscapedString(final String string) {
        generatorWriteChar('"');
        final int len = string.length();
        for (int i = 0; i < len; i++) {
            final int begin = i;
            int end = i;
            char c = string.charAt(i);
            // find all the characters that need not be escaped
            // unescaped = %x20-21 | %x23-5B | %x5D-10FFFF
            while (c >= 0x20 && c <= 0x10ffff && c != 0x22 && c != 0x5c) {
                i++;
                end = i;
                if (i < len) {
                    c = string.charAt(i);
                } else {
                    break;
                }
            }
            // generatorWrite characters without escaping
            if (begin < end) {
                generatorWriteString(string, begin, end);
                if (i == len) {
                    break;
                }
            }

            switch (c) {
                case '"':
                case '\\':
                    generatorWriteChar('\\');
                    generatorWriteChar(c);
                    break;
                case '\b':
                    generatorWriteChar('\\');
                    generatorWriteChar('b');
                    break;
                case '\f':
                    generatorWriteChar('\\');
                    generatorWriteChar('f');
                    break;
                case '\n':
                    generatorWriteChar('\\');
                    generatorWriteChar('n');
                    break;
                case '\r':
                    generatorWriteChar('\\');
                    generatorWriteChar('r');
                    break;
                case '\t':
                    generatorWriteChar('\\');
                    generatorWriteChar('t');
                    break;
                default:
                    final String hex = "000" + Integer.toHexString(c);
                    generatorWriteString("\\u" + hex.substring(hex.length() - 4));
            }
        }
        generatorWriteChar('"');
    }

    void generatorWriteString(final String str, int begin, final int end) {
        while (begin < end) { // source begin and end indexes
            final int no = Math.min(buf.length - len, end - begin);
            str.getChars(begin, begin + no, buf, len);
            begin += no; // Increment source index
            len += no; // Increment dest index
            if (len >= buf.length) {
                generatorFlushBuffer();
            }
        }
    }

    void generatorWriteString(final String str) {
        generatorWriteString(str, 0, str.length());
    }

    void generatorWriteChar(final char c) {
        if (len >= buf.length) {
            generatorFlushBuffer();
        }
        buf[len++] = c;
    }

    // Not using Integer.toString() since it creates intermediary String
    // Also, we want the chars to be copied to our buffer directly
    void generatorWriteInt(final int num) {
        int size;
        if (num == Integer.MIN_VALUE) {
            size = INT_MIN_VALUE_CHARS.length;
        } else {
            size = num < 0 ? stringSize(-num) + 1 : stringSize(num);
        }
        if (len + size >= buf.length) {
            generatorFlushBuffer();
        }
        if (num == Integer.MIN_VALUE) {
            System.arraycopy(INT_MIN_VALUE_CHARS, 0, buf, len, size);
        } else {
            fillIntChars(num, buf, len + size);
        }
        len += size;
    }

    // flushBuffer generatorWrites the buffered contents to generatorWriter. But incase of
    // byte stream, an OuputStreamgeneratorWriter is created and that buffers too.
    // We may need to call OutputStreamgeneratorWriter#flushBuffer() using
    // reflection if that is really required (commented out below)
    void generatorFlushBuffer() {
        try {
            if (len > 0) {
                writer.write(buf, 0, len);
                len = 0;
            }
        } catch (final IOException ioe) {
            throw new JsonException("GENERATOR_WRITE_IO_ERR", ioe);
        }
    }

    //    private static final Method flushBufferMethod;
    //    static {
    //        Method m = null;
    //        try {
    //            m = OutputStreamgeneratorWriter.class.getDeclaredMethod("flushBuffer");
    //            m.setAccessible(true);
    //        } catch (Exception e) {
    //            // no-op
    //        }
    //        flushBufferMethod = m;
    //    }
    //    void flushBufferOSW() {
    //        flushBuffer();
    //        if (generatorWriter instanceof OutputStreamgeneratorWriter) {
    //            try {
    //                flushBufferMethod.invoke(generatorWriter);
    //            } catch (Exception e) {
    //                // no-op
    //            }
    //        }
    //    }

    // Requires positive x
    private static int stringSize(final int x) {
        for (int i = 0;; i++)
            if (x <= INT_CHARS_SIZE_TABLE[i]) return i + 1;
    }

    /**
     * Places characters representing the integer i into the
     * character array buf. The characters are placed into
     * the buffer backwards starting with the least significant
     * digit at the specified index (exclusive), and working
     * backwards from there.
     *
     * Will fail if i == Integer.MIN_VALUE
     */
    private static void fillIntChars(int i, final char[] buf, final int index) {
        int q, r;
        int charPos = index;
        char sign = 0;

        if (i < 0) {
            sign = '-';
            i = -i;
        }

        // Generate two digits per iteration
        while (i >= 65536) {
            q = i / 100;
            // really: r = i - (q * 100);
            r = i - ((q << 6) + (q << 5) + (q << 2));
            i = q;
            buf[--charPos] = DIGIT_ONES[r];
            buf[--charPos] = DIGIT_TENS[r];
        }

        // Fall thru to fast mode for smaller numbers
        // assert(i <= 65536, i);
        for (;;) {
            q = i * 52429 >>> 16 + 3;
            r = i - ((q << 3) + (q << 1)); // r = i-(q*10) ...
            buf[--charPos] = DIGITS[r];
            i = q;
            if (i == 0) break;
        }
        if (sign != 0) {
            buf[--charPos] = sign;
        }
    }

}
