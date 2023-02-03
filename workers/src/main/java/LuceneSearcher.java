import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.lucene.queryparser.classic.QueryParser;
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

    static {
        try {
            searchManager = new SearcherManager(LuceneWorker.writer, true, true, null);
            queryParser = new QueryParser("content", LuceneWorker.analyzer);
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
            if (queryString != null) {
                Map<String, String> params = queryToMap(httpExchange.getRequestURI().getQuery());
                query = params.get("query");
            }

            if (requestParamValue != null && !requestParamValue.equals("") ) {
                JSONObject requestBody = new JSONObject(requestParamValue);
                query = requestBody.get("query").toString();
            }

            if (query == null || query.equals(""))
                return;

            System.out.println(query);

            query = "title:\"" + query + "\" OR content:\"" + query + "\"";

            searchManager.maybeRefresh();
            IndexSearcher searcher = searchManager.acquire();

            TopDocs docs = searcher.search(queryParser.parse(query), 10);

            ScoreDoc[] docs_tq = docs.scoreDocs;

            JSONArray res = new JSONArray();

            for (int i = 0; i < docs_tq.length; i++){
                JSONObject doc = new JSONObject();
                doc.put("score", docs_tq[i].score);
                doc.put("link", searcher.doc(docs_tq[i].doc).get("link"));

                res.put(doc);
            }

            OutputStream outputStream = httpExchange.getResponseBody();
            httpExchange.getResponseHeaders().set("Content-Type", "application/json");
            httpExchange.sendResponseHeaders(200, res.toString().length());
            outputStream.write(res.toString().getBytes());
            outputStream.flush();
            outputStream.close();

            searchManager.release(searcher);
        } catch (Exception ignored) {}
    }
}