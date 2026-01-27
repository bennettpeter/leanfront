/*
 * Copyright (c) 2021 Peter Bennett
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

import android.app.Activity;
import android.content.Context;
import android.text.Html;
import android.text.Spanned;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Log;

import androidx.annotation.Nullable;

import org.mythtv.leanfront.MyApplication;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AsyncRemoteCall implements Runnable {

    public String stringParameter;
    public ArrayList<Parser> results = new ArrayList<>();
    public Integer[] inTasks;
    public int [] tasks;
    private final Listener listener;
    public static final int ACTION_LOOKUP_TVMAZE = 1;
    public static final int ACTION_LOOKUP_TV = 2;
    public static final int ACTION_LOOKUP_MOVIE = 3;
    public static final int ACTION_LOOKUP_TVDB = 4;
    private static final String TAG = "lfe";
    private static final String CLASS = "AsyncRemoteCall";
    private final static ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Activity activity;

    public interface Listener {
        default void onPostExecute(AsyncRemoteCall taskRunner) {}
    }

    public AsyncRemoteCall(@Nullable Activity activity, @Nullable Listener listener) {
        this.activity = activity;
        this.listener = listener;
    }

    public void execute(Integer ... tasks) {
        inTasks = tasks;
        executor.submit(this);
    }

    @Override
    public void run() {
        try {
            runTasks();
        } catch (Throwable e) {
            e.printStackTrace();
        }
        finally {
            if (listener != null)
                if (activity == null)
                    listener.onPostExecute(this);
                else
                    activity.runOnUiThread(() -> listener.onPostExecute(this));
        }
    }

    protected Void runTasks() {
        this.tasks = new int[inTasks.length];
        for (int count = 0; count < tasks.length; count++) {
            int task = inTasks[count];
            this.tasks[count] = task;
            Parser parser;
            switch (task) {
                case ACTION_LOOKUP_TV:
                case ACTION_LOOKUP_MOVIE:
                    parser = new TmdbParser(task, stringParameter);
                    break;
                case ACTION_LOOKUP_TVMAZE:
                    parser = new TvMazeParser(task, stringParameter);
                    break;
                case ACTION_LOOKUP_TVDB:
                    parser = new TvDbParser(task, stringParameter);
                    break;
                default:
                    return null;
            }
            parser.fetch(null);
            results.add(parser);
        }
        return null;
    }

    protected void onPostExecute(Void result) {
        if (listener != null)
            listener.onPostExecute(this);
    }

    public static class TvEntry {
        public int id;
        public String name;
        public String firstAirDate;
        public String overview;
        public String type;
    }


    public static abstract class Parser {
        public int task;
        public String parameter;
        public ArrayList<TvEntry> entries = new ArrayList<>();
        public abstract void parseStream(InputStream in);
        public abstract String getUrlString();
        protected void setupConnection(HttpURLConnection con) { }

        public int fetch(String requestMethod) {
            int ret = 0;
            URL url = null;
            HttpURLConnection urlConnection = null;
            String urlString = getUrlString();
            InputStream is = null;
            try {
                url = new URL(urlString);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestProperty("Cache-Control", "no-cache");
                urlConnection.setConnectTimeout(5000);
                // 5 minutes - should never be this long.
                urlConnection.setReadTimeout(300000);
                if (requestMethod != null)
                    urlConnection.setRequestMethod(requestMethod);
                setupConnection(urlConnection);
                Log.d(TAG, CLASS + " URL: " + urlString);
                is = urlConnection.getInputStream();
                parseStream(is);
            } catch(FileNotFoundException e) {
                Log.e(TAG, CLASS + " Exception accessing: " + urlString, e);
                ret = 404;
            } catch(IOException e) {
                Log.e(TAG, CLASS + " Exception accessing: " + urlString, e);
                ret = 500;
            } finally {
                if (urlConnection != null) {
                    try {
                        Log.d(TAG, CLASS + " Response: " + urlConnection.getResponseCode()
                                + " " + urlConnection.getResponseMessage());
                    } catch (IOException e) {
                        Log.e(TAG, CLASS + " Exception getting response code: " + urlString, e);
                    }
                    urlConnection.disconnect();
                }
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        Log.e(TAG, CLASS + " Exception closing stream: " + urlString, e);
                    }
                }
            }
            return ret;
        }
    }

    public static class TmdbParser extends Parser {
        private static final String BASEURL = "https://api.themoviedb.org/3/";
        private static final String APIKEY = "c27cb71cff5bd76e1a7a009380562c62";

        public TmdbParser(int task, String parameter) {
            this.task = task;
            this.parameter = parameter;
        }

        @Override
        public String getUrlString() {
            StringBuilder urlString = new StringBuilder(BASEURL);
            switch (task) {
                case ACTION_LOOKUP_TV:
                    urlString.append("search/tv?");
                    break;
                case ACTION_LOOKUP_MOVIE:
                    urlString.append("search/movie?");
                    break;
            }

            Context context = MyApplication.getAppContext();
            Locale locale;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                locale = context.getResources().getConfiguration().getLocales().get(0);
            }
            else
                locale = context.getResources().getConfiguration().locale;
            try {
                urlString.append("query=")
                        .append(URLEncoder.encode(parameter, "UTF-8"))
                        .append("&api_key=")
                        .append(APIKEY)
                        .append("&language=")
                        .append(locale.getLanguage())
                        .append("&page=1");
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, CLASS + " Exception encoding URL: " + parameter, e);
            }
            return urlString.toString();
        }

        @Override
        public void parseStream(InputStream in) {
            // Structure of json:
            // {
            //   "**OTHER TAGS**": XXXX,
            //   "results": [
            //     {
            //       "first_air_date": "2002-07-12",
            //       "id": 1695,
            //       "name": "Monk",
            //       "overview": "Adrian Monk was once a rising star with the ...",
            //       "**OTHER_TAGS**": XXXX,
            //     },
            //     {
            //       **MORE_RESULTS** ...
            //     }
            //   ],
            //   "**OTHER TAGS**": XXXX,
            // }
            JsonReader reader = null;
            try {
                reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                reader.beginObject();
                doc:
                for (;;) {
                    switch (reader.peek()) {
                        case NAME:
                            String name = reader.nextName();
                            switch (name) {
                                case "results":
                                    reader.beginArray();
                                    results:
                                    for (;;) {
                                        if (reader.peek() == JsonToken.END_ARRAY) {
                                            reader.endArray();
                                            break results;
                                        }
                                        reader.beginObject();
                                        TvEntry entry = new TvEntry();
                                        entry:
                                        for (;;) {
                                            switch (reader.peek()) {
                                                case NAME:
                                                    String name1 = reader.nextName();
                                                    switch (name1) {
                                                        case "id":
                                                            entry.id = reader.nextInt();
                                                            break;
                                                        case "name":
                                                        case "title":
                                                            entry.name = reader.nextString();
                                                            break;
                                                        case "first_air_date":
                                                        case "release_date":
                                                            entry.firstAirDate = reader.nextString();
                                                            break;
                                                        case "overview":
                                                            entry.overview = reader.nextString();
                                                            break;
                                                        default:
                                                            reader.skipValue();
                                                    }
                                                    break;
                                                case END_OBJECT:
                                                    entries.add(entry);
                                                    reader.endObject();
                                                    break entry;
                                            }
                                        } // entry
                                    } // results
                                    break;
                                default:
                                    reader.skipValue();
                            }
                            break;
                        case END_OBJECT:
                            reader.endObject();
                            break doc;
                        default:
                    }
                } // doc
            } catch (IOException | IllegalStateException e) {
                Log.e(TAG, CLASS + " Exception parsing: " + parameter, e);
            }
            finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.e(TAG, CLASS + " Exception closing reader: " + parameter, e);
                    }
                }
            } // finally
        } // parseStream
    } // TmdbParser


    public static class TvDbParser extends Parser {
        private static final String BASEURL = "https://api4.thetvdb.com/v4/";
        private static final String APIKEY = "708401c2-b73e-4ce0-97ec-8ef3a5038c3a";
        private static String bearerToken;

        public TvDbParser(int task, String parameter) {
            this.task = task;
            this.parameter = parameter;
        }

        @Override
        public int fetch(String requestMethod) {
            int ret = super.fetch(requestMethod);
            if (ret != 0)
                bearerToken = null;
            return ret;
        }

        @Override
        public void setupConnection(HttpURLConnection con) {
            if (bearerToken == null)
                getBearerToken();
            con.setRequestProperty("Accept", "application/json");
            con.setRequestProperty("" +
                    "Authorization", "Bearer " + bearerToken);
        }

        int getBearerToken() {
            int ret = 0;
            HttpURLConnection urlConnection = null;
            InputStream is = null;
            OutputStream os = null;
            String urlString = BASEURL + "login";
            bearerToken = null;
            try {
                URL url = new URL(urlString);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestProperty("Cache-Control", "no-cache");
                urlConnection.setRequestProperty("Content-Type", "application/json");
                urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setConnectTimeout(5000);
                urlConnection.setReadTimeout(5000);
                urlConnection.setRequestMethod("POST");
                urlConnection.setDoOutput(true);
                Log.d(TAG, CLASS + " URL: " + urlString);
                os = urlConnection.getOutputStream();
                final String jsonInputString = "{ \"apikey\": \"" + APIKEY + "\" }";
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
                os.close();
                is = urlConnection.getInputStream();
                parseLogin(is);
            } catch(FileNotFoundException e) {
                Log.e(TAG, CLASS + " Exception accessing: " + urlString, e);
                ret = 404;
            } catch(IOException e) {
                Log.e(TAG, CLASS + " Exception accessing: " + urlString, e);
                ret = 500;
            } finally {
                if (urlConnection != null) {
                    try {
                        Log.d(TAG, CLASS + " Response: " + urlConnection.getResponseCode()
                                + " " + urlConnection.getResponseMessage());
                    } catch (IOException e) {
                        Log.e(TAG, CLASS + " Exception getting response code: " + urlString, e);
                    }
                    urlConnection.disconnect();
                }
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException e) {
                        Log.e(TAG, CLASS + " Exception closing stream: " + urlString, e);
                    }
                }
            }
            return ret;
        }

        @Override
        public String getUrlString() {
            StringBuilder urlString = new StringBuilder(BASEURL);
            urlString.append("search?");

            try {
                urlString.append("query=")
                        .append(URLEncoder.encode(parameter, "UTF-8"))
                        .append("&limit=100");
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, CLASS + " Exception encoding URL: " + parameter, e);
            }
            return urlString.toString();
        }

        private void parseLogin(InputStream in) {
            // Structure of json:
            // {
            //     "status": "success",
            //     "data": {
            //              "token": "very long string"
            //     }
            // }

            JsonReader reader = null;
            try {
                reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                reader.beginObject();
                results:
                for (;;) {
                    switch (reader.peek()) {
                        case NAME:      // status or data
                            String name1 = reader.nextName();
                            switch (name1) {
                                case "data":
                                    reader.beginObject();
                                    data:
                                    for (;;) {
                                        switch (reader.peek()) {
                                            case NAME:      // token
                                                String name2 = reader.nextName();
                                                switch (name2) {
                                                    case "token":
                                                        bearerToken = reader.nextString();
                                                        break;
                                                    default:
                                                        reader.skipValue();     // not expected
                                                }
                                                break;
                                            case END_OBJECT:        // end of data tage
                                                reader.endObject();
                                                break data;
                                        }
                                    } // data
                                    break;
                                default:        // status
                                    reader.skipValue();
                            }
                            break;
                        case END_OBJECT:    // end of main object
                            reader.endObject();
                            break results;
                    }  // switch (reader.peek()
                } // results
            } catch (IOException | IllegalStateException e) {
                Log.e(TAG, CLASS + " Exception parsing: " + parameter, e);
            }
            finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.e(TAG, CLASS + " Exception closing reader: " + parameter, e);
                    }
                }
            } // finally
        } // parseLogin

        @Override
        public void parseStream(InputStream in) {
            // Structure of json:
            // {
            //    "status": "success",
            //    "data": [
            //       {
            //          "objectID": "series-75692",
            //          "aliases": [
            //                "Law and Order: Special Victims Unit",
            //                "Law and Order: SVU",
            //                "Law & Order: SVU",
            //                "Law & Order: Investigação Especial"
            //          ],
            //          "country": "usa",
            //            "id": "series-75692",
            //            "image_url": "https://artworks.thetvdb.com/banners/posters/75692-10.jpg",
            //            "name": "Law & Order: Special Victims Unit",
            //            "first_air_time": "1999-09-20",
            //            "overview": "Based out of the New York City Police Department's 16th precinct in Manhattan, Law & Order: Special Victims Unit (SVU) delves into the dark side of the NYC underworld as the detectives investigate and prosecute various sexually oriented crimes including rape, pedophilia, and domestic violence. They also investigate the abuses of children, the disabled and elderly victims of non-sexual crimes who require specialist handling, all while trying to balance the effects of the investigation on their own lives as they try not to let the dark side of these crimes affect them. Its stories also touch on the political and societal issues associated with gender identity, sexual preferences, and equality rights. The unit also works with the Manhattan District Attorney's office as they prosecute cases and seek justice for victims. The series often uses stories that are \"ripped from the headlines\" or based on real crimes. Such episodes take a real crime and fictionalize it by changing some details.",
            //            "primary_language": "eng",
            //            "primary_type": "series",
            //            "status": "Continuing",
            //            "type": "series",
            //            "tvdb_id": "75692",
            //            "year": "1999",
            //            "slug": "law-and-order-special-victims-unit",
            //            ... other tags
            //       },
            //      more occurrences
            //    ]
            // }

            JsonReader reader = null;
            try {
                reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                reader.beginObject();
                results:
                for (;;) {
                    entry:
                    for (;;) {
                        switch (reader.peek()) {
                            case NAME:
                                String name1 = reader.nextName();
                                switch (name1) {
                                    case "data":
                                        reader.beginArray();            // start of "data"
                                        shows:
                                        for (;;) {                      // shows
                                            switch (reader.peek()) {
                                                case BEGIN_OBJECT:      // start a show entry
                                                    reader.beginObject();
                                                    TvEntry show = new TvEntry();
                                                    show:
                                                    for (;;) {          // fields in a show
                                                        switch (reader.peek()) {
                                                            case NAME:
                                                                String name2 = reader.nextName();
                                                                switch (name2) {
                                                                    case "tvdb_id":
                                                                        show.id = reader.nextInt();
                                                                        break;
                                                                    case "name":
                                                                        show.name = reader.nextString();
                                                                        break;
                                                                    case "first_air_time":
                                                                        show.firstAirDate = reader.nextString();
                                                                        break;
                                                                    case "overview":
                                                                        Spanned spanned;
                                                                        if (android.os.Build.VERSION.SDK_INT >= 24)
                                                                            spanned = Html.fromHtml(reader.nextString(), Html.FROM_HTML_MODE_COMPACT);
                                                                        else
                                                                            spanned = Html.fromHtml(reader.nextString());
                                                                        show.overview = spanned.toString();
                                                                        break;
                                                                    case "type":
                                                                        show.type = reader.nextString();
                                                                        break;
                                                                    default:
                                                                        reader.skipValue();
                                                                }
                                                                break;
                                                            case END_OBJECT:    // end show entry
                                                                entries.add(show);
                                                                reader.endObject();
                                                                break show;
                                                        }
                                                    } // show
                                                    break;
                                                case END_ARRAY:     // end show array
                                                    reader.endArray();
                                                    break shows;
                                            }
                                        }  // shows
                                        break;
                                    default:
                                        reader.skipValue();  // status or other top-level entries
                                        break;
                                }
                                break;
                            case END_OBJECT:
                                reader.endObject();
                                break results;
                        } //switch reader.peek()
                    } // entry
                } // results
            } catch (IOException | IllegalStateException e) {
                Log.e(TAG, CLASS + " Exception parsing: " + parameter, e);
            }
            finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.e(TAG, CLASS + " Exception closing reader: " + parameter, e);
                    }
                }
            } // finally
        } // parseStream
    } // TvDbParser

    public static class TvMazeParser extends Parser {
        private static final String BASEURL = "https://api.tvmaze.com/";
        // https://api.tvmaze.com/search/shows?q=Monk

        public TvMazeParser(int task, String parameter) {
            this.task = task;
            this.parameter = parameter;
        }

        @Override
        public String getUrlString() {
            StringBuilder urlString = new StringBuilder(BASEURL);
            urlString.append("search/shows?");

            try {
                urlString.append("q=")
                        .append(URLEncoder.encode(parameter, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, CLASS + " Exception encoding URL: " + parameter, e);
            }
            return urlString.toString();
        }

        @Override
        public void parseStream(InputStream in) {
            // Structure of json:
            // [
            //     {
            //        "**OTHER TAGS**": XXXX,
            //        "show" : {
            //           "id": 543,
            //           "name": "Mork & Mindy",
            //           "premiered": "2002-07-12",
            //           "summary": ""<p>In <b>Mork &amp; Mindy</b>, in which  ...</p>",
            //           "**OTHER_TAGS**": XXXX,
            //         },
            //     },
            //     {
            //       **MORE_RESULTS** ...
            //     }
            // ]
            JsonReader reader = null;
            try {
                reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                reader.beginArray();
                results:
                for (;;) {
                    if (reader.peek() == JsonToken.END_ARRAY) {
                        reader.endArray();
                        break results;
                    }
                    reader.beginObject();
                    entry:
                    for (;;) {
                        switch (reader.peek()) {
                            case NAME:
                                String name1 = reader.nextName();
                                switch (name1) {
                                    case "show":
                                        TvEntry show = new TvEntry();
                                        reader.beginObject();
                                        show:
                                        for (;;) {
                                            switch (reader.peek()) {
                                                case NAME:
                                                    String name2 = reader.nextName();
                                                    switch (name2) {
                                                        case "id":
                                                            show.id = reader.nextInt();
                                                            break;
                                                        case "name":
                                                            show.name = reader.nextString();
                                                            break;
                                                        case "premiered":
                                                            show.firstAirDate = reader.nextString();
                                                            break;
                                                        case "summary":
                                                            Spanned spanned;
                                                            if (android.os.Build.VERSION.SDK_INT >= 24)
                                                                spanned = Html.fromHtml(reader.nextString(),Html.FROM_HTML_MODE_COMPACT);
                                                            else
                                                                spanned =  Html.fromHtml(reader.nextString());
                                                            show.overview = spanned.toString();
                                                            break;
                                                        default:
                                                            reader.skipValue();
                                                    }
                                                    break;
                                                case END_OBJECT:
                                                    entries.add(show);
                                                    reader.endObject();
                                                    break show;
                                            }
                                        } // show
                                        break;
                                    default:
                                        reader.skipValue();
                                }
                                break;
                            case END_OBJECT:
                                reader.endObject();
                                break entry;
                        }
                    } // entry
                } // results
            } catch (IOException | IllegalStateException e) {
                Log.e(TAG, CLASS + " Exception parsing: " + parameter, e);
            }
            finally {
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        Log.e(TAG, CLASS + " Exception closing reader: " + parameter, e);
                    }
                }
            } // finally
        } // parseStream
    } // TvMazeParser


}
