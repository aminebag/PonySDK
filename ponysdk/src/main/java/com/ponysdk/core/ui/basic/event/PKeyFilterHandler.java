/*
 * Copyright (c) 2011 PonySDK
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

package com.ponysdk.core.ui.basic.event;

import com.ponysdk.core.model.ClientToServerModel;
import com.ponysdk.core.server.application.UIContext;
import com.ponysdk.core.ui.eventbus.EventHandler;
import com.ponysdk.core.ui.model.PKeyCodes;

import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

public interface PKeyFilterHandler extends EventHandler {

    default PKeyCodes[] getFilteredKeys() {
        return null;
    }

    default JsonObject getJsonFilteredKeys() {
        final PKeyCodes[] listeningKeys = getFilteredKeys();

        if (listeningKeys != null) {
            final JsonArrayBuilder builder = UIContext.get().getJsonProvider().createArrayBuilder();

            for (final PKeyCodes code : listeningKeys) {
                builder.add(code.getCode());
            }

            final JsonObjectBuilder jsonObjectBuilder = UIContext.get().getJsonProvider().createObjectBuilder();
            jsonObjectBuilder.add(ClientToServerModel.KEY_FILTER.toStringValue(), builder.build());

            return jsonObjectBuilder.build();
        }

        return null;
    }

}
