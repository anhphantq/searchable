import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.sun.net.httpserver.HttpServer;
import org.elasticsearch.Version;
import org.elasticsearch.cluster.metadata.IndexMetadata;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.Index;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AnalysisRegistry;
import org.elasticsearch.index.analysis.IndexAnalyzers;
import org.elasticsearch.index.analysis.NamedAnalyzer;
import org.elasticsearch.indices.analysis.AnalysisModule;
import org.elasticsearch.plugin.analysis.vi.AnalysisVietnamesePlugin;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.FSDirectory;
import org.json.JSONObject;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class LuceneWorker {

    private final static String QUEUE_NAME = "news";
    private static final String INDEX_DIR = "/Users/anhphantq/Library/Group Containers/UBF8T346G9.OneDriveStandaloneSuite/OneDrive.noindex/OneDrive/Documents/BÁCH KHOA/TKTT/searchable/workers/src/main/resources";
    static NamedAnalyzer analyzer;
    static IndexWriter writer;

    private static void createWriter() throws IOException
    {
        FSDirectory dir = FSDirectory.open(Paths.get(INDEX_DIR));
        IndexAnalyzers indexAnalyzers = createIndexAnalyzers();
        NamedAnalyzer analyzer = indexAnalyzers.get("vi_analyzer");
        LuceneWorker.analyzer = analyzer;
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        LuceneWorker.writer = new IndexWriter(dir, config);
    }

    private static Document createDocument(String link, String title, String description, String content)
    {
        Document document = new Document();
        document.add(new StringField("link", link, Field.Store.YES));
        document.add(new TextField("title", title, Field.Store.YES));
        document.add(new TextField("description", description, Field.Store.YES));
        document.add(new TextField("content", content, Field.Store.NO));

        return document;
    }

    public static IndexAnalyzers createIndexAnalyzers() throws IOException {
        Settings settings = Settings.builder()
                .put(IndexMetadata.SETTING_VERSION_CREATED, Version.CURRENT)
                .put(Environment.PATH_HOME_SETTING.getKey(), Paths.get(LuceneWorker.INDEX_DIR))
                .put(Settings.EMPTY)
                .build();

        Settings actualSettings;
        if (settings.get("index.version.created") == null) {
            actualSettings = Settings.builder().put(settings).put("index.version.created", Version.CURRENT).build();
        } else {
            actualSettings = settings;
        }

        IndexSettings indexSettings = newIndexSettings("searchable", actualSettings);
        AnalysisRegistry analysisRegistry = createAnalysisRegistryFromSettings(actualSettings, settings, new AnalysisVietnamesePlugin());

        return analysisRegistry.build(indexSettings);
    }

    public static IndexSettings newIndexSettings(String index, Settings settings, Setting<?>... setting) {
        return newIndexSettings(new Index(index, settings.get("index.uuid", "_na_")), settings, setting);
    }

    public static IndexSettings newIndexSettings(Index index, Settings settings, Setting<?>... setting) {
        return newIndexSettings(index, settings, Settings.EMPTY, setting);
    }

    public static IndexSettings newIndexSettings(Index index, Settings indexSetting, Settings nodeSettings, Setting<?>... setting) {
        Settings build = Settings.builder().put("index.version.created", Version.CURRENT).put("index.number_of_replicas", 1).put("index.number_of_shards", 1).put(indexSetting).build();
        IndexMetadata metadata = IndexMetadata.builder(index.getName()).settings(build).build();
        Set<Setting<?>> settingSet = new HashSet(IndexScopedSettings.BUILT_IN_INDEX_SETTINGS);
        if (setting.length > 0) {
            settingSet.addAll(Arrays.asList(setting));
        }

        return new IndexSettings(metadata, nodeSettings, new IndexScopedSettings(Settings.EMPTY, settingSet));
    }

    public static AnalysisRegistry createAnalysisRegistryFromSettings(Settings actualSettings, Settings settings, AnalysisPlugin... plugins) throws IOException {
        Path configPath = null;

        return (new AnalysisModule(new Environment(actualSettings, configPath), Arrays.asList(plugins))).getAnalysisRegistry();
    }

    static {
        try {
            createWriter();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] argv) throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setUsername("group06");
        factory.setPassword("group06");
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.queueDeclare(QUEUE_NAME, true, false, false, null);
        System.out.println(" [*] Waiting for messages. To exit press CTRL+C");

        class Commit extends TimerTask {
            public void run() {
                try {
                    LuceneWorker.writer.commit();
                    System.out.println("Committed");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        Timer timer = new Timer();
        timer.schedule(new Commit(), 0, 60000);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), "UTF-8");

            try {
                System.out.println(" [x] Received '" + message + "'");

                JSONObject msg = new JSONObject(message);

                System.out.println(msg.get("content"));

                Document document = createDocument(msg.get("link").toString(),
                                                   msg.get("title").toString(),
                                                   msg.get("description").toString(),
                                                   msg.get("content").toString());

                LuceneWorker.writer.addDocument(document);
            } catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                System.out.println(" [x] Done");
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };
        channel.basicConsume(QUEUE_NAME, false, deliverCallback, consumerTag -> { });

        System.out.println("HELLO WORLD!");
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 8001), 0);

        server.createContext("/search", new LuceneSearcher());

        ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) Executors.newFixedThreadPool(5);
        server.setExecutor(threadPoolExecutor);

        server.start();
    }

}
