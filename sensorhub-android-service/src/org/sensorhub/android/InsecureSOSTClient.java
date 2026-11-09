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

import org.sensorhub.impl.client.sost.SOSTClient;
import org.vast.ows.OWSException;
import org.vast.ows.OWSRequest;
import org.vast.ows.sos.SOSUtils;

import java.io.IOException;
import java.io.PrintStream;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.net.ssl.HttpsURLConnection;

public class InsecureSOSTClient extends SOSTClient
{
    public InsecureSOSTClient()
    {
        sosUtils = new InsecureSOSUtils();
    }

    private static final class InsecureSOSUtils extends SOSUtils
    {
        @Override
        public HttpURLConnection sendGetRequest(OWSRequest request) throws IOException, OWSException
        {
            if (request.getGetServer() == null)
                throw new OWSException(INVALID_ENDPOINT_MSG);

            String requestString = buildURLQuery(request);
            try {
                HttpURLConnection connection = open(requestString);
                connection.setConnectTimeout(request.getConnectTimeOut());
                connection.setReadTimeout(request.getConnectTimeOut());
                connection.connect();
                tryParseException(connection);
                return connection;
            } catch (IOException e) {
                throw new IOException(IO_ERROR_MSG + requestString, e);
            }
        }

        @Override
        public HttpURLConnection sendPostRequest(OWSRequest request) throws IOException, OWSException
        {
            String endpoint = request.getPostServer() != null ? request.getPostServer() : request.getGetServer();
            if (endpoint == null)
                throw new OWSException(INVALID_ENDPOINT_MSG);

            try {
                HttpURLConnection connection = open(endpoint.endsWith("?") ? endpoint.substring(0, endpoint.length() - 1) : endpoint);
                connection.setConnectTimeout(request.getConnectTimeOut());
                connection.setReadTimeout(request.getConnectTimeOut());
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-type", XML_MIME_TYPE);
                try (PrintStream output = new PrintStream(connection.getOutputStream())) {
                    writeXMLQuery(output, request);
                    output.flush();
                }
                connection.connect();
                tryParseException(connection);
                return connection;
            } catch (IOException e) {
                throw new IOException(IO_ERROR_MSG + request.getOperation(), e);
            }
        }

        @Override
        public HttpURLConnection sendPostRequestWithQuery(OWSRequest request) throws IOException, OWSException
        {
            String endpoint = request.getGetServer();
            if (endpoint == null) {
                endpoint = request.getPostServer();
                request.setGetServer(endpoint);
            }
            if (endpoint == null)
                throw new OWSException(INVALID_ENDPOINT_MSG);

            try {
                HttpURLConnection connection = open(buildURLQuery(request));
                connection.setConnectTimeout(request.getConnectTimeOut());
                connection.setReadTimeout(request.getConnectTimeOut());
                connection.setDoInput(true);
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                return connection;
            } catch (IOException e) {
                throw new IOException(IO_ERROR_MSG + request.getOperation(), e);
            }
        }

        private HttpURLConnection open(String url) throws IOException
        {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            if (connection instanceof HttpsURLConnection https) {
                UnsafeTls.configure(https);
            }
            return connection;
        }
    }
}
