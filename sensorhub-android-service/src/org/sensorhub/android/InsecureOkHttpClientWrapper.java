/***************************** BEGIN LICENSE BLOCK ***************************

 The contents of this file are subject to the Mozilla Public License, v. 2.0.
 If a copy of the MPL was not distributed with this file, You can obtain one
 at http://mozilla.org/MPL/2.0/.

 Software distributed under the License is distributed on an "AS IS" basis,
 WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 for the specific language governing rights and limitations under the License.

 Copyright (C) 2026 GeoRobotix. All Rights Reserved.

 ******************************* END LICENSE BLOCK ***************************/

package org.sensorhub.android;

import android.util.Log;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.OkHttpClient;


public class InsecureOkHttpClientWrapper extends OkHttpClientWrapper
{
    private static final String TAG = "ConSysTls";

    public InsecureOkHttpClientWrapper()
    {
        Log.i(TAG, "Creating insecure Connected Systems HTTP client; certificate and hostname validation are disabled");
        rebuildHttpClient();
    }

    @Override
    protected void rebuildHttpClient()
    {
        OkHttpClient.Builder builder = UnsafeTls.configure(new OkHttpClient.Builder());
        builder.eventListener(new EventListener() {
            @Override
            public void secureConnectEnd(Call call, Handshake handshake) {
                Log.i(TAG, "TLS handshake accepted for " + call.request().url().host());
            }

            @Override
            public void callFailed(Call call, IOException exception) {
                Log.e(TAG, "Connected Systems request failed for " + call.request().url(), exception);
            }
        });
        if (username != null && !username.isEmpty()) {
            String finalPwd = password != null ? new String(password) : "";
            builder.authenticator((route, response) -> response.request().newBuilder()
                    .header("Authorization", okhttp3.Credentials.basic(username, finalPwd))
                    .build());
        }
        this.http = builder.build();
    }

}
