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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;

public class StringDictionary {

    private final Map<String, Integer> stringToIndex;
    private final List<String> strings;
    private final int maxLength;

    public StringDictionary(final Stream<String> stringStream) {
        strings = Collections.unmodifiableList(stringStream.collect(Collectors.toCollection(ArrayList::new)));
        stringToIndex = new HashMap<>();
        int maxLength = 0;
        for (int i = 0; i < strings.size(); i++) {
            final String str = strings.get(i);
            stringToIndex.put(str, i);
            maxLength = Math.max(maxLength, str.length());
        }
        this.maxLength = maxLength;
    }

    public Integer getStringIndex(final String str) {
        return str.length() > maxLength ? null : stringToIndex.get(str);
    }

    public List<String> getStrings() {
        return strings;
    }

    public JsonArray getStringsAsJsonArray() {
        final JsonArrayBuilder array = Json.createArrayBuilder();
        for (final String str : strings) {
            array.add(str);
        }
        return array.build();
    }

    public int size() {
        return strings.size();
    }
}
