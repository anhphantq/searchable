import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;

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

        String requestParamValue = null;

        if ("GET".equals(httpExchange.getRequestMethod())) {
            requestParamValue = handleGetRequest(httpExchange);
            handleResponse(httpExchange,requestParamValue);
        }
    }

    private String handleGetRequest(HttpExchange httpExchange) throws  IOException {
        return httpExchange.getRequestURI()
                .toString()
                .split("\\?")[1]
                .split("=")[1];
    }

    public static void handleResponse(HttpExchange httpExchange, String requestParamValue)  throws  IOException {
        try {
            searchManager.maybeRefresh();
            IndexSearcher searcher = searchManager.acquire();

            String query = "title:" + requestParamValue + " OR content:" + requestParamValue;

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