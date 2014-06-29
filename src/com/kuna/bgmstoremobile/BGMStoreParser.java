package com.kuna.bgmstoremobile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringEscapeUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import android.os.Handler;
import net.htmlparser.jericho.Element;
import net.htmlparser.jericho.HTMLElementName;
import net.htmlparser.jericho.Source;

public class BGMStoreParser {
    private static boolean isRandomSongParsing = false;
    public static void getRandomSong(final Handler h) {
        if (isRandomSongParsing) {
            return;
        }

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                isRandomSongParsing = true;

                try {
                    URL nURL = new URL("http://bgmstore.net/random");
                    InputStream html = nURL.openStream();
                    Source source = new Source(new InputStreamReader(html, "utf-8"));

                    SongData data = new SongData();

                    List<Element> eles;
                    eles = source.getAllElements(HTMLElementName.DIV);
                    for (Element e: eles) {
                        if (e.getAttributeValue("class") == null) {
                            continue;
                        }
                        if (e.getAttributeValue("class").indexOf("titleBox") >= 0) {
                            data.title = e.getTextExtractor().setIncludeAttributes(true).toString();
                            break;
                        }
                    }

                    eles = source.getAllElements(HTMLElementName.UL);
                    for (Element e: eles) {
                        if (e.getAttributeValue("class") == null) {
                            continue;
                        }
                        if (e.getAttributeValue("class").equals("dropdown-menu")) {
                            data.url = e.getAllElements(HTMLElementName.A).get(1).getAttributeValue("href");
                            break;
                        }
                    }

                    h.obtainMessage(0, data).sendToTarget();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                isRandomSongParsing = false;
            }
        });

        t.start();
    }

    private static boolean isBMSListParsing = false;
    public static void parseBGMStoreList(final Handler h) {
        if (isBMSListParsing) {
            return;
        }

        Thread t = new Thread(new Runnable() {
            @Override
            public void run() {
                isBMSListParsing = true;

                try {
                    Source source = new Source(downloadBGMHTML());

                    List<SongData> lsd = new ArrayList<SongData>();

                    List<Element> eles = source.getAllElements(HTMLElementName.TR);
                    for (Element e: eles) {
                        SongData data = new SongData();

                        List<Element> as = e.getAllElements(HTMLElementName.A);
                        for (Element e_: as) {
                            if (e_.getAttributeValue("class").equals("title")) {
                                data.title = e_.getTextExtractor().toString();
                                break;
                            }
                        }

                        data.url = e.getAllElements(HTMLElementName.UL).get(0)
                                .getAllElements(HTMLElementName.A).get(1)
                                .getAttributeValue("href");

                        lsd.add(data);
                    }

                    h.obtainMessage(0, lsd).sendToTarget();
                } catch (Exception e) {
                    e.printStackTrace();
                }

                isBMSListParsing = false;
            }
        });

        t.start();
    }

    public static String downloadBGMHTML() {
        String doc = "";
        try {
            URL url = new URL("http://bgmstore.net/servlet/more_bgm");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            if (conn != null) {
                conn.setConnectTimeout(10000);
                conn.setUseCaches(false);

                // optional default is GET
                conn.setRequestMethod("POST");
                conn.setDoOutput(true);

                // add request header
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/35.0.1916.153 Safari/537.36");
                conn.setRequestProperty("X-Requested-With", "XMLHttpRequest");

                OutputStream out_stream = conn.getOutputStream();
                out_stream.write( BGMStoreQuery.getQuery().getBytes("UTF-8") );
                out_stream.flush();
                out_stream.close();

                if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    // 완성이됫다
                    BufferedReader br = new BufferedReader(
                            new InputStreamReader(conn.getInputStream()));
                    for (;;) {
                        String line = br.readLine();
                        if (line == null) {
                            break;
                        }
                        doc = doc + line + "\n";
                    }
                    br.close();
                }
                conn.disconnect();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
        }

        // Parse json
        try {
            JSONParser parser = new JSONParser();
            JSONObject jobj = (JSONObject) parser.parse(doc);
            String html_data = StringEscapeUtils.unescapeJava((String) jobj.get("html_data"));
            // int result_count = Integer.parseInt((String) jobj.get("result_count"));

            return html_data;
        } catch (Exception ex) {
            ex.printStackTrace();
            return "";
        }
    }
}
