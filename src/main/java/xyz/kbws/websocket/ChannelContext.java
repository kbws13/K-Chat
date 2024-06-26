package xyz.kbws.websocket;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xyz.kbws.constant.CommonConstant;
import xyz.kbws.mapper.UserMapper;
import xyz.kbws.model.entity.ChatSessionUser;
import xyz.kbws.model.entity.User;
import xyz.kbws.model.enums.UserContactTypeEnum;
import xyz.kbws.model.vo.WsInitVO;
import xyz.kbws.redis.RedisComponent;
import xyz.kbws.service.ChatSessionUserService;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author kbws
 * @date 2024/6/26
 * @description: Channel 上下文工具类
 */
@Slf4j
@Component
public class ChannelContext {

    private static final ConcurrentHashMap<String, Channel> USER_CONTEXT_MAP = new ConcurrentHashMap<>();

    private static final ConcurrentHashMap<String, ChannelGroup> GROUP_CONTEXT_MAP = new ConcurrentHashMap<>();

    @Resource
    private ChatSessionUserService chatSessionUserService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisComponent redisComponent;

    public void addContext(String userId, Channel channel) {
        String channelId = channel.id().toString();
        log.info("channelId：{}", channelId);
        AttributeKey attributeKey = null;
        if (!AttributeKey.exists(channelId)) {
            attributeKey = AttributeKey.newInstance(channelId);
        }else {
            attributeKey = AttributeKey.valueOf(channelId);
        }
        channel.attr(attributeKey).set(userId);
        List<String> contactIdList = redisComponent.getUserContactList(userId);
        for (String groupId : contactIdList) {
            if (groupId.startsWith(UserContactTypeEnum.GROUP.getPrefix())) {
                addToGroup(groupId, channel);
            }
        }
        USER_CONTEXT_MAP.put(userId, channel);
        redisComponent.saveUserHeartBeat(userId);
        // 更新用户最后登录时间
        User user = userMapper.selectById(userId);
        user.setLastLoginTime(new Date());
        userMapper.updateById(user);

        // 给用户发送消息
        Long sourceLastOfTime = user.getLastOffTime();
        Long lastOfTime = sourceLastOfTime;
        if (sourceLastOfTime != null && System.currentTimeMillis() - CommonConstant.MILLISECOND_3DAYS_AGO > sourceLastOfTime) {
            lastOfTime = CommonConstant.MILLISECOND_3DAYS_AGO;
        }
        // 1.查询会话信息 查询用户所有的会话信息 保证换了设备会话同步
        QueryWrapper<ChatSessionUser> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userId", userId);
        queryWrapper.orderByDesc("lastReceiveTime");
        List<ChatSessionUser> chatSessionUserList = chatSessionUserService.list(queryWrapper);

        WsInitVO wsInitVO = new WsInitVO();
        wsInitVO.setChatSessionUserList(chatSessionUserList);

        // 2.查询聊天消息


        // 3.查询好友申请
    }

    /**
     * 发送消息
     */
    public static void sendMessage() {

    }

    private void addToGroup(String groupId, Channel channel) {
        ChannelGroup group = GROUP_CONTEXT_MAP.get(groupId);
        if (group == null) {
            group = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
            GROUP_CONTEXT_MAP.put(groupId, group);
        }
        if (channel == null) {
            return;
        }
        group.add(channel);
    }

    public void removeContext(Channel channel) {
        Attribute<String> attributeKey = channel.attr(AttributeKey.valueOf(channel.id().toString()));
        String userId = attributeKey.get();
        if (StrUtil.isEmpty(userId)) {
            USER_CONTEXT_MAP.remove(userId);
        }
        redisComponent.removeUserHeartBeat(userId);
        // 更新用户最后离线时间
        User user = userMapper.selectById(userId);
        user.setLastOffTime(System.currentTimeMillis());
        userMapper.updateById(user);
    }

}