/*
 * Copyright (c) 2011 PonySDK
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

package com.ponysdk.sample.client.page;

import com.ponysdk.ui.server.basic.PFlexTable;
import com.ponysdk.ui.server.basic.PLabel;
import com.ponysdk.ui.server.basic.PScrollPanel;

public class FlexTablePageActivity extends SamplePageActivity {

    public FlexTablePageActivity() {
        super("Flex Table", "Table");
    }

    @Override
    protected void onFirstShowPage() {
        super.onFirstShowPage();

        final PFlexTable table = new PFlexTable();
        table.setCellPadding(0);
        table.setCellSpacing(0);
        table.setSizeFull();

        for (int r = 0; r < 100; r++) {
            for (int c = 0; c < 10; c++) {
                table.setWidget(r, c, new PLabel(r + "_" + c));
            }
        }

        PScrollPanel scrollPanel = new PScrollPanel();
        scrollPanel.setWidget(table);
        scrollPanel.setSizeFull();

        examplePanel.setWidget(scrollPanel);
    }
}
