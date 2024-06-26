package xyz.kbws.websocket.netty.handler;

import cn.hutool.core.util.StrUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.kbws.model.vo.UserVO;
import xyz.kbws.redis.RedisComponent;
import xyz.kbws.websocket.ChannelContext;

import javax.annotation.Resource;

/**
 * @author kbws
 * @date 2024/6/25
 * @description: WebSocket 处理
 */
@Slf4j
@Component
@ChannelHandler.Sharable
public class WebSocketHandler extends SimpleChannelInboundHandler<TextWebSocketFrame> {

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private ChannelContext channelContext;

    /**
     * 通道就绪后调用，一般用户来做初始化
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        log.info("有新的连接加入");
    }

    /**
     * 连接断开后调用
     * @param ctx
     * @throws Exception
     */
    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        log.info("有连接断开");
        channelContext.removeContext(ctx.channel());
    }


    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, TextWebSocketFrame textWebSocketFrame) throws Exception {
        Channel channel = channelHandlerContext.channel();
        Attribute<String> attributeKey = channel.attr(AttributeKey.valueOf(channel.id().toString()));
        String userId = attributeKey.get();
        redisComponent.saveUserHeartBeat(userId);
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof WebSocketServerProtocolHandler.HandshakeComplete) {
            WebSocketServerProtocolHandler.HandshakeComplete complete = (WebSocketServerProtocolHandler.HandshakeComplete) evt;
            String url = complete.requestUri();
            String token = getToken(url);
            if (token == null) {
                ctx.channel().close();
                return;
            }
            UserVO tokenUserVO = redisComponent.getTokenUserVO(token);
            if (tokenUserVO == null) {
                ctx.channel().close();
                return;
            }
            channelContext.addContext(tokenUserVO.getUserId(), ctx.channel());
        }
    }

    private String getToken(String url) {
        if (StrUtil.isEmpty(url) || url.indexOf("?") == -1) {
            return null;
        }
        String[] queryParam = url.split("\\?");
        if (queryParam.length != 2) {
            return null;
        }
        String[] params = queryParam[1].split("=");
        if (params.length != 2) {
            return null;
        }
        return params[1];
    }
}
