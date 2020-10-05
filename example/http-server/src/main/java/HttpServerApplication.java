import server.HttpServer;

/**
 * @author shuang.kou
 * @createTime 2020年10月04日 15:48:00
 **/
public class HttpServerApplication {
    public static void main(String[] args) {
        HttpServer httpServer = new HttpServer();
        httpServer.start();
    }
}
