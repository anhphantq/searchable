import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.complexPhrase.ComplexPhraseQueryParser;
import org.apache.lucene.search.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Map;
import java.util.HashMap;
public class LuceneSearcher implements HttpHandler {
    private static SearcherManager searchManager;
    private static QueryParser queryParser;
    private static QueryParser complexQueryParser;

    static {
        try {
            searchManager = new SearcherManager(LuceneWorker.writer, true, true, null);
            queryParser = new QueryParser("content", LuceneWorker.analyzer);
            complexQueryParser = new ComplexPhraseQueryParser("content", LuceneWorker.analyzer);
        } catch (Exception e){
            System.out.println("Cannot initialize SearchManager...");
        }
    }

    public void handle(HttpExchange httpExchange) throws IOException {
        httpExchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        String requestBody;

        if ("GET".equals(httpExchange.getRequestMethod())) {
            requestBody = handleGetRequest(httpExchange);
            handleResponse(httpExchange,requestBody);
        }
    }

    private String handleGetRequest(HttpExchange httpExchange) throws  IOException {
        InputStreamReader isr =  new InputStreamReader(httpExchange.getRequestBody(),"utf-8");
        BufferedReader br = new BufferedReader(isr);

// From now on, the right way of moving from bytes to utf-8 characters:

        int b;
        StringBuilder buf = new StringBuilder(512);
        while ((b = br.read()) != -1) {
            buf.append((char) b);
        }

        br.close();
        isr.close();

        return buf.toString();
    }

    private static Map<String, String> queryToMap(String query) {
        Map<String, String> result = new HashMap<>();
        for (String param : query.split("&")) {
            String[] entry = param.split("=");
            if (entry.length > 1) {
                result.put(entry[0], entry[1]);
            }else{
                result.put(entry[0], "");
            }
        }
        return result;
    }


    public static void handleResponse(HttpExchange httpExchange, String requestParamValue)  throws  IOException {
        try {
            String queryString = httpExchange.getRequestURI().getQuery();
            String query = null;
            int type = 2;
            if (queryString != null) {
                Map<String, String> params = queryToMap(httpExchange.getRequestURI().getQuery());
                query = params.get("query");
                type = Integer.parseInt(params.get("type").toString());
            }

            if (requestParamValue != null && !requestParamValue.equals("") ) {
                JSONObject requestBody = new JSONObject(requestParamValue);
                query = requestBody.get("query").toString();
                type = Integer.parseInt(requestBody.get("type").toString());
            }

            Query Q = null;

            /*
            ["PhraseBoolean", "PhraseBooleanSlope", "BooleanQuery", "LuceneRawQuery", "FuzzyPhrase", "FuzzyBoolean"]
             */

            System.out.println(type);


            switch (type){
                case 0:
                    query = "title:\"" + query + "\" OR content:\"" + query + "\"" + "\" OR description:\"" + query + "\"";
                    Q = queryParser.parse(query);
                    System.out.println(query);
                    break;
                case 1:
                    query = "title:\"" + query + "\"~10 OR content:\"" + query + "\"~10" + " OR description:\"" + query + "\"~10";
                    Q = queryParser.parse(query);

                    System.out.println(query);
                    break;
                case 2:
                    String[] strings = query.split(" ");

                    String tmp = "";

                    for (String str: strings){
                        String str_strim = str.trim();
                        tmp = tmp + " title:\"" + str_strim + "\" OR content:\"" + str_strim + "\"" + " OR description:\"" + str_strim + "\"";
                    }

                    query = tmp;
                    Q = complexQueryParser.parse(query);

                    System.out.println(query);
                    break;
                case 3:
                    Q = queryParser.parse(query);
                    break;
                case 4:
                    // to be continued
                    Q = queryParser.parse(query);
                    break;
                case 5:
                    // to be continued
                    Q = queryParser.parse(query);
                    break;
            }

            if (query == null || query.equals("") || Q == null)
                return;

            searchManager.maybeRefresh();
            IndexSearcher searcher = searchManager.acquire();

            TopDocs docs = searcher.search(Q, 100);

            ScoreDoc[] docs_tq = docs.scoreDocs;

            JSONArray res = new JSONArray();

            for (int i = 0; i < docs_tq.length; i++){
                JSONObject doc = new JSONObject();
                doc.put("score", docs_tq[i].score);
                doc.put("link", searcher.doc(docs_tq[i].doc).get("link"));
                try {
                    doc.put("title", searcher.doc(docs_tq[i].doc).get("title"));
                    doc.put("description", searcher.doc(docs_tq[i].doc).get("description"));
                } catch (Exception e){
                    System.out.println("No title and description");
                }

                res.put(doc);
            }

            OutputStream outputStream = httpExchange.getResponseBody();
            httpExchange.getResponseHeaders().set("Content-Type", "application/json");
            httpExchange.sendResponseHeaders(200, res.toString().getBytes().length);
            System.out.println(res.toString());
            outputStream.write(res.toString().getBytes());
            outputStream.flush();
            outputStream.close();

            searchManager.release(searcher);
        } catch (Exception e) {
            System.out.println(e.toString());
        }
    }
}