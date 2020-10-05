package handler;


import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.CharEncoding;
import org.apache.commons.codec.Charsets;


/**
 * @author shuang.kou
 * @createTime 2020年09月24日 13:33:00
 **/
@Slf4j
public class PostRequestHandler implements RequestHandler {

    @Override
    public Object handle(FullHttpRequest fullHttpRequest) {
        String requestUri = fullHttpRequest.uri();
        log.info("request uri :[{}]", requestUri);
        String contentType = this.getContentType(fullHttpRequest.headers());
        if (contentType.equals("application/json")) {
            return fullHttpRequest.content().toString(Charsets.toCharset(CharEncoding.UTF_8));
        } else {
            throw new IllegalArgumentException("only receive application/json type data");
        }

    }

    private String getContentType(HttpHeaders headers) {
        String typeStr = headers.get("Content-Type");
        String[] list = typeStr.split(";");
        return list[0];
    }
}


