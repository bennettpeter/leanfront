/*
 * Copyright (c) 2019-2020 Peter Bennett
 *
 * This file is part of MythTV-leanfront.
 *
 * MythTV-leanfront is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * MythTV-leanfront is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with MythTV-leanfront.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.mythtv.leanfront.data;

import android.annotation.SuppressLint;
import android.util.Log;
import android.util.Xml;

import org.mythtv.leanfront.model.Settings;
import org.mythtv.leanfront.ui.MainFragment;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.AccessDeniedException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Pattern;


public class XmlNode {
    private static final String TAG = "lfe";
    private static final String CLASS = "XmlNode";

    private String name;
    private HashMap<String, XmlNode> childMap = new HashMap<>();
    private HashMap<String, String> attributeMap = new HashMap<>();
    private String text = null;
    private XmlNode nextSibling;

    public static String getIpAndPort(String hostname) throws IOException, XmlPullParserException {
        String backendIP = Settings.getString("pref_backend");
        backendIP = fixIpAddress(backendIP);
        String mainPort = Settings.getString("pref_http_port");
        if (backendIP.length() == 0 || mainPort.length() == 0) {
            Log.e(TAG, CLASS + " Backend port or IP address not specified");
            return null;
        }
        BackendCache bCache = BackendCache.getInstance();
        if (!backendIP.equals(bCache.sBackendIP) || !mainPort.equals(bCache.sMainPort)) {
            BackendCache.flush();
        }
        if (hostname == null)
            return bCache.sBackendIP + ":" + mainPort;
        String hostIpAndPort = bCache.sHostMap.get(hostname);
        if (hostIpAndPort == null) {
            String urlString = XmlNode.mythApiUrl(null,
                    "/Myth/GetSetting?Key=BackendServerAddr&HostName="
                            + hostname);
            XmlNode response = XmlNode.fetch(urlString, null);
            String hostIp = response.getString();
            // This is needed to support mythbackend v0.28, where there is
            // no setting for BackendServerAddr
            if (hostIp == null || hostIp.startsWith("127.") || hostIp.equalsIgnoreCase("localhost"))
                hostIp = backendIP;
            hostIp = fixIpAddress(hostIp);
            // These are removed now. I don't know why this was here
//            if (hostIp == null || hostIp.length() == 0)
//                return "";

            // This removed because the system may use 6744 when 6544 is the status port
            // If your slave backend has a different port you are out of luck.
//            urlString = XmlNode.mythApiUrl(null,
//                    "/Myth/GetSetting?Key=BackendStatusPort&HostName="
//                            + hostname);
//            response = XmlNode.fetch(urlString, null);
//            String port = response.getString();
//            if (port == null)
//                port = mainPort;
//            hostIpAndPort = hostIp + ":" + port;
            hostIpAndPort = hostIp + ":" + bCache.sMainPort;
            bCache.sHostMap.put(hostname, hostIpAndPort);
        }
        return hostIpAndPort;
    }

    public static boolean isSetupDone() {
        try {
            if (XmlNode.getIpAndPort(null) == null)
                return false;
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public static String fixIpAddress(String ipAddress) {
        if (ipAddress != null) {
            if (ipAddress.indexOf(':') > -1 && ipAddress.charAt(0)!= '[')
                ipAddress = "[" + ipAddress + "]";
        }
        return ipAddress;
    }

    public static XmlNode parseStream(InputStream in) throws XmlPullParserException, IOException {
        XmlPullParser parser = Xml.newPullParser();
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true);
        parser.setInput(in, "utf-8");
        int eventType = parser.getEventType();
        XmlNode ret = null;

        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG)
                ret = parseNode(parser);
            eventType = parser.next();
        }
        return ret;
    }

    // called on a START_TAG
    private static XmlNode parseNode(XmlPullParser parser) throws IOException, XmlPullParserException {
        XmlNode ret = new XmlNode();
        ret.name = parser.getName();
        int numAttribs = parser.getAttributeCount();
        for (int ix = 0; ix < numAttribs; ix++) {
            ret.attributeMap.put(parser.getAttributeName(ix),
                    parser.getAttributeValue(ix));
        }
        // For wsdls fake out tag name as nme attribute
        String attName = ret.attributeMap.get("name");
        if (attName != null)
            ret.name = attName;
        int eventType = XmlPullParser.START_TAG;
        while (eventType != XmlPullParser.END_TAG) {
            eventType = parser.next();
            if (eventType == XmlPullParser.START_TAG) {
                XmlNode child = parseNode(parser);
                XmlNode priorChild = ret.childMap.get(child.name);
                if (priorChild == null)
                    ret.childMap.put(child.name, child);
                else {
                    while (priorChild.nextSibling != null)
                        priorChild = priorChild.nextSibling;
                    priorChild.nextSibling = child;
                }
            } else if (eventType == XmlPullParser.TEXT) {
                ret.text = parser.getText();
            }
        }
        return ret;
    }

    /**
     * Fetch XML object from a given URL.
     *
     * @return the XmlNode representation of the response
     * @throws XmlPullParserException
     * @throws IOException
     */
    public static XmlNode fetch(String urlString, String requestMethod)
            throws XmlPullParserException, IOException {
        BackendCache bCache = BackendCache.getInstance();
        XmlNode ret = null;
        URL url = null;
        HttpURLConnection urlConnection = null;
        InputStream is = null;
        try {
            url = new URL(urlString);
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.addRequestProperty("Cache-Control", "no-cache");
            String auth = BackendCache.getInstance().authorization;
            if (auth != null && auth.length() > 0 )
                urlConnection.addRequestProperty("Authorization", auth);
            urlConnection.setConnectTimeout(5000);
            // 5 minutes - should never be this long.
            urlConnection.setReadTimeout(300000);
            if (requestMethod != null)
                urlConnection.setRequestMethod(requestMethod);
            Log.i(TAG, CLASS + " URL: " + urlString);
            is = urlConnection.getInputStream();
            Log.i(TAG, CLASS + " Response: " + urlConnection.getResponseCode()
                    + " " + urlConnection.getResponseMessage());
            ret = XmlNode.parseStream(is);
            bCache.isConnected = true;
        } catch(FileNotFoundException e) {
            int respCode =  urlConnection.getResponseCode();
            Log.i(TAG, CLASS + " Response: " + respCode
                    + " " + urlConnection.getResponseMessage());
            if (respCode == 401) {
                // MythTask will process login
                MainFragment.restartMythTask();
                throw new IOException("Unauthorized: 401", e);
            }
            throw e;
        } catch(IOException e) {
            bCache.isConnected = false;
            Log.i(TAG, CLASS + " Response: " + urlConnection.getResponseCode()
                    + " " + urlConnection.getResponseMessage());
            if (!urlString.endsWith("/Myth/DelayShutdown"))
                MainFragment.restartMythTask();
            throw e;
        } finally {
            if (urlConnection != null)
                urlConnection.disconnect();
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    Log.e(TAG, CLASS + " XML feed closed: " + urlString, e);
                }
            }
        }
        return ret;
    }

    public static XmlNode safeFetch(String urlString, String requestMethod) {
        try {
            return fetch(urlString,requestMethod);
        } catch(IOException | XmlPullParserException e) {
            Log.i(TAG, CLASS + " Unsupported url " + urlString);
            return new XmlNode();
        }
    }


    public XmlNode getNode(String[] tag, int index) {
        XmlNode node = this;
        for (String item : tag) {
            node = node.childMap.get(item);
            if (node == null)
                return null;
        }
        for (int ix = 0; ix<index; ix++) {
            node = node.nextSibling;
            if (node == null)
                return null;
        }
        return node;
    }

    public XmlNode getNode(String tag, int index) {
        String[] tags = {tag};
        return getNode(tags,index);
    }

    public XmlNode getNode(String tag) {
        String[] tags = {tag};
        return getNode(tags,0);
    }

    public XmlNode getNextSibling() {
        return nextSibling;
    }

    public String getString(String[] tag, int index) {
        XmlNode node = getNode(tag, index);
        if (node == null)
            return null;
        return node.text;
    }

    public String getString(String[] tag) {
        return getString(tag, 0);
    }

    public String getString(String tag) {
        String[] tags = {tag};
        return getString(tags, 0);
    }

    public void setString(String text) {
        this.text = text;
    }

    public String getString() { return text; }

    public int getInt(String tag, int defaultValue) {
        int result = defaultValue;
        String strValue = getString(tag);
        if (strValue != null) {
            try {
                result = Integer.parseInt(strValue);
            } catch (NumberFormatException e) {
                result = defaultValue;
            }
        }
        return result;
    }

    public long getLong(String tag, long defaultValue) {
        long result = defaultValue;
        String strValue = getString(tag);
        if (strValue != null) {
            try {
                result = Long.parseLong(strValue);
            } catch (NumberFormatException e) {
                result = defaultValue;
            }
        }
        return result;
    }

    public boolean getBoolean() {
        boolean result = false;
        if (text != null)
            result = "true".equalsIgnoreCase(text);
        return result;
    }


    @SuppressLint("SimpleDateFormat")
    private static final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'Z");

    public Date getDate() {
        Date result = null;
        if (text !=  null) {
            try {
                result = dateFormat.parse(text + "+0000");
            } catch (ParseException e) {
                result = null;
            }
        }
        return result;
    }


    public String getAttribute(String name) {
        return attributeMap.get(name);
    }

    public String getName() {
        return name;
    }

    /**
     * Convenience method to allow extra data to be stored in a node
     * @param name
     * @param value
     */
    public void setAttribute(String name, String value) {
        attributeMap.put(name, value);
    }

    public static String mythApiUrl(String hostName, String params) throws IOException, XmlPullParserException {
        String ipAndPort = getIpAndPort(hostName);
        if (ipAndPort == null || ipAndPort.length() == 0)
            return "";
        String url = "http://";
        if ("true".equals(Settings.getString("pref_http_ssl")))
            url = "https://";
        url = url + ipAndPort;
        if (params != null)
            url = url + params;
        return url;
    }

    // need to be able to run this on a null XmlNode, that is why it is static
    public static ArrayList<String> getStringList(XmlNode listNode) {
        ArrayList<String> ret = new ArrayList<>();
        ret.add("Default");
        if (listNode != null) {
            XmlNode stringNode = listNode.getNode("String");
            while (stringNode != null) {
                String value = stringNode.getString();
                if (!"Default".equals(value))
                    ret.add(value);
                stringNode = stringNode.getNextSibling();
            }
        }
        return ret;
    }

}