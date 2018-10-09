/*
 * Copyright (c) 2017 PonySDK
 *  Owners:
 *  Luciano Broussal  <luciano.broussal AT gmail.com>
 *  Mathieu Barbier   <mathieu.barbier AT gmail.com>
 *  Nicolas Ciaravola <nicolas.ciaravola.pro AT gmail.com>
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

package com.ponysdk.core.server.websocket;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonObject;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WriteCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ponysdk.core.model.BooleanModel;
import com.ponysdk.core.model.CharsetModel;
import com.ponysdk.core.model.ServerToClientModel;
import com.ponysdk.core.server.application.UIContext;
import com.ponysdk.core.server.concurrent.AutoFlushedBuffer;
import com.ponysdk.core.util.StringDictionary;

public class WebSocketPusher extends AutoFlushedBuffer implements WriteCallback {

    private static final Logger log = LoggerFactory.getLogger(WebSocketPusher.class);

    private static final Charset ASCII_CHARSET = StandardCharsets.ISO_8859_1;
    private static final Charset UTF8_CHARSET = StandardCharsets.UTF_8;

    private static final StringDictionary stringDictionary;
    private static final JsonObject stringDisctionaryJson;

    private static final int MAX_UNSIGNED_BYTE_VALUE = Byte.MAX_VALUE * 2 + 1;
    private static final int MAX_UNSIGNED_SHORT_VALUE = Short.MAX_VALUE * 2 + 1;

    private final Session session;

    static {
        StringDictionary d = null;
        try (Stream<String> s = Files.lines(Paths.get("string.dictionary"))) {
            d = new StringDictionary(s);
        } catch (final IOException e) {
            d = new StringDictionary(Stream.empty());
        }
        stringDictionary = d;
        stringDisctionaryJson = Json.createObjectBuilder().add("dictio", stringDictionary.getStringsAsJsonArray()).build();
    }

    public WebSocketPusher(final Session session, final int bufferSize, final int maxChunkSize, final long timeoutMillis) {
        super(bufferSize, true, maxChunkSize, 0.25f, timeoutMillis);
        this.session = session;
    }

    @Override
    public void flush() {
        try {
            super.flush();
        } catch (final IOException e) {
            log.error("Can't write on the websocket, so we destroy the application", e);
            UIContext.get().onDestroy();
        }
    }

    @Override
    protected void doFlush(final ByteBuffer bufferToFlush) {
        session.getRemote().sendBytes(bufferToFlush, this);
    }

    @Override
    protected void closeFlusher() {
        session.close();
    }

    @Override
    public void writeFailed(final Throwable t) {
        if (t instanceof Exception) {
            onFlushFailure((Exception) t);
        } else {
            // wrap error into a generic exception to notify producer thread and rethrow the original throwable
            onFlushFailure(new IOException(t));
            throw (RuntimeException) t;
        }
    }

    @Override
    public void writeSuccess() {
        onFlushCompletion();
    }

    protected void encodeStringDictionary() {
        encode(ServerToClientModel.STRING_DICTIONARY, stringDisctionaryJson);
    }

    protected void encode(final ServerToClientModel model, final Object value) {
        if (log.isDebugEnabled()) log.debug("Writing in the buffer : {} => {}", model, value);
        try {
            switch (model.getTypeModel()) {
                case NULL:
                    write(model);
                    break;
                case BOOLEAN:
                    write(model, (boolean) value);
                    break;
                case BYTE:
                    write(model, (byte) value);
                    break;
                case SHORT:
                    write(model, (short) value);
                    break;
                case INTEGER:
                    write(model, (int) value);
                    break;
                case LONG:
                    write(model, (long) value);
                    break;
                case DOUBLE:
                    write(model, (double) value);
                    break;
                case STRING_ASCII:
                    write(model, (String) value, ASCII_CHARSET);
                case STRING:
                    write(model, (String) value, UTF8_CHARSET);
                    break;
                case JSON_OBJECT:
                    write(model, (JsonObject) value);
                    break;
                default:
                    log.error("Unknow model type : {}", model.getTypeModel());
                    break;
            }
        } catch (final IOException e) {
            log.error("Can't write on the websocket, so we destroy the application", e);
            UIContext.get().onDestroy();
        }
    }

    private void write(final ServerToClientModel model) throws IOException {
        putModelKey(model);
    }

    private void write(final ServerToClientModel model, final boolean value) throws IOException {
        write(model, value ? BooleanModel.TRUE.getValue() : BooleanModel.FALSE.getValue());
    }

    private void write(final ServerToClientModel model, final byte value) throws IOException {
        putModelKey(model);
        put(value);
    }

    private void write(final ServerToClientModel model, final short value) throws IOException {
        putModelKey(model);
        putShort(value);
    }

    private void write(final ServerToClientModel model, final int value) throws IOException {
        putModelKey(model);
        putInt(value);
    }

    private boolean write(final ServerToClientModel model, final String value, final Charset charset, final int maxLength,
                          final PutLength putLength)
            throws IOException {
        putModelKey(model);

        if (value != null) {
            final Integer indexInDictionary = stringDictionary.getStringIndex(value);
            if (indexInDictionary == null) {
                try {
                    final byte[] bytes = value.getBytes(charset);
                    final int length = bytes.length;
                    if (length > maxLength) return false;
                    if (length == value.length()) put(CharsetModel.ASCII.getValue());
                    else put(CharsetModel.UTF8.getValue());
                    putLength.apply(length);
                    put(bytes);
                } catch (final UnsupportedEncodingException e) {
                    throw new IllegalArgumentException("Cannot convert message : " + value);
                }
            } else {
                put(CharsetModel.STRING_DICTIONARY.getValue());
                putUnsignedShort(indexInDictionary);
            }
        } else {
            put(CharsetModel.ASCII.getValue());
            putUnsignedShort(0);
        }
        return true;
    }

    private void write(final ServerToClientModel model, final long longValue) throws IOException {
        write(model, String.valueOf(longValue), ASCII_CHARSET, MAX_UNSIGNED_BYTE_VALUE, l -> putUnsignedByte((short) l));
    }

    private void write(final ServerToClientModel model, final double doubleValue) throws IOException {
        write(model, String.valueOf(doubleValue), ASCII_CHARSET, MAX_UNSIGNED_BYTE_VALUE, l -> putUnsignedByte((short) l));
    }

    private void write(final ServerToClientModel model, final String value, final Charset charset) throws IOException {
        if (!write(model, value, charset, MAX_UNSIGNED_SHORT_VALUE, this::putUnsignedShort)) {
            throw new IllegalArgumentException("Message too big (" + value.length() + " > " + MAX_UNSIGNED_SHORT_VALUE
                    + "), use a JsonObject instead : " + value.substring(0, Math.min(value.length(), 100)) + "...");
        }
    }

    private void write(final ServerToClientModel model, final JsonObject jsonObject) throws IOException {
        write(model, jsonObject.toString(), UTF8_CHARSET, Integer.MAX_VALUE, this::putInt);
    }

    private void putModelKey(final ServerToClientModel model) throws IOException {
        putUnsignedByte(model.getValue());
    }

    public final void putUnsignedByte(final short shortValue) throws IOException {
        put((byte) (shortValue & 0xFF));
    }

    public final void putUnsignedShort(final int intValue) throws IOException {
        putShort((short) (intValue & 0xFFFF));
    }

    public final void putUnsignedInteger(final long longValue) throws IOException {
        putInt((int) (longValue & 0xFFFFFF));
    }

    private static interface PutLength {

        void apply(int length) throws IOException;
    }

}
