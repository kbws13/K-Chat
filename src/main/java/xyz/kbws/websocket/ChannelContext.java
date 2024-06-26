package xyz.kbws.websocket;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
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
import xyz.kbws.mapper.ChatMessageMapper;
import xyz.kbws.mapper.UserContactMapper;
import xyz.kbws.mapper.UserMapper;
import xyz.kbws.model.dto.message.MessageSendDTO;
import xyz.kbws.model.entity.*;
import xyz.kbws.model.enums.MessageTypeEnum;
import xyz.kbws.model.enums.UserContactApplyStatusEnum;
import xyz.kbws.model.enums.UserContactTypeEnum;
import xyz.kbws.model.vo.WsInitVO;
import xyz.kbws.redis.RedisComponent;
import xyz.kbws.service.ChatSessionUserService;
import xyz.kbws.service.UserContactApplyService;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

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
    private ChatMessageMapper chatMessageMapper;

    @Resource
    private UserMapper userMapper;

    @Resource
    private UserContactMapper userContactMapper;

    @Resource
    private UserContactApplyService userContactApplyService;

    @Resource
    private RedisComponent redisComponent;

    public void addContext(String userId, Channel channel) {
        try {
            String channelId = channel.id().toString();
            log.info("channelId：{}", channelId);
            AttributeKey attributeKey = null;
            if (!AttributeKey.exists(channelId)) {
                attributeKey = AttributeKey.newInstance(channelId);
            } else {
                attributeKey = AttributeKey.valueOf(channelId);
            }
            channel.attr(attributeKey).set(userId);
            List<String> contactIdList = redisComponent.getUserContactList(userId);
            for (String groupId : contactIdList) {
                if (groupId.startsWith(UserContactTypeEnum.GROUP.getPrefix())) {
                    addUserToGroup(groupId, channel);
                }
            }
            USER_CONTEXT_MAP.put(userId, channel);
            redisComponent.saveUserHeartBeat(userId);
            // 更新用户最后登录时间
            User user = userMapper.selectById(userId);
            user.setLastLoginTime(new Date());
            userMapper.updateById(user);

            // 给用户发送消息
            // 获取用户最后离线时间
            Long sourceLastOfTime = user.getLastOffTime();
            Long lastOfTime = sourceLastOfTime;
            // 这里避免毫秒时间差，所以减去1秒的时间
            // 如果时间太久，只取最近三天的消息数
            if (sourceLastOfTime != null && System.currentTimeMillis() - CommonConstant.MILLISECOND_3DAYS_AGO > sourceLastOfTime) {
                lastOfTime = CommonConstant.MILLISECOND_3DAYS_AGO;
            }
            // 1.查询会话信息 查询用户所有的会话信息 保证换了设备会话同步
            QueryWrapper<ChatSessionUser> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userId", userId);
            queryWrapper.orderByDesc("lastReceiveTime");
            List<ChatSessionUser> chatSessionUserList = chatSessionUserService.list(queryWrapper);

            WsInitVO wsInitVO = new WsInitVO();
            wsInitVO.setChatSessionList(chatSessionUserList);

            // 2.查询聊天消息
            // 查询用户联系人
            QueryWrapper<UserContact> queryChat = new QueryWrapper<>();
            queryChat.eq("contactType", UserContactTypeEnum.GROUP.getType());
            queryChat.eq("userId", userId);
            List<UserContact> groupContactList = userContactMapper.selectList(queryChat);
            List<String> groupIdList = groupContactList.stream().map(UserContact::getContactId).collect(Collectors.toList());
            // 将自己也加进去
            groupIdList.add(userId);
            QueryWrapper<ChatMessage> query = new QueryWrapper<>();
            query.in("contactId", groupIdList);
            query.ge("sendTime", lastOfTime);
            List<ChatMessage> chatMessageList = chatMessageMapper.selectList(query);
            wsInitVO.setChatMessageList(chatMessageList);

            // 3.查询好友申请
            QueryWrapper<UserContactApply> applyQuery = new QueryWrapper<>();
            applyQuery.eq("receiveId", userId);
            applyQuery.eq("status", UserContactApplyStatusEnum.INIT.getStatus());
            applyQuery.ge("lastApplyTime", lastOfTime);
            Integer count = Math.toIntExact(userContactApplyService.count(applyQuery));
            wsInitVO.setApplyCount(count);
            // 发送消息
            MessageSendDTO messageSendDTO = new MessageSendDTO();
            messageSendDTO.setMessageType(MessageTypeEnum.INIT.getType());
            messageSendDTO.setContactId(userId);
            messageSendDTO.setExtentData(wsInitVO);
            sendMessage(messageSendDTO, userId);
        } catch (Exception e) {
            log.error("初始化链接失败", e);
        }
    }

    /**
     * 发送消息
     */
    public void sendMessage(MessageSendDTO messageSendDTO, String receiveId) {
        if (receiveId == null) {
            return;
        }
        Channel sendChannel = USER_CONTEXT_MAP.get(receiveId);
        if (sendChannel == null) {
            return;
        }
        // 相对于客户端而言，联系人就是发送人，这里要转一下再发送，好友申请的时候不处理
        if (MessageTypeEnum.ADD_FRIEND_SELF.getType().equals(messageSendDTO.getMessageType())) {
            User user = (User) messageSendDTO.getExtentData();
            messageSendDTO.setMessageType(MessageTypeEnum.ADD_FRIEND.getType());
            messageSendDTO.setContactId(user.getUserId());
            messageSendDTO.setContactName(user.getNickName());
            messageSendDTO.setExtentData(null);
        } else {
            messageSendDTO.setContactId(messageSendDTO.getSendUserId());
            messageSendDTO.setContactName(messageSendDTO.getSendUserNickName());
        }
        sendChannel.writeAndFlush(new TextWebSocketFrame(JSONUtil.toJsonStr(messageSendDTO)));
    }

    private void addUserToGroup(String groupId, Channel channel) {
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

    public void addUserToGroup(String userId, String groupId) {
        Channel channel = USER_CONTEXT_MAP.get(userId);
        addUserToGroup(groupId, channel);
    }

    /**
     * 删除通道
     * @param channel
     */
    public void removeContext(Channel channel) {
        Attribute<String> attributeKey = channel.attr(AttributeKey.valueOf(channel.id().toString()));
        String userId = attributeKey.get();
        if (!StrUtil.isEmpty(userId)) {
            USER_CONTEXT_MAP.remove(userId);
        }
        redisComponent.removeUserHeartBeat(userId);
        // 更新用户最后离线时间
        User user = userMapper.selectById(userId);
        user.setLastOffTime(System.currentTimeMillis());
        userMapper.updateById(user);
    }

    /**
     * 关闭连接
     * @param userId
     */
    public void closeContext(String userId) {
        if (StrUtil.isEmpty(userId)) {
            return;
        }
        redisComponent.clearUserTokenByUserId(userId);
        Channel channel = USER_CONTEXT_MAP.get(userId);
        USER_CONTEXT_MAP.remove(userId);
        if (channel != null) {
            channel.close();
        }
    }

    // 发送广播消息
    public void sendMessage(MessageSendDTO messageSendDTO) {
        UserContactTypeEnum contactTypeEnum = UserContactTypeEnum.getByPrefix(messageSendDTO.getContactId());
        switch (contactTypeEnum) {
            case USER:
                sendToUser(messageSendDTO);
                break;
            case GROUP:
                sendToGroup(messageSendDTO);
                break;
        }
    }

    // 发送给用户
    private void sendToUser(MessageSendDTO messageSendDTO) {
        String contactId = messageSendDTO.getContactId();
        sendMessage(messageSendDTO, contactId);
        if (MessageTypeEnum.FORCE_OFF_LINE.getType().equals(messageSendDTO.getMessageType())) {
            // 关闭通道
            closeContact(contactId);
        }
    }

    public void closeContact(String userId) {
        if (StrUtil.isEmpty(userId)) {
            return;
        }
        redisComponent.clearUserTokenByUserId(userId);
        Channel channel = USER_CONTEXT_MAP.get(userId);
        if (channel == null) {
            return;
        }
        channel.close();
    }

    // 发送给群组
    private void sendToGroup(MessageSendDTO messageSendDTO) {
        if (messageSendDTO.getContactId() == null) {
            return;
        }
        ChannelGroup group = GROUP_CONTEXT_MAP.get(messageSendDTO.getContactId());
        if (group == null) {
            return;
        }
        group.writeAndFlush(new TextWebSocketFrame(JSONUtil.toJsonStr(messageSendDTO)));
    }

}
